package com.evalvis.loadbalancer.balance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConsistentHashRingTest {

	@Test
	fun whenSingleBackendThenAlwaysThatUrl() {
		val ring = ConsistentHashRing(listOf("http://192.0.2.10:8080/"))
		assertEquals("http://192.0.2.10:8080/", ring.assign("198.51.100.1"))
		assertEquals("http://192.0.2.10:8080/", ring.assign("198.51.100.2"))
	}

	@Test
	fun whenSameClientIpThenStableBackend() {
		val ring = ConsistentHashRing(
			listOf(
				"http://192.0.2.1:9000/",
				"http://192.0.2.2:9000/",
			),
		)
		val chosen = ring.assign("203.0.113.44")
		repeat(50) {
			assertEquals(chosen, ring.assign("203.0.113.44"))
		}
	}

	@Test
	fun whenEmptyBackendsThenThrows() {
		assertThrows(IllegalStateException::class.java) {
			ConsistentHashRing(emptyList())
		}
	}

	@Test
	fun whenVirtualNodesPerServerIsZeroThenThrows() {
		assertThrows(IllegalArgumentException::class.java) {
			ConsistentHashRing(
				listOf("http://192.0.2.1:8080/"),
				virtualNodesPerServer = 0,
			)
		}
	}

	@Test
	fun whenVirtualNodesPerServerGreaterThanOneThenSameClientIpStable() {
		val ring = ConsistentHashRing(
			listOf(
				"http://192.0.2.1:9000/",
				"http://192.0.2.2:9000/",
			),
			virtualNodesPerServer = 10,
		)
		val chosen = ring.assign("203.0.113.44")
		repeat(50) {
			assertEquals(chosen, ring.assign("203.0.113.44"))
		}
	}
}
