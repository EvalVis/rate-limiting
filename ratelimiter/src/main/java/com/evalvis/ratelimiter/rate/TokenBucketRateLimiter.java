package com.evalvis.ratelimiter.rate;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenBucketRateLimiter implements RateLimiter {

	private final double capacity;
	private final double refillPerSecond;
	private final Clock clock;
	private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

	public TokenBucketRateLimiter(double capacity, double refillPerSecond, Clock clock) {
		this.capacity = capacity;
		this.refillPerSecond = refillPerSecond;
		this.clock = clock;
	}

	@Override
	public boolean tryAcquire(String key) {
		return buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillPerSecond, clock))
			.tryConsume();
	}

}
