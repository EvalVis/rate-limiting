package com.evalvis.loadbalancer

import com.evalvis.loadbalancer.config.LoadbalancerProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(LoadbalancerProperties::class)
@EnableScheduling
class LoadbalancerApplication

fun main(args: Array<String>) {
	runApplication<LoadbalancerApplication>(*args)
}
