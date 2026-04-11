package com.evalvis.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
class JwtRoleRateLimitIntegrationTest {

	private static final String SECRET = "integration-test-secret-32bytes-min!!";

	private static final MockWebServer UPSTREAM = createUpstream();

	private static MockWebServer createUpstream() {
		try {
			MockWebServer server = new MockWebServer();
			server.enqueue(new MockResponse().setBody("ok").setResponseCode(200));
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
		registry.add("ratelimiter.rate-limit.capacity", () -> "1");
		registry.add("ratelimiter.rate-limit.refill-per-second", () -> "0");
		registry.add("ratelimiter.admin-rate-limit.capacity", () -> "10");
		registry.add("ratelimiter.admin-rate-limit.refill-per-second", () -> "0");
		registry.add("ratelimiter.jwt.secret", () -> SECRET);
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
	void afterUserBucketExhausted_adminJwtStillAllowed() {
		String base = "http://127.0.0.1:" + port;
		assertThat(restTemplate.getForEntity(base + "/x", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(restTemplate.getForEntity(base + "/y", String.class).getStatusCode())
			.isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		String adminJwt = Jwts.builder()
			.subject("admin1")
			.claim("role", "admin")
			.expiration(Date.from(Instant.now().plusSeconds(3600)))
			.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
			.compact();
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminJwt);
		ResponseEntity<String> third = restTemplate.exchange(
			base + "/z",
			HttpMethod.GET,
			new HttpEntity<>(headers),
			String.class
		);
		assertThat(third.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(UPSTREAM.getRequestCount()).isEqualTo(2);
	}

}
