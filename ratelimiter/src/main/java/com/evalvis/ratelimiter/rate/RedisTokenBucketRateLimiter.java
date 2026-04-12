package com.evalvis.ratelimiter.rate;

import java.util.Collections;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisTokenBucketRateLimiter implements RateLimiter {

	private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

	static {
		String lua = """
				local capacity = tonumber(ARGV[1])
				local refillPerSecond = tonumber(ARGV[2])
				local t = redis.call('TIME')
				local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
				local tokensStr = redis.call('HGET', KEYS[1], 'tokens')
				local lastStr = redis.call('HGET', KEYS[1], 'last_ms')
				local tokens
				local last_ms
				if not tokensStr then
				  tokens = capacity
				  last_ms = now
				else
				  tokens = tonumber(tokensStr)
				  last_ms = tonumber(lastStr)
				  local elapsed = now - last_ms
				  if elapsed > 0 then
				    local added = (elapsed / 1000.0) * refillPerSecond
				    tokens = math.min(capacity, tokens + added)
				    last_ms = now
				  end
				end
				if tokens >= 1.0 then
				  tokens = tokens - 1.0
				  redis.call('HSET', KEYS[1], 'tokens', string.format('%.17g', tokens), 'last_ms', string.format('%.17g', last_ms))
				  return 1
				end
				redis.call('HSET', KEYS[1], 'tokens', string.format('%.17g', tokens), 'last_ms', string.format('%.17g', last_ms))
				return 0
				""";
		SCRIPT.setScriptText(lua);
		SCRIPT.setResultType(Long.class);
	}

	private final StringRedisTemplate redis;
	private final double capacity;
	private final double refillPerSecond;
	private final String keyPrefix;

	public RedisTokenBucketRateLimiter(StringRedisTemplate redis, double capacity, double refillPerSecond) {
		this(redis, capacity, refillPerSecond, "rl:tb:");
	}

	public RedisTokenBucketRateLimiter(StringRedisTemplate redis, double capacity, double refillPerSecond, String keyPrefix) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		if (refillPerSecond < 0) {
			throw new IllegalArgumentException("refillPerSecond must be non-negative");
		}
		this.redis = redis;
		this.capacity = capacity;
		this.refillPerSecond = refillPerSecond;
		this.keyPrefix = keyPrefix;
	}

	@Override
	public boolean tryAcquire(String key) {
		String redisKey = keyPrefix + key;
		Long r = redis.execute(SCRIPT, Collections.singletonList(redisKey), Double.toString(capacity), Double.toString(refillPerSecond));
		return r != null && r.longValue() == 1L;
	}

}
