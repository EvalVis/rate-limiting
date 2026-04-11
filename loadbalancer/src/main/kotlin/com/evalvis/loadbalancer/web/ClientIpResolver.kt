package com.evalvis.loadbalancer.web

import jakarta.servlet.http.HttpServletRequest

object ClientIpResolver {

	fun resolve(request: HttpServletRequest): String {
		val xff = request.getHeader("X-Forwarded-For")
		if (!xff.isNullOrBlank()) {
			return xff.split(",").first().trim()
		}
		return request.remoteAddr
	}
}
