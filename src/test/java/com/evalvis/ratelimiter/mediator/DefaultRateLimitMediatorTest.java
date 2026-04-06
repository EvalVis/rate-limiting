package com.evalvis.ratelimiter.mediator;

import static org.assertj.core.api.Assertions.assertThat;

import com.evalvis.ratelimiter.key.RateLimitKeyResolver;
import com.evalvis.ratelimiter.rate.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DefaultRateLimitMediatorTest {

	@Test
	void resolvesKeyThenDelegatesToRateLimiter() {
		RateLimitKeyResolver keys = request -> "k1";
		RateLimiter limiter = key -> "k1".equals(key);
		RateLimitMediator mediator = new DefaultRateLimitMediator(keys, limiter);
		assertThat(mediator.tryAcquire(new MockHttpServletRequest())).isTrue();
	}

	@Test
	void whenRateLimiterDenies_returnsFalse() {
		RateLimitKeyResolver keys = request -> "k1";
		RateLimiter limiter = key -> false;
		RateLimitMediator mediator = new DefaultRateLimitMediator(keys, limiter);
		assertThat(mediator.tryAcquire(new MockHttpServletRequest())).isFalse();
	}

}
