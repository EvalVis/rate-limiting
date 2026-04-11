package com.evalvis.loadbalancer.balance

import java.net.URI

class ConsistentHashRing(backends: List<String>) {

	private data class Point(val hash: Long, val baseUrl: String)

	private val ring: List<Point>

	init {
		if (backends.isEmpty()) {
			throw IllegalStateException("loadbalancer.ips must not be empty")
		}
		ring = backends.map { url ->
			val host = backendHostIp(url)
			Point(RingHasher.hash64(host), url)
		}.sortedWith(compareBy({ it.hash }, { it.baseUrl }))
	}

	fun assign(clientIp: String): String {
		val h = RingHasher.hash64(clientIp)
		val hit = ring.firstOrNull { it.hash >= h }
		return hit?.baseUrl ?: ring.first().baseUrl
	}
}

private fun backendHostIp(baseUrl: String): String {
	val uri = URI.create(baseUrl.trim())
	return uri.host ?: throw IllegalStateException("invalid backend url: $baseUrl")
}
