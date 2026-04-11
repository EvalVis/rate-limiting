package com.evalvis.loadbalancer.balance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RoundRobinTargetPickerTest {

	@Test
	fun whenTwoTargetsThenAlternatesInOrder() {
		val picker = RoundRobinTargetPicker(listOf("http://a", "http://b"))
		assertEquals("http://a", picker.next())
		assertEquals("http://b", picker.next())
		assertEquals("http://a", picker.next())
		assertEquals("http://b", picker.next())
	}

	@Test
	fun whenSingleTargetThenAlwaysSame() {
		val picker = RoundRobinTargetPicker(listOf("http://only"))
		assertEquals("http://only", picker.next())
		assertEquals("http://only", picker.next())
	}

	@Test
	fun whenEmptyListThenThrows() {
		assertThrows(IllegalStateException::class.java) {
			RoundRobinTargetPicker(emptyList())
		}
	}
}
