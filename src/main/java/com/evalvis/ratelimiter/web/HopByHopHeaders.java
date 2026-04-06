package com.evalvis.ratelimiter.web;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;

public final class HopByHopHeaders {

	private static final Set<String> REQUEST_SKIP = Set.of(
		"connection",
		"keep-alive",
		"proxy-connection",
		"transfer-encoding",
		"upgrade",
		"host",
		"content-length"
	);

	private static final Set<String> RESPONSE_SKIP = Set.of(
		"connection",
		"keep-alive",
		"proxy-connection",
		"transfer-encoding",
		"upgrade"
	);

	private HopByHopHeaders() {
	}

	public static boolean skipRequestHeader(String name) {
		return REQUEST_SKIP.contains(name.toLowerCase(Locale.ROOT));
	}

	public static HttpHeaders filterResponse(HttpHeaders source) {
		HttpHeaders out = new HttpHeaders();
		source.forEach((name, values) -> {
			if (!RESPONSE_SKIP.contains(name.toLowerCase(Locale.ROOT))) {
				out.put(name, values);
			}
		});
		return out;
	}

}
