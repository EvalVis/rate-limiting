package com.evalvis.loadbalancer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "loadbalancer")
data class LoadbalancerProperties(
	val ips: List<String> = emptyList(),
	val strategy: LoadBalancingStrategy = LoadBalancingStrategy.ROUND_ROBIN,
	val consistentHash: ConsistentHashProperties = ConsistentHashProperties(),
)

data class ConsistentHashProperties(
	val virtualNodesPerServer: Int = 10,
)

enum class LoadBalancingStrategy {
	ROUND_ROBIN,
	CONSISTENT_HASH,
}
