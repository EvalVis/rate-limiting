package com.evalvis.ratelimiter.mediator;

import jakarta.servlet.http.HttpServletRequest;

public interface RateLimitMediator {

	boolean tryAcquire(HttpServletRequest request);

}
