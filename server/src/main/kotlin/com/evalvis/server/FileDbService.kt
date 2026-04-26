package com.evalvis.server

import com.evalvis.database.FileDbClient
import com.evalvis.database.JsonLineRecord
import com.evalvis.database.TableNotFoundException
import com.evalvis.database.TcpFileDbClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import java.util.logging.Logger

@Service
class FileDbService(
    @Value("\${database.url:127.0.0.1:7379}") private val readDbUrl: String,
    @Value("\${database.leader-discovery.election-endpoints:}") electionEndpointsRaw: String,
    @Value("\${database.leader-discovery.cache-ttl-ms:5000}") cacheTtlMs: Long,
    @Value("\${database.consistency.mode:EVENTUAL}") private val consistencyMode: String,
    @Value("\${database.consistency.quorum-r:1}") private val quorumR: Int
) {
    private val LOG = Logger.getLogger(FileDbService::class.java.name)

    private val leaderDiscovery: LeaderDiscoveryClient? =
        electionEndpointsRaw.trim()
            .takeIf { it.isNotBlank() }
            ?.let { raw -> LeaderDiscoveryClient(raw.split(",").map { it.trim() }, cacheTtlMs) }

    fun createTable(tableName: String) {
        executeWrite { it.createTable(tableName) }
    }

    fun put(tableName: String, key: String, value: String) {
        executeWrite { it.put(tableName, key, value) }
    }

    fun get(tableName: String, key: String): Optional<String> {
        return executeRead(tableName, key)
    }

    fun migrateFrom(tableName: String, sourceUrl: String) {
        // Implementation for migration logic
    }

    fun listTables(): List<String> {
        return execute { createClient(readDbUrl).listTables() }
    }

    private fun <T> executeWrite(action: (FileDbClient) -> T): T {
        try {
            return execute { action(writeClient()) }
        } catch (e: Exception) {
            if (e is com.evalvis.server.TableNotFoundException) throw e
            if (leaderDiscovery != null) {
                LOG.warning("Write failed: ${e.message}. Retrying with fresh discovery...")
                leaderDiscovery.invalidate()
                Thread.sleep(1000)
                return execute { action(writeClient()) }
            }
            throw e
        }
    }

    private fun executeRead(tableName: String, key: String): Optional<String> {
        if ("STRICT".equals(consistencyMode, ignoreCase = true) && leaderDiscovery != null) {
            try {
                return quorumGet(tableName, key)
            } catch (e: Exception) {
                if (e is com.evalvis.server.TableNotFoundException) throw e
                LOG.warning("Quorum read failed: ${e.message}. Retrying...")
                leaderDiscovery.invalidate()
                Thread.sleep(1000)
                return quorumGet(tableName, key)
            }
        }
        try {
            return execute { createClient(readDbUrl).get(tableName, key) }
        } catch (e: Exception) {
            if (e is com.evalvis.server.TableNotFoundException) throw e
            if (leaderDiscovery != null) {
                LOG.warning("Read failed: ${e.message}. Retrying with fresh discovery...")
                leaderDiscovery.invalidate()
                Thread.sleep(500)
                return execute { createClient(leaderDiscovery.findLeaderDbAddress()).get(tableName, key) }
            }
            throw e
        }
    }

    private fun quorumGet(tableName: String, key: String): Optional<String> {
        val nodes = runCatching { leaderDiscovery!!.findAllProxyAddresses() }.getOrElse { emptyList() }
        if (nodes.isEmpty()) {
             throw DatabaseClientException("Consistency failure: no proxy nodes found")
        }
        
        val futures = nodes.map { addr ->
            CompletableFuture.supplyAsync(Supplier<Result<Optional<JsonLineRecord>>> {
                runCatching { createClient(addr).getRecord(tableName, key) }
            })
        }

        val successfulResults = mutableListOf<Optional<JsonLineRecord>>()
        var tableNotFoundCount = 0
        var connectivityErrorCount = 0
        val deadline = System.currentTimeMillis() + 5000
        
        for (future in futures) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining > 0) {
                runCatching { future.get(remaining, TimeUnit.MILLISECONDS) }
                    .fold(
                        onSuccess = { res ->
                            res.fold(
                                onSuccess = { successfulResults.add(it) },
                                onFailure = { 
                                    if (it is TableNotFoundException) tableNotFoundCount++ 
                                    else connectivityErrorCount++
                                }
                            )
                        },
                        onFailure = { connectivityErrorCount++ }
                    )
            } else {
                connectivityErrorCount++
            }
        }

        if (tableNotFoundCount >= quorumR) {
            throw com.evalvis.server.TableNotFoundException()
        }

        if (successfulResults.size < quorumR) {
            throw DatabaseClientException("Consistency failure: read quorum not reached (got ${successfulResults.size}, need $quorumR)")
        }

        return successfulResults.filter { it.isPresent }
            .map { it.get() }
            .maxByOrNull { it.version() }
            ?.let { Optional.of(it.value()) }
            ?: Optional.empty()
    }

    private fun writeClient(): FileDbClient =
        createClient(leaderDiscovery?.findLeaderDbAddress() ?: readDbUrl)

    private fun <T> execute(action: () -> T): T {
        try {
            return action()
        } catch (e: TableNotFoundException) {
            throw com.evalvis.server.TableNotFoundException()
        } catch (e: Exception) {
            if (e is DatabaseClientException) throw e
            if (e is com.evalvis.server.TableNotFoundException) throw e
            if (e.cause is TableNotFoundException) throw com.evalvis.server.TableNotFoundException()
            throw DatabaseClientException("Database unavailable: ${e.message}")
        }
    }

    private fun createClient(url: String): FileDbClient {
        val tokens = url.split(":", limit = 2)
        require(tokens.size == 2) { "database url must be host:port, got: $url" }
        return TcpFileDbClient(tokens[0], tokens[1].toInt())
    }
}
