package com.evalvis.ratelimiter.key;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpRateLimitKeyResolverTest {

	private final IpRateLimitKeyResolver resolver = new IpRateLimitKeyResolver();

	@Test
	void usesRemoteAddrWhenNoForwardedHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.168.1.10");
		assertThat(resolver.resolveKey(request)).isEqualTo("192.168.1.10");
	}

}
