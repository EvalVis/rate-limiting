package com.evalvis.ratelimiter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "ratelimiter.redis.enabled", havingValue = "true")
public class RatelimiterRedisClientConfiguration {

	@Bean
	public LettuceConnectionFactory ratelimiterRedisConnectionFactory(
			@Value("${spring.data.redis.host:127.0.0.1}") String host,
			@Value("${spring.data.redis.port:6379}") int port) {
		return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
	}

	@Bean
	public StringRedisTemplate ratelimiterStringRedisTemplate(LettuceConnectionFactory ratelimiterRedisConnectionFactory) {
		StringRedisTemplate t = new StringRedisTemplate();
		t.setConnectionFactory(ratelimiterRedisConnectionFactory);
		t.afterPropertiesSet();
		return t;
	}

}
