package com.evalvis.ratelimiter.rate;

public final class IpRateLimiter {

	private final RateLimiter rateLimiter;

	public IpRateLimiter(RateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	public boolean tryAcquire(String clientIp) {
		return rateLimiter.tryAcquire(clientIp);
	}

}
