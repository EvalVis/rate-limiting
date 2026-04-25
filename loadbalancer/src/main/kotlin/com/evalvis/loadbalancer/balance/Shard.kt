package com.evalvis.loadbalancer.balance

import java.util.concurrent.atomic.AtomicInteger

data class Shard(
    val id: String,
    val backends: List<String>,
    val decommissioning: Boolean = false
) {
    private val counter = AtomicInteger(0)

    fun getNextBackend(): String {
        if (backends.isEmpty()) throw IllegalStateException("Shard $id has no backends")
        val index = Math.abs(counter.getAndIncrement() % backends.size)
        return backends[index]
    }
}
