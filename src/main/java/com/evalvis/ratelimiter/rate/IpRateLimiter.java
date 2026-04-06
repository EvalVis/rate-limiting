package com.evalvis.ratelimiter.rate;

public final class IpRateLimiter {

	private final TokenBucketRateLimiter tokenBucketRateLimiter;

	public IpRateLimiter(TokenBucketRateLimiter tokenBucketRateLimiter) {
		this.tokenBucketRateLimiter = tokenBucketRateLimiter;
	}

	public boolean tryAcquire(String clientIp) {
		return tokenBucketRateLimiter.tryAcquire(clientIp);
	}

}
