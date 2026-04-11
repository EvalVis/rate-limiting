package com.evalvis.loadbalancer.balance

import java.util.concurrent.atomic.AtomicInteger

class RoundRobinTargetPicker(private val bases: List<String>) {

	private val index = AtomicInteger(0)

	init {
		if (bases.isEmpty()) {
			throw IllegalStateException("loadbalancer.ips must not be empty")
		}
	}

	fun next(): String {
		val i = index.getAndUpdate { current -> (current + 1) % bases.size }
		return bases[i]
	}
}
