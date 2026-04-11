package com.evalvis.loadbalancer.balance

import jakarta.servlet.http.HttpServletRequest
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinTargetPicker(private val bases: List<String>) : BackendTargetSelector {

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

	override fun selectTarget(request: HttpServletRequest): String {
		return next()
	}
}
