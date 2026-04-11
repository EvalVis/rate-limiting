package com.evalvis.ratelimiter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class ProxyController {

	private final WebClient forwardWebClient;

	public ProxyController(@Qualifier("forwardWebClient") WebClient forwardWebClient) {
		this.forwardWebClient = forwardWebClient;
	}

	@RequestMapping(
			value = "/**",
			method = {
				RequestMethod.GET,
				RequestMethod.HEAD,
				RequestMethod.POST,
				RequestMethod.PUT,
				RequestMethod.PATCH,
				RequestMethod.DELETE,
				RequestMethod.OPTIONS
			})
	public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
		String relativeUri = buildRelativeUri(request);
		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		return forwardWebClient.method(method)
			.uri(relativeUri)
			.headers(h -> copyRequestHeaders(request, h))
			.body(BodyInserters.fromResource(new InputStreamResource(request.getInputStream())))
			.exchangeToMono(this::toResponseEntity)
			.block();
	}

	private static String buildRelativeUri(HttpServletRequest request) {
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}
		if (path.isEmpty()) {
			path = "/";
		}
		String query = request.getQueryString();
		if (query == null || query.isEmpty()) {
			return path;
		}
		return path + "?" + query;
	}

	private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders target) {
		Enumeration<String> names = request.getHeaderNames();
		if (names == null) {
			return;
		}
		for (String name : Collections.list(names)) {
			if (HopByHopHeaders.skipRequestHeader(name)) {
				continue;
			}
			Enumeration<String> values = request.getHeaders(name);
			for (String value : Collections.list(values)) {
				target.add(name, value);
			}
		}
	}

	private Mono<ResponseEntity<byte[]>> toResponseEntity(ClientResponse response) {
		return response.bodyToMono(byte[].class)
			.defaultIfEmpty(new byte[0])
			.map(body -> new ResponseEntity<byte[]>(
					body,
					HopByHopHeaders.filterResponse(response.headers().asHttpHeaders()),
					response.statusCode()));
	}

}
