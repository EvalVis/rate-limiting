package com.evalvis.ratelimiter.config;

import com.evalvis.ratelimiter.key.IpRateLimitKeyResolver;
import com.evalvis.ratelimiter.key.RateLimitKeyResolver;
import com.evalvis.ratelimiter.mediator.DefaultRateLimitMediator;
import com.evalvis.ratelimiter.mediator.RateLimitMediator;
import com.evalvis.ratelimiter.rate.IpRateLimiter;
import com.evalvis.ratelimiter.rate.RateLimiter;
import com.evalvis.ratelimiter.rate.TokenBucketRateLimiter;
import com.evalvis.ratelimiter.selector.FixedRateLimiterSelector;
import com.evalvis.ratelimiter.selector.JwtRoleRateLimiterSelector;
import com.evalvis.ratelimiter.selector.RateLimiterSelector;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(RatelimiterProperties.class)
public class RatelimiterConfiguration {

	@Bean
	public Clock systemClock() {
		return Clock.systemUTC();
	}

	@Bean
	@ConditionalOnMissingBean(RateLimiter.class)
	public TokenBucketRateLimiter tokenBucketRateLimiter(RatelimiterProperties properties, Clock clock) {
		return new TokenBucketRateLimiter(
			properties.getRateLimit().getCapacity(),
			properties.getRateLimit().getRefillPerSecond(),
			clock
		);
	}

	@Bean
	@ConditionalOnBean(TokenBucketRateLimiter.class)
	public IpRateLimiter ipRateLimiter(TokenBucketRateLimiter tokenBucketRateLimiter) {
		return new IpRateLimiter(tokenBucketRateLimiter);
	}

	@Bean
	@ConditionalOnMissingBean(RateLimitKeyResolver.class)
	public RateLimitKeyResolver rateLimitKeyResolver() {
		return new IpRateLimitKeyResolver();
	}

	@Bean
	@ConditionalOnMissingBean(RateLimiterSelector.class)
	public RateLimiterSelector rateLimiterSelector(RatelimiterProperties properties, RateLimiter rateLimiter, Clock clock) {
		if (!properties.getJwt().isConfigured()) {
			return new FixedRateLimiterSelector(rateLimiter);
		}
		RateLimiter adminLimiter = new TokenBucketRateLimiter(
			properties.getAdminRateLimit().getCapacity(),
			properties.getAdminRateLimit().getRefillPerSecond(),
			clock
		);
		return new JwtRoleRateLimiterSelector(
			properties.getJwt().getSecret(),
			properties.getJwt().getRoleClaim(),
			properties.getJwt().getAdminRoleValue(),
			rateLimiter,
			adminLimiter
		);
	}

	@Bean
	@ConditionalOnMissingBean(RateLimitMediator.class)
	public RateLimitMediator rateLimitMediator(RateLimitKeyResolver keyResolver, RateLimiterSelector rateLimiterSelector) {
		return new DefaultRateLimitMediator(keyResolver, rateLimiterSelector);
	}

	@Bean
	public WebClient forwardWebClient(RatelimiterProperties properties) {
		return WebClient.builder()
			.baseUrl(properties.getForward().baseUrl())
			.build();
	}

}
