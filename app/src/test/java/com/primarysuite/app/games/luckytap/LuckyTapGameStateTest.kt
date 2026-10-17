package com.primarysuite.app.games.luckytap

import com.primarysuite.app.core.protocol.GameMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures match what simple-games backend/cmd/lucky_tap.go marshals:
 * LuckyTapFullStatePayload, NumberPayload, NumbersPayload, DrawErrorPayload,
 * and a null payload for current-collection-cleared.
 */
class LuckyTapGameStateTest {

    private fun msg(json: String) = GameMessage.decode(json)!!

    private val fullState = msg(
        """{"type":"lucky-tap-full-state","payload":{"checkedInNumbers":[12,3,7],""" +
            """"likelihoods":{"3":100,"7":75,"12":0},"currentCollection":[3,3,7],"numWinners":2}}""",
    )

    private val synced = LuckyTapGameState().reduce(fullState)

    @Test
    fun `full state replaces everything`() {
        assertTrue(synced.synced)
        assertEquals(listOf(3, 7, 12), synced.checkedIn)
        assertEquals(mapOf(3 to 100, 7 to 75, 12 to 0), synced.weights)
        assertEquals(mapOf(3 to 2, 7 to 1), synced.roundScans)
        assertEquals(3, synced.scansThisRound)
        assertEquals(2, synced.numWinners)
    }

    @Test
    fun `an empty game from the server`() {
        val empty = msg("""{"type":"lucky-tap-full-state","payload":{"checkedInNumbers":[],"likelihoods":{},"currentCollection":[],"numWinners":1}}""")
        val state = synced.reduce(empty)
        assertTrue(state.checkedIn.isEmpty())
        assertTrue(state.roundScans.isEmpty())
        assertEquals(1, state.numWinners)
    }

    @Test
    fun `null lists from Go are treated as empty`() {
        val state = LuckyTapGameState().reduce(
            msg("""{"type":"lucky-tap-full-state","payload":{"checkedInNumbers":null,"likelihoods":null,"currentCollection":null,"numWinners":3}}"""),
        )
        assertTrue(state.synced)
        assertTrue(state.checkedIn.isEmpty())
        assertEquals(3, state.numWinners)
    }

    @Test
    fun `odds are relative to a fresh card`() {
        assertEquals(100, synced.oddsPercent(3))
        assertEquals(75, synced.oddsPercent(7))
        assertEquals(0, synced.oddsPercent(12))
    }

    @Test
    fun `a scan adds to the round`() {
        val state = synced.reduce(msg("""{"type":"card-activated","payload":{"number":7}}"""))
        assertEquals(mapOf(3 to 2, 7 to 2), state.roundScans)
    }

    @Test
    fun `scanning an unknown card checks it in with full odds`() {
        val state = synced.reduce(msg("""{"type":"card-activated","payload":{"number":5}}"""))
        assertEquals(listOf(3, 5, 7, 12), state.checkedIn)
        assertEquals(100, state.weights[5])
        assertEquals(1, state.roundScans[5])
    }

    @Test
    fun `scan all adds each card once`() {
        val state = synced.reduce(msg("""{"type":"cards-activated-batch","payload":{"numbers":[12,3,7]}}"""))
        assertEquals(mapOf(3 to 3, 7 to 2, 12 to 1), state.roundScans)
    }

    @Test
    fun `check-in updates keep known weights and drop checked-out cards`() {
        val state = synced.reduce(msg("""{"type":"checked-in-cards-update","payload":{"checkedInNumbers":[7,9]}}"""))
        assertEquals(listOf(7, 9), state.checkedIn)
        assertEquals(mapOf(7 to 75, 9 to 100), state.weights)
        assertEquals(mapOf(7 to 1), state.roundScans) // card 3 checked out, so its scans go too
    }

    @Test
    fun `clearing scans keeps check-ins and odds`() {
        val state = synced.reduce(msg("""{"type":"current-collection-cleared","payload":null}"""))
        assertTrue(state.roundScans.isEmpty())
        assertEquals(synced.checkedIn, state.checkedIn)
        assertEquals(synced.weights, state.weights)
    }

    @Test
    fun `a draw runs from tumbler start to the winners`() {
        val drawing = synced.reduce(msg("""{"type":"start-tumbler-animation","payload":{"options":[3,7]}}"""))
        assertTrue(drawing.drawing)

        val done = drawing.reduce(msg("""{"type":"selected-number-final","payload":{"numbers":[3]}}"""))

        assertFalse(done.drawing)
        assertEquals(DrawResult.Winners(listOf(3)), done.lastDraw)
        assertTrue(done.roundScans.isEmpty())
        assertEquals(75, done.weights[3]) // 100 - 25, as the server does
        assertEquals(75, done.weights[7]) // not a winner: unchanged
    }

    @Test
    fun `a winner's weight never drops below zero`() {
        val done = synced.reduce(msg("""{"type":"selected-number-final","payload":{"numbers":[12]}}"""))
        assertEquals(0, done.weights[12])
    }

    @Test
    fun `a failed draw keeps the round`() {
        val drawing = synced.reduce(msg("""{"type":"start-tumbler-animation","payload":{"options":[]}}"""))
        val failed = drawing.reduce(msg("""{"type":"draw-error","payload":{"message":"No cards activated for this round!"}}"""))

        assertFalse(failed.drawing)
        assertEquals(DrawResult.Failed("No cards activated for this round!"), failed.lastDraw)
        assertEquals(synced.roundScans, failed.roundScans)
    }

    @Test
    fun `a full state keeps the last draw and an ongoing draw`() {
        val drawing = synced.reduce(msg("""{"type":"start-tumbler-animation","payload":{"options":[3]}}"""))
        val state = drawing.reduce(fullState)
        assertTrue(state.drawing)
        assertNull(state.lastDraw)
    }

    @Test
    fun `invalid numbers and unknown messages change nothing`() {
        assertSame(synced, synced.reduce(msg("""{"type":"card-activated","payload":{"number":0}}""")))
        assertSame(synced, synced.reduce(msg("""{"type":"card-activated","payload":{}}""")))
        assertSame(synced, synced.reduce(msg("""{"type":"cards-activated-batch","payload":{"numbers":"oops"}}""")))
        assertSame(synced, synced.reduce(msg("""{"type":"some-future-message","payload":{"x":1}}""")))
    }

    @Test
    fun `zero or negative winners in a full state are ignored`() {
        val state = synced.reduce(msg("""{"type":"lucky-tap-full-state","payload":{"checkedInNumbers":[],"likelihoods":{},"currentCollection":[],"numWinners":0}}"""))
        assertEquals(2, state.numWinners)
    }
}
