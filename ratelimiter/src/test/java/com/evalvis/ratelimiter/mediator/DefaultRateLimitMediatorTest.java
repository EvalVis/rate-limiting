package com.evalvis.ratelimiter.mediator;

import static org.assertj.core.api.Assertions.assertThat;

import com.evalvis.ratelimiter.key.RateLimitKeyResolver;
import com.evalvis.ratelimiter.rate.RateLimiter;
import com.evalvis.ratelimiter.selector.RateLimiterSelector;
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

	@Test
	void delegatesToLimiterReturnedBySelector() {
		RateLimitKeyResolver keys = request -> "k1";
		RateLimiter admin = key -> true;
		RateLimiter user = key -> false;
		RateLimiterSelector selector = request -> "1".equals(request.getHeader("tier")) ? admin : user;
		RateLimitMediator mediator = new DefaultRateLimitMediator(keys, selector);
		assertThat(mediator.tryAcquire(new MockHttpServletRequest())).isFalse();
		MockHttpServletRequest up = new MockHttpServletRequest();
		up.addHeader("tier", "1");
		assertThat(mediator.tryAcquire(up)).isTrue();
	}

}
