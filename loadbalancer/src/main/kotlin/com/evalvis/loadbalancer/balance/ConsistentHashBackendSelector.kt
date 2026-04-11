package com.evalvis.loadbalancer.balance

import com.evalvis.loadbalancer.web.ClientIpResolver
import jakarta.servlet.http.HttpServletRequest

class ConsistentHashBackendSelector(
	private val ring: ConsistentHashRing,
) : BackendTargetSelector {

	override fun selectTarget(request: HttpServletRequest): String {
		return ring.assign(ClientIpResolver.resolve(request))
	}
}
