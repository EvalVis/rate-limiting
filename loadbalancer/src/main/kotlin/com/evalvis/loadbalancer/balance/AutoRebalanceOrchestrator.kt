package com.evalvis.loadbalancer.balance

import com.evalvis.loadbalancer.config.LoadbalancerProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

enum class MigrationState { IN_PROGRESS, COMPLETED, FAILED }

@Component
class AutoRebalanceOrchestrator(
    private val properties: LoadbalancerProperties,
    private val forwardWebClient: WebClient,
) {
    private val log = Logger.getLogger(AutoRebalanceOrchestrator::class.java.name)
    
    // Tracks status: ShardId -> Map(TableName -> State)
    private val statusMap = ConcurrentHashMap<String, MutableMap<String, MigrationState>>()

    fun getStatus() = statusMap.toMap()

    @Scheduled(fixedDelay = 10000) // Check more frequently (every 10s)
    fun checkAndRebalance() {
        val decommissioningShards = properties.shards.filter { it.decommissioning }
        
        // Cleanup status for shards no longer in config
        val currentShardIds = properties.shards.map { it.id }.toSet()
        statusMap.keys.retainAll(currentShardIds)

        decommissioningShards.forEach { decomm ->
            val shardStatus = statusMap.getOrPut(decomm.id) { ConcurrentHashMap() }
            
            // If already fully completed, skip
            if (shardStatus.isNotEmpty() && shardStatus.values.all { it == MigrationState.COMPLETED }) {
                return@forEach
            }

            val sourceUrl = decomm.backends.firstOrNull() ?: return@forEach
            log.info("Discovering tables for auto-rebalance from shard: ${decomm.id}")

            forwardWebClient.get()
                .uri("$sourceUrl/tables")
                .retrieve()
                .bodyToMono<List<String>>()
                .subscribe({ tables ->
                    log.info("Found tables on ${decomm.id}: $tables")
                    
                    val activeShards = properties.shards.filter { !it.decommissioning }
                    
                    tables.forEach { table ->
                        if (shardStatus[table] == MigrationState.COMPLETED) return@forEach
                        
                        shardStatus[table] = MigrationState.IN_PROGRESS
                        
                        // In this protocol, we just need to ensure the data is pushed to at least ONE active shard
                        // because that shard's internal sharding logic will handle the keys it owns,
                        // and lazy-migration will handle the rest.
                        val targetShard = activeShards.firstOrNull() ?: return@forEach
                        val targetUrl = targetShard.backends.firstOrNull() ?: return@forEach

                        forwardWebClient.post()
                            .uri("$targetUrl/tables/$table/migrate-from?sourceUrl=${sourceUrl.replace("http://", "")}")
                            .retrieve()
                            .toBodilessEntity()
                            .subscribe(
                                { 
                                    log.info("Migration of $table from ${decomm.id} COMPLETED")
                                    shardStatus[table] = MigrationState.COMPLETED 
                                },
                                { err -> 
                                    log.warning("Migration of $table from ${decomm.id} FAILED: ${err.message}")
                                    shardStatus[table] = MigrationState.FAILED
                                }
                            )
                    }
                }, { err ->
                    log.warning("Could not discover tables from decommissioning shard ${decomm.id}: ${err.message}")
                })
        }
    }
}
