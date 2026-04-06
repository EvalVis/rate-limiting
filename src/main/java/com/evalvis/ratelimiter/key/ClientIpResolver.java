package com.evalvis.ratelimiter.key;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

	private ClientIpResolver() {
	}

	public static String resolve(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
			return first.trim();
		}
		return request.getRemoteAddr();
	}

}
