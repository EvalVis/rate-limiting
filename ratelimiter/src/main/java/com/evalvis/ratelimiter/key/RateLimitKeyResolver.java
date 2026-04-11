package com.evalvis.ratelimiter.key;

import jakarta.servlet.http.HttpServletRequest;

public interface RateLimitKeyResolver {

	String resolveKey(HttpServletRequest request);

}
