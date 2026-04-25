package com.evalvis.loadbalancer.config

import com.evalvis.loadbalancer.balance.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class LoadbalancerConfiguration {

	@Bean
	fun backendTargetSelector(properties: LoadbalancerProperties): BackendTargetSelector {
		return when (properties.strategy) {
			LoadBalancingStrategy.CONSISTENT_HASH -> ConsistentHashBackendSelector(
				ConsistentHashRing(
					properties.ips,
					properties.consistentHash.virtualNodesPerServer,
				),
			)
			LoadBalancingStrategy.ROUND_ROBIN -> RoundRobinTargetPicker(properties.ips)
			LoadBalancingStrategy.SHARDING_CONSISTENT_HASH -> {
				val shards = properties.shards.map { Shard(it.id, it.backends, it.decommissioning) }
				val ring = ShardRing(shards, properties.sharding.virtualNodesPerShard)
				ShardingBackendSelector(ring, properties.sharding.pathPattern)
			}
		}
	}

	@Bean
	fun forwardWebClient(): WebClient {
		return WebClient.builder().build()
	}
}
