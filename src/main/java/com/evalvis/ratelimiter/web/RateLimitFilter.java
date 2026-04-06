package com.evalvis.ratelimiter.web;

import com.evalvis.ratelimiter.mediator.RateLimitMediator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimitMediator rateLimitMediator;

	public RateLimitFilter(RateLimitMediator rateLimitMediator) {
		this.rateLimitMediator = rateLimitMediator;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!rateLimitMediator.tryAcquire(request)) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			return;
		}
		filterChain.doFilter(request, response);
	}

}
