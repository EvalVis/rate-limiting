package com.evalvis.ratelimiter.selector;

import com.evalvis.ratelimiter.rate.RateLimiter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.SecretKey;

public final class JwtRoleRateLimiterSelector implements RateLimiterSelector {

	private final SecretKey key;
	private final String roleClaim;
	private final String adminRoleValue;
	private final RateLimiter defaultLimiter;
	private final RateLimiter adminLimiter;

	public JwtRoleRateLimiterSelector(
		String secret,
		String roleClaim,
		String adminRoleValue,
		RateLimiter defaultLimiter,
		RateLimiter adminLimiter
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.roleClaim = roleClaim;
		this.adminRoleValue = adminRoleValue.toLowerCase(Locale.ROOT);
		this.defaultLimiter = defaultLimiter;
		this.adminLimiter = adminLimiter;
	}

	@Override
	public RateLimiter select(HttpServletRequest request) {
		Optional<String> token = bearerToken(request);
		if (token.isEmpty()) {
			return defaultLimiter;
		}
		try {
			Claims claims = Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token.get())
				.getPayload();
			if (isAdmin(claims)) {
				return adminLimiter;
			}
		} catch (JwtException e) {
			return defaultLimiter;
		}
		return defaultLimiter;
	}

	private boolean isAdmin(Claims claims) {
		Object raw = claims.get(roleClaim);
		if (raw == null) {
			return false;
		}
		if (raw instanceof String s) {
			return adminRoleValue.equals(s.toLowerCase(Locale.ROOT));
		}
		if (raw instanceof Collection<?> c) {
			for (Object o : c) {
				if (o != null && adminRoleValue.equals(o.toString().toLowerCase(Locale.ROOT))) {
					return true;
				}
			}
		}
		return false;
	}

	private static Optional<String> bearerToken(HttpServletRequest request) {
		String h = request.getHeader("Authorization");
		if (h == null || h.length() < 7 || !h.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return Optional.empty();
		}
		String t = h.substring(7).trim();
		return t.isEmpty() ? Optional.empty() : Optional.of(t);
	}

}
