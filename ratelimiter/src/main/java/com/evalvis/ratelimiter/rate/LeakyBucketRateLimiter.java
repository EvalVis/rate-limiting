package com.evalvis.ratelimiter.rate;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

public final class LeakyBucketRateLimiter implements RateLimiter {

	private final double capacity;
	private final double leakPerSecond;
	private final Clock clock;
	private final ConcurrentHashMap<String, LeakyBucket> buckets = new ConcurrentHashMap<>();

	public LeakyBucketRateLimiter(double capacity, double leakPerSecond, Clock clock) {
		this.capacity = capacity;
		this.leakPerSecond = leakPerSecond;
		this.clock = clock;
	}

	@Override
	public boolean tryAcquire(String key) {
		return buckets.computeIfAbsent(key, k -> new LeakyBucket(capacity, leakPerSecond, clock))
			.tryConsume();
	}

}
