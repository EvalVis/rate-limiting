package com.evalvis.ratelimiter.selector;

import com.evalvis.ratelimiter.rate.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;

public final class FixedRateLimiterSelector implements RateLimiterSelector {

	private final RateLimiter rateLimiter;

	public FixedRateLimiterSelector(RateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	@Override
	public RateLimiter select(HttpServletRequest request) {
		return rateLimiter;
	}

}
