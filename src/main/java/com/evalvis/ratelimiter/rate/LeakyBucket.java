package com.evalvis.ratelimiter.rate;

import java.time.Clock;

public final class LeakyBucket {

	private final double capacity;
	private final double leakPerSecond;
	private final Clock clock;
	private double volume;
	private long lastMillis;

	public LeakyBucket(double capacity, double leakPerSecond, Clock clock) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		if (leakPerSecond < 0) {
			throw new IllegalArgumentException("leakPerSecond must be non-negative");
		}
		this.capacity = capacity;
		this.leakPerSecond = leakPerSecond;
		this.clock = clock;
		this.volume = 0;
		this.lastMillis = clock.millis();
	}

	public boolean tryConsume() {
		long now = clock.millis();
		long elapsed = now - lastMillis;
		if (elapsed > 0) {
			double leaked = (elapsed / 1000.0) * leakPerSecond;
			volume = Math.max(0, volume - leaked);
			lastMillis = now;
		}
		if (volume + 1.0 <= capacity) {
			volume += 1.0;
			return true;
		}
		return false;
	}

}
