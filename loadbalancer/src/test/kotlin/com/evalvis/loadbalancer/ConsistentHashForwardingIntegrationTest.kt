package com.evalvis.loadbalancer

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestTemplate
import java.io.IOException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsistentHashForwardingIntegrationTest {

	private val restTemplate = RestTemplate()

	@LocalServerPort
	private var port: Int = 0

	@Test
	fun sameClientIpAlwaysHitsSameBackend() {
		repeat(12) {
			UPSTREAM_ONE.enqueue(MockResponse().setBody("one").setResponseCode(200))
			UPSTREAM_TWO.enqueue(MockResponse().setBody("two").setResponseCode(200))
		}
		val headers = HttpHeaders()
		headers.add("X-Forwarded-For", "203.0.113.88")
		val entity = HttpEntity<Void>(headers)
		val bodies = mutableListOf<String>()
		repeat(12) {
			val response = restTemplate.exchange(
				"http://127.0.0.1:$port/ping",
				HttpMethod.GET,
				entity,
				String::class.java,
			)
			assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
			bodies.add(response.body!!)
		}
		assertThat(bodies.distinct().size).isEqualTo(1)
		if (bodies.first() == "one") {
			assertThat(UPSTREAM_ONE.requestCount).isEqualTo(12)
			assertThat(UPSTREAM_TWO.requestCount).isEqualTo(0)
		} else {
			assertThat(UPSTREAM_ONE.requestCount).isEqualTo(0)
			assertThat(UPSTREAM_TWO.requestCount).isEqualTo(12)
		}
	}

	companion object {
		private val UPSTREAM_ONE = MockWebServer()
		private val UPSTREAM_TWO = MockWebServer()

		init {
			try {
				UPSTREAM_ONE.start()
				UPSTREAM_TWO.start()
			} catch (e: IOException) {
				throw ExceptionInInitializerError(e)
			}
		}

		@JvmStatic
		@DynamicPropertySource
		fun registerBackends(registry: DynamicPropertyRegistry) {
			registry.add("loadbalancer.strategy") { "consistent-hash" }
			registry.add("loadbalancer.ips[0]") { "http://127.0.0.1:${UPSTREAM_ONE.port}" }
			registry.add("loadbalancer.ips[1]") { "http://localhost:${UPSTREAM_TWO.port}" }
		}

		@JvmStatic
		@AfterAll
		fun shutdown() {
			UPSTREAM_ONE.shutdown()
			UPSTREAM_TWO.shutdown()
		}
	}
}
