package com.evalvis.ratelimiter.key;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IpRateLimitKeyResolverTest {

	private final IpRateLimitKeyResolver resolver = new IpRateLimitKeyResolver();

	@Test
	void usesRemoteAddrWhenNoForwardedHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.168.1.10");
		assertThat(resolver.resolveKey(request)).isEqualTo("192.168.1.10");
	}

	@Test
	void usesFirstForwardedForEntry() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
		request.setRemoteAddr("127.0.0.1");
		assertThat(resolver.resolveKey(request)).isEqualTo("203.0.113.1");
	}

}
