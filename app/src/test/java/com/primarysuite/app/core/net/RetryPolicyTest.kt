package com.primarysuite.app.core.net

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `matches the web - half a second doubling to four, then every five`() {
        val delays = (0 until 8).map { RetryPolicy().delayFor(it) }
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 5_000L, 5_000L, 5_000L, 5_000L), delays)
    }

    @Test
    fun `never gives up by default`() {
        assertEquals(RetryPolicy.UNLIMITED, RetryPolicy().maxRetries)
        assertEquals(5_000L, RetryPolicy().delayFor(Int.MAX_VALUE))
    }

    @Test
    fun `custom base and cap`() {
        val policy = RetryPolicy(baseDelayMs = 10, maxDelayMs = 50)
        assertEquals(listOf(10L, 20L, 40L, 50L), (0..3).map { policy.delayFor(it) })
    }

    @Test
    fun `pings often enough to notice a dead connection within about twenty seconds`() {
        assertEquals(10_000, WebSocketManager.defaultClient().pingIntervalMillis)
    }
}
