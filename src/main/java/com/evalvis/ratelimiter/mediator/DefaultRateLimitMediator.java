package com.evalvis.ratelimiter.mediator;

import com.evalvis.ratelimiter.key.RateLimitKeyResolver;
import com.evalvis.ratelimiter.rate.RateLimiter;
import com.evalvis.ratelimiter.selector.FixedRateLimiterSelector;
import com.evalvis.ratelimiter.selector.RateLimiterSelector;
import jakarta.servlet.http.HttpServletRequest;

public final class DefaultRateLimitMediator implements RateLimitMediator {

	private final RateLimitKeyResolver keyResolver;
	private final RateLimiterSelector rateLimiterSelector;

	public DefaultRateLimitMediator(RateLimitKeyResolver keyResolver, RateLimiter rateLimiter) {
		this(keyResolver, new FixedRateLimiterSelector(rateLimiter));
	}

	public DefaultRateLimitMediator(RateLimitKeyResolver keyResolver, RateLimiterSelector rateLimiterSelector) {
		this.keyResolver = keyResolver;
		this.rateLimiterSelector = rateLimiterSelector;
	}

	@Override
	public boolean tryAcquire(HttpServletRequest request) {
		String key = keyResolver.resolveKey(request);
		RateLimiter rateLimiter = rateLimiterSelector.select(request);
		return rateLimiter.tryAcquire(key);
	}

}
