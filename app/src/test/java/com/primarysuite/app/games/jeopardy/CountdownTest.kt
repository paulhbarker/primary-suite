package com.primarysuite.app.games.jeopardy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownTest {

    private val answering = Fixtures.state("answering") // deadline 1760700005000, total 5000, kind answer

    @Test
    fun `counts down in server time`() {
        // Phone clock 2 s behind the server: offset +2000.
        val c = Countdown.of(answering, clockOffsetMs = 2_000, nowMs = 1760700000000)
        assertTrue(c.active)
        assertEquals(3_000, c.remainingMs)
        assertEquals(0.6f, c.fraction, 0.001f)
        assertEquals("answer", c.kind)
    }

    @Test
    fun `stops at zero`() {
        val c = Countdown.of(answering, clockOffsetMs = 0, nowMs = 1760700009000)
        assertFalse(c.active)
        assertEquals(0, c.remainingMs)
        assertEquals(0f, c.fraction)
    }

    @Test
    fun `fraction never exceeds one`() {
        assertEquals(1f, Countdown.of(answering, clockOffsetMs = -10_000, nowMs = 1760700000000).fraction)
    }

    @Test
    fun `idle without a deadline or a total`() {
        assertEquals(Countdown.IDLE, Countdown.of(null, 0, 0))
        assertEquals(Countdown.IDLE, Countdown.of(Fixtures.state("board"), 0, 0))
        assertEquals(Countdown.IDLE, Countdown.of(answering.copy(deadlineTotalMs = 0), 0, 1760700000000))
    }
}
