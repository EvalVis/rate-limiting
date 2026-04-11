package com.evalvis.loadbalancer.web

import org.springframework.http.HttpHeaders
import java.util.Locale

object HopByHopHeaders {

	private val requestSkip = setOf(
		"connection",
		"keep-alive",
		"proxy-connection",
		"transfer-encoding",
		"upgrade",
		"host",
		"content-length",
	)

	private val responseSkip = setOf(
		"connection",
		"keep-alive",
		"proxy-connection",
		"transfer-encoding",
		"upgrade",
	)

	fun skipRequestHeader(name: String): Boolean {
		return requestSkip.contains(name.lowercase(Locale.ROOT))
	}

	fun filterResponse(source: HttpHeaders): HttpHeaders {
		val out = HttpHeaders()
		source.forEach { name, values ->
			if (!responseSkip.contains(name.lowercase(Locale.ROOT))) {
				out.addAll(name, values)
			}
		}
		return out
	}
}
