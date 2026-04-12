package com.evalvis.loadbalancer.config

import com.evalvis.loadbalancer.balance.BackendTargetSelector
import com.evalvis.loadbalancer.balance.ConsistentHashBackendSelector
import com.evalvis.loadbalancer.balance.ConsistentHashRing
import com.evalvis.loadbalancer.balance.RoundRobinTargetPicker
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
		}
	}

	@Bean
	fun forwardWebClient(): WebClient {
		return WebClient.builder().build()
	}
}
