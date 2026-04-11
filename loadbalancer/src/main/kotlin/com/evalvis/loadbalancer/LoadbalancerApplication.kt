package com.evalvis.loadbalancer

import com.evalvis.loadbalancer.config.LoadbalancerProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(LoadbalancerProperties::class)
class LoadbalancerApplication

fun main(args: Array<String>) {
	runApplication<LoadbalancerApplication>(*args)
}
