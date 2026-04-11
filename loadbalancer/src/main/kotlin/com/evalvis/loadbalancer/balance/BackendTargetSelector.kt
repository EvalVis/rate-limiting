package com.evalvis.loadbalancer.balance

import jakarta.servlet.http.HttpServletRequest

fun interface BackendTargetSelector {

	fun selectTarget(request: HttpServletRequest): String
}
