package com.evalvis.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class LeakyBucketRateLimitIntegrationTest {

	private static final MockWebServer UPSTREAM = createUpstream();

	private static MockWebServer createUpstream() {
		try {
			MockWebServer server = new MockWebServer();
			server.enqueue(new MockResponse().setBody("ok").setResponseCode(200));
			server.enqueue(new MockResponse().setBody("ok").setResponseCode(200));
			server.start();
			return server;
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@DynamicPropertySource
	static void registerUpstream(DynamicPropertyRegistry registry) {
		registry.add("ratelimiter.forward.host", () -> UPSTREAM.getHostName());
		registry.add("ratelimiter.forward.port", () -> UPSTREAM.getPort());
		registry.add("ratelimiter.rate-limit.algorithm", () -> "leaky-bucket");
		registry.add("ratelimiter.rate-limit.capacity", () -> "1");
		registry.add("ratelimiter.rate-limit.refill-per-second", () -> "0");
	}

	@LocalServerPort
	private int port;

	private final RestTemplate restTemplate = restTemplateIgnoringClientErrors();

	private static RestTemplate restTemplateIgnoringClientErrors() {
		RestTemplate t = new RestTemplate();
		t.setErrorHandler(new DefaultResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse response) {
				return false;
			}
		});
		return t;
	}

	@AfterAll
	void shutdown() throws IOException {
		UPSTREAM.shutdown();
	}

	@Test
	void rejectsSecondRequestWith429() {
		String base = "http://127.0.0.1:" + port;
		ResponseEntity<String> first = restTemplate.getForEntity(base + "/a", String.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<String> second = restTemplate.getForEntity(base + "/b", String.class);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(UPSTREAM.getRequestCount()).isEqualTo(1);
	}

}
