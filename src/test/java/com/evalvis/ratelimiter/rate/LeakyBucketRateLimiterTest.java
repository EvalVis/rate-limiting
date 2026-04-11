package com.evalvis.ratelimiter.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LeakyBucketRateLimiterTest {

	@Test
	void differentKeysHaveIndependentBuckets() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		RateLimiter limiter = new LeakyBucketRateLimiter(1, 0.0, clock);
		assertThat(limiter.tryAcquire("key-a")).isTrue();
		assertThat(limiter.tryAcquire("key-a")).isFalse();
		assertThat(limiter.tryAcquire("key-b")).isTrue();
	}

	@Test
	void sameKeyAllowsAcquireAfterLeak() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		RateLimiter limiter = new LeakyBucketRateLimiter(1, 1.0, clock);
		assertThat(limiter.tryAcquire("key-a")).isTrue();
		assertThat(limiter.tryAcquire("key-a")).isFalse();
		clock.advance(Duration.ofSeconds(1));
		assertThat(limiter.tryAcquire("key-a")).isTrue();
	}

}
