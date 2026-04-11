package com.evalvis.ratelimiter.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IpRateLimiterTest {

	@Test
	void differentClientIpsHaveIndependentBuckets() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		TokenBucketRateLimiter method = new TokenBucketRateLimiter(1, 0.0, clock);
		IpRateLimiter ipLimiter = new IpRateLimiter(method);
		assertThat(ipLimiter.tryAcquire("10.0.0.1")).isTrue();
		assertThat(ipLimiter.tryAcquire("10.0.0.1")).isFalse();
		assertThat(ipLimiter.tryAcquire("10.0.0.2")).isTrue();
	}

	@Test
	void sameClientIpRefillsOverTime() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		TokenBucketRateLimiter method = new TokenBucketRateLimiter(1, 1.0, clock);
		IpRateLimiter ipLimiter = new IpRateLimiter(method);
		assertThat(ipLimiter.tryAcquire("10.0.0.1")).isTrue();
		assertThat(ipLimiter.tryAcquire("10.0.0.1")).isFalse();
		clock.advance(Duration.ofSeconds(1));
		assertThat(ipLimiter.tryAcquire("10.0.0.1")).isTrue();
	}

}
