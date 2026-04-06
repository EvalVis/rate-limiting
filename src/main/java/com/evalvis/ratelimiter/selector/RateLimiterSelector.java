package com.evalvis.ratelimiter.selector;

import com.evalvis.ratelimiter.rate.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;

public interface RateLimiterSelector {

	RateLimiter select(HttpServletRequest request);

}
