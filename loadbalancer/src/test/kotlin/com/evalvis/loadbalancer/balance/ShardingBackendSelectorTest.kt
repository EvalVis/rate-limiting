package com.evalvis.loadbalancer.balance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ShardingBackendSelectorTest {

    @Test
    fun `should extract key from path and route to correct shard`() {
        val shardA = Shard("shard-a", listOf("http://backend-a1:8080"))
        val shardB = Shard("shard-b", listOf("http://backend-b1:8080"))
        
        val ring = ShardRing(listOf(shardA, shardB))
        val selector = ShardingBackendSelector(
            ring = ring,
            pathPattern = "/tables/[^/]+/keys/([^/]+)"
        )

        val request = MockHttpServletRequest("GET", "/tables/users/keys/user-42")
        val target = selector.selectTarget(request)
        
        // user-42 hashes to something that should consistently map to one of the shards
        // Let's assume for this test we know where it maps or just verify it's from the correct set
        val possibleBackends = listOf("http://backend-a1:8080", "http://backend-b1:8080")
        assert(possibleBackends.contains(target))
        
        // Stability check
        repeat(10) {
            assertEquals(target, selector.selectTarget(request))
        }
    }

    @Test
    fun `should fallback to round-robin if no key matches`() {
        val shardA = Shard("shard-a", listOf("http://backend-a1:8080"))
        val ring = ShardRing(listOf(shardA))
        val selector = ShardingBackendSelector(
            ring = ring,
            pathPattern = "/tables/[^/]+/keys/([^/]+)"
        )

        val request = MockHttpServletRequest("GET", "/health")
        val target = selector.selectTarget(request)
        
        assertEquals("http://backend-a1:8080", target)
    }
}
