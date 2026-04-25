package com.evalvis.loadbalancer

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShardingIntegrationTest {

    private val restTemplate = RestTemplate()

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `should route to specific shard based on key`() {
        val initialCount1 = UPSTREAM_ONE.requestCount
        val initialCount2 = UPSTREAM_TWO.requestCount

        UPSTREAM_ONE.enqueue(MockResponse().setBody("from-shard-1"))
        UPSTREAM_TWO.enqueue(MockResponse().setBody("from-shard-2"))

        val response = restTemplate.getForEntity("http://localhost:$port/tables/users/keys/key-123", String::class.java)
        
        val body = response.body
        assert(body == "from-shard-1" || body == "from-shard-2")

        if (body == "from-shard-1") {
            assertEquals(initialCount1 + 1, UPSTREAM_ONE.requestCount)
            assertEquals(initialCount2, UPSTREAM_TWO.requestCount)
        } else {
            assertEquals(initialCount1, UPSTREAM_ONE.requestCount)
            assertEquals(initialCount2 + 1, UPSTREAM_TWO.requestCount)
        }
    }

    @Test
    fun `should broadcast table creation to all shards`() {
        val initialCount1 = UPSTREAM_ONE.requestCount
        val initialCount2 = UPSTREAM_TWO.requestCount

        UPSTREAM_ONE.enqueue(MockResponse().setResponseCode(201))
        UPSTREAM_TWO.enqueue(MockResponse().setResponseCode(201))

        val response = restTemplate.postForEntity("http://localhost:$port/tables/new-table", null, Unit::class.java)
        
        assertEquals(201, response.statusCode.value())
        
        assertEquals(initialCount1 + 1, UPSTREAM_ONE.requestCount)
        assertEquals(initialCount2 + 1, UPSTREAM_TWO.requestCount)
    }

    companion object {
        private val UPSTREAM_ONE = MockWebServer()
        private val UPSTREAM_TWO = MockWebServer()

        init {
            UPSTREAM_ONE.start()
            UPSTREAM_TWO.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerBackends(registry: DynamicPropertyRegistry) {
            registry.add("loadbalancer.strategy") { "SHARDING_CONSISTENT_HASH" }
            registry.add("loadbalancer.sharding.path-pattern") { "/tables/[^/]+/keys/([^/]+)" }
            registry.add("loadbalancer.shards[0].id") { "shard-1" }
            registry.add("loadbalancer.shards[0].backends[0]") { UPSTREAM_ONE.url("/").toString().removeSuffix("/") }
            registry.add("loadbalancer.shards[1].id") { "shard-2" }
            registry.add("loadbalancer.shards[1].backends[0]") { UPSTREAM_TWO.url("/").toString().removeSuffix("/") }
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            UPSTREAM_ONE.shutdown()
            UPSTREAM_TWO.shutdown()
        }
    }
}
