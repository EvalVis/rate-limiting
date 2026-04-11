package com.evalvis.ratelimiter.selector;

import static org.assertj.core.api.Assertions.assertThat;

import com.evalvis.ratelimiter.rate.RateLimiter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtRoleRateLimiterSelectorTest {

	private static final String SECRET = "test-secret-key-at-least-32-bytes-long!!";
	private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

	@Test
	void whenNoAuthorizationHeader_selectsDefaultLimiter() {
		SelectingLimiter def = new SelectingLimiter();
		SelectingLimiter admin = new SelectingLimiter();
		JwtRoleRateLimiterSelector selector = selector(def, admin);
		RateLimiter chosen = selector.select(new MockHttpServletRequest());
		assertThat(chosen).isSameAs(def);
	}

	@Test
	void whenBearerMissingToken_selectsDefaultLimiter() {
		SelectingLimiter def = new SelectingLimiter();
		SelectingLimiter admin = new SelectingLimiter();
		JwtRoleRateLimiterSelector selector = selector(def, admin);
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("Authorization", "Bearer ");
		assertThat(selector.select(req)).isSameAs(def);
	}

	@Test
	void whenJwtInvalid_selectsDefaultLimiter() {
		SelectingLimiter def = new SelectingLimiter();
		SelectingLimiter admin = new SelectingLimiter();
		JwtRoleRateLimiterSelector selector = selector(def, admin);
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("Authorization", "Bearer not-a-jwt");
		assertThat(selector.select(req)).isSameAs(def);
	}

	@Test
	void whenRoleIsUser_selectsDefaultLimiter() {
		SelectingLimiter def = new SelectingLimiter();
		SelectingLimiter admin = new SelectingLimiter();
		JwtRoleRateLimiterSelector selector = selector(def, admin);
		String jwt = Jwts.builder()
			.subject("u1")
			.claim("role", "user")
			.expiration(Date.from(Instant.now().plusSeconds(3600)))
			.signWith(KEY)
			.compact();
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("Authorization", "Bearer " + jwt);
		assertThat(selector.select(req)).isSameAs(def);
	}

	@Test
	void whenRoleIsAdmin_selectsAdminLimiter() {
		SelectingLimiter def = new SelectingLimiter();
		SelectingLimiter admin = new SelectingLimiter();
		JwtRoleRateLimiterSelector selector = selector(def, admin);
		String jwt = Jwts.builder()
			.subject("a1")
			.claim("role", "admin")
			.expiration(Date.from(Instant.now().plusSeconds(3600)))
			.signWith(KEY)
			.compact();
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("Authorization", "Bearer " + jwt);
		assertThat(selector.select(req)).isSameAs(admin);
	}

	@Test
	void whenRoleClaimIsArrayContainingAdmin_selectsAdminLimiter() {
		SelectingLimiter def = new SelectingLimiter();
		SelectingLimiter admin = new SelectingLimiter();
		JwtRoleRateLimiterSelector selector = selector(def, admin);
		String jwt = Jwts.builder()
			.subject("a1")
			.claim("role", List.of("user", "admin"))
			.expiration(Date.from(Instant.now().plusSeconds(3600)))
			.signWith(KEY)
			.compact();
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("Authorization", "Bearer " + jwt);
		assertThat(selector.select(req)).isSameAs(admin);
	}

	@Test
	void adminMatchIsCaseInsensitive() {
		SelectingLimiter def = new SelectingLimiter();
		SelectingLimiter admin = new SelectingLimiter();
		JwtRoleRateLimiterSelector selector = selector(def, admin);
		String jwt = Jwts.builder()
			.subject("a1")
			.claim("role", "ADMIN")
			.expiration(Date.from(Instant.now().plusSeconds(3600)))
			.signWith(KEY)
			.compact();
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("Authorization", "Bearer " + jwt);
		assertThat(selector.select(req)).isSameAs(admin);
	}

	private static JwtRoleRateLimiterSelector selector(SelectingLimiter def, SelectingLimiter admin) {
		return new JwtRoleRateLimiterSelector(SECRET, "role", "admin", def, admin);
	}

	private static final class SelectingLimiter implements RateLimiter {

		@Override
		public boolean tryAcquire(String key) {
			return false;
		}

	}

}
