package com.evalvis.loadbalancer.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ClientIpResolverTest {

	@Test
	fun usesFirstForwardedHopWhenPresent() {
		val request = MockHttpServletRequest()
		request.addHeader("X-Forwarded-For", " 203.0.113.1 , 198.51.100.2 ")
		request.remoteAddr = "127.0.0.1"
		assertEquals("203.0.113.1", ClientIpResolver.resolve(request))
	}

	@Test
	fun usesRemoteAddrWhenNoForwardedHeader() {
		val request = MockHttpServletRequest()
		request.remoteAddr = "10.0.0.5"
		assertEquals("10.0.0.5", ClientIpResolver.resolve(request))
	}
}
