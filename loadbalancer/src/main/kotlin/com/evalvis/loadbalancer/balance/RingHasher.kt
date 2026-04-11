package com.evalvis.loadbalancer.balance

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets

object RingHasher {

	fun hash64(key: String): Long {
		return Hashing.murmur3_128().hashString(key, StandardCharsets.UTF_8).asLong()
	}
}
