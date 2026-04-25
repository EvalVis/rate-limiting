package com.evalvis.loadbalancer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "loadbalancer")
data class LoadbalancerProperties(
	val ips: List<String> = emptyList(),
	val strategy: LoadBalancingStrategy = LoadBalancingStrategy.ROUND_ROBIN,
	val consistentHash: ConsistentHashProperties = ConsistentHashProperties(),
	val sharding: ShardingProperties = ShardingProperties(),
	val shards: List<ShardConfig> = emptyList(),
)

data class ConsistentHashProperties(
	val virtualNodesPerServer: Int = 10,
)

data class ShardingProperties(
	val pathPattern: String = "/tables/[^/]+/keys/([^/]+)",
	val virtualNodesPerShard: Int = 10,
)

data class ShardConfig(
	val id: String = "",
	val backends: List<String> = emptyList(),
	val decommissioning: Boolean = false,
)

enum class LoadBalancingStrategy {
	ROUND_ROBIN,
	CONSISTENT_HASH,
	SHARDING_CONSISTENT_HASH,
}
