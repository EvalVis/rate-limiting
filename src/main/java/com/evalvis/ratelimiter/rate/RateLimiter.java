package com.evalvis.ratelimiter.rate;

public interface RateLimiter {

	boolean tryAcquire(String key);

}
