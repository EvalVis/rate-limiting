package com.evalvis.ratelimiter.key;

import jakarta.servlet.http.HttpServletRequest;

public final class IpRateLimitKeyResolver implements RateLimitKeyResolver {

	@Override
	public String resolveKey(HttpServletRequest request) {
		return ClientIpResolver.resolve(request);
	}

}
