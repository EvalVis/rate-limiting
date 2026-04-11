package com.evalvis.ratelimiter.rate;

import java.time.Clock;

public final class TokenBucket {

	private final double capacity;
	private final double refillPerSecond;
	private final Clock clock;
	private double tokens;
	private long lastRefillMillis;

	public TokenBucket(double capacity, double refillPerSecond, Clock clock) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		if (refillPerSecond < 0) {
			throw new IllegalArgumentException("refillPerSecond must be non-negative");
		}
		this.capacity = capacity;
		this.refillPerSecond = refillPerSecond;
		this.clock = clock;
		this.tokens = capacity;
		this.lastRefillMillis = clock.millis();
	}

	public boolean tryConsume() {
		refill();
		if (tokens >= 1.0) {
			tokens -= 1.0;
			return true;
		}
		return false;
	}

	private void refill() {
		long now = clock.millis();
		long elapsed = now - lastRefillMillis;
		if (elapsed <= 0) {
			return;
		}
		double added = (elapsed / 1000.0) * refillPerSecond;
		tokens = Math.min(capacity, tokens + added);
		lastRefillMillis = now;
	}

}
