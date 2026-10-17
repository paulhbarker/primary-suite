package com.primarysuite.app.core.net

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `doubles from one second and caps at thirty`() {
        val delays = (0 until 10).map { RetryPolicy().delayFor(it) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L, 30_000L), delays)
    }

    @Test
    fun `custom base and cap`() {
        val policy = RetryPolicy(baseDelayMs = 10, maxDelayMs = 50)
        assertEquals(listOf(10L, 20L, 40L, 50L), (0..3).map { policy.delayFor(it) })
    }
}
