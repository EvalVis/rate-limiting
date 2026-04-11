package com.evalvis.ratelimiter.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

	@Test
	void whenBucketIsFull_consumeSucceeds() {
		Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		TokenBucket bucket = new TokenBucket(3, 1.0, clock);
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isTrue();
	}

	@Test
	void whenTokensExhausted_consumeFailsUntilRefill() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		TokenBucket bucket = new TokenBucket(2, 1.0, clock);
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isFalse();
		clock.advance(Duration.ofMillis(500));
		assertThat(bucket.tryConsume()).isFalse();
		clock.advance(Duration.ofMillis(500));
		assertThat(bucket.tryConsume()).isTrue();
	}

	@Test
	void refillNeverExceedsCapacity() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		TokenBucket bucket = new TokenBucket(2, 100.0, clock);
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isTrue();
		clock.advance(Duration.ofSeconds(10));
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isFalse();
	}

}
