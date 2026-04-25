package com.evalvis.loadbalancer.balance

import java.util.*

class ShardRing(
    private val allShards: List<Shard>,
    private val virtualNodesPerShard: Int = 10
) {
    private val activeRing = TreeMap<Long, Shard>()
    private val decommissioningRing = TreeMap<Long, Shard>()

    init {
        require(allShards.isNotEmpty()) { "shards must not be empty" }
        allShards.forEach { shard ->
            val ring = if (shard.decommissioning) decommissioningRing else activeRing
            repeat(virtualNodesPerShard) { i ->
                val hash = RingHasher.hash64("${shard.id}#$i")
                ring[hash] = shard
            }
        }
        // If active ring is empty (all decommissioning), fallback to decommissioning for everything
        if (activeRing.isEmpty()) {
            activeRing.putAll(decommissioningRing)
        }
    }

    fun assign(key: String): Shard {
        val hash = RingHasher.hash64(key)
        val entry = activeRing.ceilingEntry(hash) ?: activeRing.firstEntry()
        return entry!!.value
    }

    fun getAllShards(): Collection<Shard> = allShards

    fun assignFallback(key: String): Shard? {
        val hash = RingHasher.hash64(key)
        val primaryEntry = activeRing.ceilingEntry(hash) ?: activeRing.firstEntry() ?: return null
        val primaryShard = primaryEntry.value
        
        // 1. Try finding a different ACTIVE shard (clockwise)
        var current = primaryEntry
        repeat(activeRing.size) {
            val next = activeRing.higherEntry(current.key) ?: activeRing.firstEntry() ?: return@repeat
            if (next.value.id != primaryShard.id) {
                return next.value
            }
            current = next
        }

        // 2. If not found or if we want to specifically check decommissioning shards:
        // In removal scenarios, the data is on a decommissioning shard.
        val decommEntry = decommissioningRing.ceilingEntry(hash) ?: decommissioningRing.firstEntry()
        if (decommEntry != null && decommEntry.value.id != primaryShard.id) {
            return decommEntry.value
        }

        return null
    }
}
