package com.evalvis.ratelimiter.mediator;

import com.evalvis.ratelimiter.key.RateLimitKeyResolver;
import com.evalvis.ratelimiter.rate.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;

public final class DefaultRateLimitMediator implements RateLimitMediator {

	private final RateLimitKeyResolver keyResolver;
	private final RateLimiter rateLimiter;

	public DefaultRateLimitMediator(RateLimitKeyResolver keyResolver, RateLimiter rateLimiter) {
		this.keyResolver = keyResolver;
		this.rateLimiter = rateLimiter;
	}

	@Override
	public boolean tryAcquire(HttpServletRequest request) {
		String key = keyResolver.resolveKey(request);
		return rateLimiter.tryAcquire(key);
	}

}
