package com.evalvis.loadbalancer.web

import com.evalvis.loadbalancer.balance.BackendTargetSelector
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.core.io.InputStreamResource
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI
import java.util.Collections
import java.util.Enumeration

@RestController
class ProxyController(
	private val backendTargetSelector: BackendTargetSelector,
	private val forwardWebClient: WebClient,
) {

	@RequestMapping(
		value = ["/**"],
		method = [
			RequestMethod.GET,
			RequestMethod.HEAD,
			RequestMethod.POST,
			RequestMethod.PUT,
			RequestMethod.PATCH,
			RequestMethod.DELETE,
			RequestMethod.OPTIONS,
		],
	)
	fun proxy(request: HttpServletRequest): ResponseEntity<ByteArray> {
		val relativeUri = buildRelativeUri(request)
		val method = HttpMethod.valueOf(request.method)

		if (backendTargetSelector.isBroadcast(request)) {
			val targets = backendTargetSelector.selectTargets(request)
			val body = request.inputStream.readAllBytes()
			val responses = targets.map { baseUrl ->
				val targetUri = resolveTargetUri(baseUrl, relativeUri)
				forwardWebClient.method(method)
					.uri(targetUri)
					.headers { copyRequestHeaders(request, it) }
					.bodyValue(body)
					.exchangeToMono { toResponseEntity(it) }
			}
			return Mono.zip(responses) { it.first() as ResponseEntity<ByteArray> }
				.block()!!
		} else {
			val primaryTarget = backendTargetSelector.selectTarget(request)
			val targetUri = resolveTargetUri(primaryTarget, relativeUri)
			
			// We might need the body multiple times for fallback, but only for GET it's usually empty.
			// However, let's keep it simple for now.
			
			val response = forwardWebClient.method(method)
				.uri(targetUri)
				.headers { copyRequestHeaders(request, it) }
				.body(BodyInserters.fromResource(InputStreamResource(request.inputStream)))
				.exchangeToMono { toResponseEntity(it) }
				.block()!!

			if (response.statusCode.value() == 404) {
				val fallbackTarget = backendTargetSelector.selectFallbackTarget(request)
				if (fallbackTarget != null) {
					val fallbackUri = resolveTargetUri(fallbackTarget, relativeUri)
					val fallbackResponse = forwardWebClient.method(method)
						.uri(fallbackUri)
						.headers { copyRequestHeaders(request, it) }
						.exchangeToMono { toResponseEntity(it) }
						.block()!!

					if (fallbackResponse.statusCode.is2xxSuccessful && fallbackResponse.body != null) {
						// Lazy Migration: write to primary target asynchronously
						val primaryUri = resolveTargetUri(primaryTarget, relativeUri)
						forwardWebClient.put()
							.uri(primaryUri)
							.headers { copyRequestHeaders(request, it) }
							.bodyValue(fallbackResponse.body!!)
							.retrieve()
							.toBodilessEntity()
							.subscribe() 
					}
					return fallbackResponse
				}
			}
			return response
		}
	}

	private fun resolveTargetUri(base: String, relativeUri: String): URI {
		val normalizedBase = base.trimEnd('/')
		val path = if (relativeUri.startsWith("/")) relativeUri else "/$relativeUri"
		return URI.create(normalizedBase + path)
	}

	private fun buildRelativeUri(request: HttpServletRequest): String {
		var path = request.requestURI
		val context = request.contextPath
		if (context.isNotEmpty() && path.startsWith(context)) {
			path = path.substring(context.length)
		}
		if (path.isEmpty()) {
			path = "/"
		}
		val query = request.queryString
		return if (query.isNullOrEmpty()) {
			path
		} else {
			"$path?$query"
		}
	}

	private fun copyRequestHeaders(request: HttpServletRequest, target: HttpHeaders) {
		val names = request.headerNames ?: return
		for (name in Collections.list(names)) {
			if (HopByHopHeaders.skipRequestHeader(name)) {
				continue
			}
			val values = request.getHeaders(name)
			for (value in Collections.list(values)) {
				target.add(name, value)
			}
		}
	}

	private fun toResponseEntity(response: ClientResponse): Mono<ResponseEntity<ByteArray>> {
		return response.bodyToMono(ByteArray::class.java)
			.defaultIfEmpty(ByteArray(0))
			.map { body ->
				ResponseEntity.status(response.statusCode())
					.headers(HopByHopHeaders.filterResponse(response.headers().asHttpHeaders()))
					.body(body)
			}
	}
}
