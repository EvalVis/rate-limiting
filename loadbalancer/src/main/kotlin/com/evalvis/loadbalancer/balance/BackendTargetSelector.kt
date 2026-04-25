package com.evalvis.loadbalancer.balance

import jakarta.servlet.http.HttpServletRequest

interface BackendTargetSelector {

	fun selectTarget(request: HttpServletRequest): String

	fun selectTargets(request: HttpServletRequest): List<String> = listOf(selectTarget(request))

	fun selectFallbackTarget(request: HttpServletRequest): String? = null

	fun isBroadcast(request: HttpServletRequest): Boolean = false
}
