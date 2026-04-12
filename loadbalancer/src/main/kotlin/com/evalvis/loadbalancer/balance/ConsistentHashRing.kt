package com.evalvis.loadbalancer.balance

import java.net.URI

class ConsistentHashRing(
	backends: List<String>,
	virtualNodesPerServer: Int = DEFAULT_VIRTUAL_NODES_PER_SERVER,
) {

	companion object {
		const val DEFAULT_VIRTUAL_NODES_PER_SERVER = 10
	}

	private data class Point(val hash: Long, val baseUrl: String)

	private val ring: List<Point>

	init {
		if (backends.isEmpty()) {
			throw IllegalStateException("loadbalancer.ips must not be empty")
		}
		require(virtualNodesPerServer >= 1) {
			"virtualNodesPerServer must be at least 1, was $virtualNodesPerServer"
		}
		ring = backends.flatMap { url ->
			(0 until virtualNodesPerServer).map { replica ->
				val vnodeKey = backendVnodeKey(url, replica)
				Point(RingHasher.hash64(vnodeKey), url)
			}
		}.sortedWith(compareBy({ it.hash }, { it.baseUrl }))
	}

	fun assign(clientIp: String): String {
		val h = RingHasher.hash64(clientIp)
		val hit = ring.firstOrNull { it.hash >= h }
		return hit?.baseUrl ?: ring.first().baseUrl
	}
}

private fun backendVnodeKey(baseUrl: String, replica: Int): String {
	val uri = URI.create(baseUrl.trim())
	val host = uri.host ?: throw IllegalStateException("invalid backend url: $baseUrl")
	val port = uri.port
	val identity = if (port == -1) host else "$host:$port"
	return "$identity#$replica"
}
