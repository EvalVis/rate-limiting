package com.evalvis.loadbalancer

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestTemplate
import java.io.IOException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadBalancerForwardingIntegrationTest {

	private val restTemplate = RestTemplate()

	@LocalServerPort
	private var port: Int = 0

	@Test
	fun forwardsSequentiallyToBackendsInRoundRobin() {
		repeat(4) { i ->
			val response = restTemplate.getForEntity("http://127.0.0.1:$port/ping", String::class.java)
			assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
			assertThat(response.body).isEqualTo(if (i % 2 == 0) "one" else "two")
		}
		assertThat(UPSTREAM_ONE.requestCount).isEqualTo(2)
		assertThat(UPSTREAM_TWO.requestCount).isEqualTo(2)
	}

	companion object {
		private val UPSTREAM_ONE = MockWebServer()
		private val UPSTREAM_TWO = MockWebServer()

		init {
			try {
				UPSTREAM_ONE.enqueue(MockResponse().setBody("one").setResponseCode(200))
				UPSTREAM_ONE.enqueue(MockResponse().setBody("one").setResponseCode(200))
				UPSTREAM_ONE.start()
				UPSTREAM_TWO.enqueue(MockResponse().setBody("two").setResponseCode(200))
				UPSTREAM_TWO.enqueue(MockResponse().setBody("two").setResponseCode(200))
				UPSTREAM_TWO.start()
			} catch (e: IOException) {
				throw ExceptionInInitializerError(e)
			}
		}

		@JvmStatic
		@DynamicPropertySource
		fun registerBackends(registry: DynamicPropertyRegistry) {
			registry.add("loadbalancer.ips[0]") { UPSTREAM_ONE.url("/").toString().removeSuffix("/") }
			registry.add("loadbalancer.ips[1]") { UPSTREAM_TWO.url("/").toString().removeSuffix("/") }
		}

		@JvmStatic
		@AfterAll
		fun shutdown() {
			UPSTREAM_ONE.shutdown()
			UPSTREAM_TWO.shutdown()
		}
	}
}
