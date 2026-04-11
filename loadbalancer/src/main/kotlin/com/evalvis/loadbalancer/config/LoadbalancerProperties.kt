package com.evalvis.loadbalancer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "loadbalancer")
data class LoadbalancerProperties(
	val ips: List<String> = emptyList(),
)
