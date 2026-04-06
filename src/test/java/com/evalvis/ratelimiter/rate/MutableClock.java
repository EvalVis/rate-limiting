package com.evalvis.ratelimiter.rate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

final class MutableClock extends Clock {

	private Instant instant;
	private final ZoneId zone;

	MutableClock(Instant initial, ZoneId zone) {
		this.instant = initial;
		this.zone = zone;
	}

	void advance(Duration duration) {
		instant = instant.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(instant, zone);
	}

	@Override
	public Instant instant() {
		return instant;
	}

}
