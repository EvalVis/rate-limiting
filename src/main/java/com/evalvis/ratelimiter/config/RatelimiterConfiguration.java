package com.evalvis.ratelimiter.config;

import com.evalvis.ratelimiter.key.IpRateLimitKeyResolver;
import com.evalvis.ratelimiter.key.RateLimitKeyResolver;
import com.evalvis.ratelimiter.mediator.DefaultRateLimitMediator;
import com.evalvis.ratelimiter.mediator.RateLimitMediator;
import com.evalvis.ratelimiter.rate.IpRateLimiter;
import com.evalvis.ratelimiter.rate.RateLimiter;
import com.evalvis.ratelimiter.rate.TokenBucketRateLimiter;
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
	@ConditionalOnMissingBean(RateLimitMediator.class)
	public RateLimitMediator rateLimitMediator(RateLimitKeyResolver keyResolver, RateLimiter rateLimiter) {
		return new DefaultRateLimitMediator(keyResolver, rateLimiter);
	}

	@Bean
	public WebClient forwardWebClient(RatelimiterProperties properties) {
		return WebClient.builder()
			.baseUrl(properties.getForward().baseUrl())
			.build();
	}

}
