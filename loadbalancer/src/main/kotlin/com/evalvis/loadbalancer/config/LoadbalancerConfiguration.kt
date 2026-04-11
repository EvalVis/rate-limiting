package com.evalvis.loadbalancer.config

import com.evalvis.loadbalancer.balance.RoundRobinTargetPicker
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class LoadbalancerConfiguration {

	@Bean
	fun roundRobinTargetPicker(properties: LoadbalancerProperties): RoundRobinTargetPicker {
		return RoundRobinTargetPicker(properties.ips)
	}

	@Bean
	fun forwardWebClient(): WebClient {
		return WebClient.builder().build()
	}
}
