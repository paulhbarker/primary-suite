package com.primarysuite.app.games.buttondebug

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonDebugSessionTest {

    private fun session(snap: DebugSnapshot = snapshot()) = DebugSession().onSnapshot(snap)

    @Test
    fun `a snapshot sets up empty stats for every button`() {
        val s = session()
        assertTrue(s.hasBox)
        assertEquals(TWO_BUTTONS, s.buttons)
        assertEquals(emptyStats(), s.statsFor(0))
        assertEquals(emptyStats(), s.statsFor(1))
        assertTrue(s.log.isEmpty())
        assertNull(s.firstTsUs)
    }

    @Test
    fun `a snapshot replays its history without marking presses live`() {
        val s = session(
            snapshot(
                events = Tap.down(1_000_000, seq = 5) + Tap.up(1_500_000),
                presses = listOf(press(id = 4, seq = 5, hubUs = 450)),
            ),
        )
        val st = s.statsFor(0)
        assertEquals(1, st.presses)
        assertFalse(st.live)
        assertEquals(1, st.delivered)
        assertEquals(450L, st.hubMaxUs)
        assertEquals(500.0, st.lastHoldMs!!, 0.0)
        assertEquals(1_000_000L, s.firstTsUs)
        assertEquals(7, s.log.size)
        assertEquals(emptyStats(), s.statsFor(1))
    }

    @Test
    fun `replaying the fixture gives the same stats the web would`() {
        val snap = DebugSnapshot.parse(JSONObject(javaClass.getResource("/buttondebug/snapshot.json")!!.readText()))
        val s = session(snap)
        val st = s.statsFor(0)
        assertEquals(1, st.presses)
        assertEquals(3, st.edges)
        assertEquals(2L, st.kernelDropped)
        assertEquals(300L, st.maxPressBounceUs)
        assertEquals(0L, st.maxReleaseBounceUs)
        assertEquals(1, st.delivered) // seq 6 has no edge in the history, so it's skipped
        assertEquals(500.0, st.lastHoldMs!!, 0.0)
        assertEquals(listOf(9), s.wifi.keys.toList())
        assertEquals(-61, s.wifi[9]!!.rssi)
        assertEquals(listOf(3), s.disabledIds)
        assertEquals(listOf("lucky-tap"), s.activeGames)
        // 5 edges, then the wifi press; newest first.
        assertTrue(s.log.first() is LogRow.Wifi)
        assertEquals(6, s.log.size)
    }

    @Test
    fun `box presses with no edge in the history are not replayed`() {
        val s = session(snapshot(events = Tap.down(0, seq = 5), presses = listOf(press(id = 4, seq = 5), press(id = 4, seq = 4), press(id = 4, seq = 0))))
        assertEquals(1, s.statsFor(0).delivered)
    }

    @Test
    fun `wifi presses are always replayed and appear in the log`() {
        val s = session(snapshot(presses = listOf(press(id = 9, seq = 1, source = "wifi", rssi = -50), press(id = 9, seq = 2, source = "wifi"))))
        assertEquals(2, s.wifi[9]!!.presses)
        assertEquals(2, s.log.size)
        assertTrue(s.log.all { it is LogRow.Wifi })
    }

    @Test
    fun `a new snapshot replaces everything, as after a reset`() {
        var s = session(snapshot(events = Tap.down(0)))
        s = s.onPress(press(id = 9, seq = 1, source = "wifi"))
        assertEquals(1, s.statsFor(0).presses)
        s = s.onSnapshot(snapshot())
        assertEquals(0, s.statsFor(0).presses)
        assertTrue(s.wifi.isEmpty())
        assertTrue(s.log.isEmpty())
        assertNull(s.firstTsUs)
    }

    @Test
    fun `a snapshot without a box has no buttons or tiles`() {
        val s = session(snapshot(box = false, boxError = "BUTTON_BOX is not set", buttons = emptyList(), status = null))
        assertFalse(s.hasBox)
        assertEquals("BUTTON_BOX is not set", s.info!!.boxError)
        assertTrue(s.buttons.isEmpty())
        assertEquals(0, s.issueCount)
        assertEquals(0, s.totalPresses)
        assertTrue(s.disabledIds.isEmpty())
    }

    @Test
    fun `a live edge updates its button and the log`() {
        var s = session()
        s = s.onEdge(edge(button = 1, id = 3, tsUs = 500, verdict = Verdict.PRESS, seq = 1), live = true)
        assertEquals(1, s.statsFor(1).presses)
        assertTrue(s.statsFor(1).live)
        assertEquals(0, s.statsFor(0).presses)
        assertEquals(500L, s.firstTsUs)
        s = s.onEdge(edge(button = 0, id = 4, tsUs = 900), live = true)
        assertEquals(500L, s.firstTsUs)
        // Newest first.
        assertEquals(listOf(1L, 0L), s.log.map { it.id })
        assertEquals(4, (s.log[0] as LogRow.Edge).ev.id)
    }

    @Test
    fun `an edge for a button the snapshot didn't list still gets stats`() {
        val s = session().onEdge(edge(button = 7, id = 8, verdict = Verdict.PRESS), live = true)
        assertEquals(1, s.statsFor(7).presses)
    }

    @Test
    fun `the log keeps the newest 200`() {
        var s = session()
        repeat(250) { s = s.onEdge(edge(tsUs = it.toLong()), live = true) }
        assertEquals(200, s.log.size)
        assertEquals(249L, s.log.first().id)
        assertEquals(50L, s.log.last().id)
        assertEquals(250, s.statsFor(0).edges)
    }

    @Test
    fun `a box press is matched to its button by id`() {
        var s = session()
        s = s.onPress(press(id = 3, hubUs = 600))
        assertEquals(1, s.statsFor(1).delivered)
        assertEquals(0, s.statsFor(0).delivered)
        assertTrue("a box press is not logged", s.log.isEmpty())
    }

    @Test
    fun `a box press for an unknown button, or before any snapshot, is ignored`() {
        assertEquals(session(), session().onPress(press(id = 99)))
        val before = DebugSession()
        assertEquals(before, before.onPress(press(id = 4)))
    }

    @Test
    fun `wifi presses are tracked by id in id order and logged`() {
        var s = session()
        s = s.onPress(press(id = 12, seq = 1, source = "wifi", rssi = -80))
        s = s.onPress(press(id = 9, seq = 1, source = "wifi"))
        s = s.onPress(press(id = 12, seq = 4, source = "wifi"))
        assertEquals(listOf(9, 12), s.wifi.keys.toList())
        assertEquals(2L, s.wifi[12]!!.lostSeq)
        assertEquals(3, s.log.size)
        assertEquals(12, (s.log.first() as LogRow.Wifi).press.id)
    }

    @Test
    fun `status replaces the status`() {
        val s = session().onStatus(status(quietMs = 30.0))
        assertEquals(30.0, s.status!!.quietMs, 0.0)
    }

    @Test
    fun `issue count and presses add up across buttons`() {
        var s = session(snapshot(status = status(stuck = listOf(true, false))))
        assertEquals(1, s.issueCount) // button 0 is stuck
        s = s.onEdge(edge(button = 1, id = 3, tsUs = 0, verdict = Verdict.PRESS, seq = 1), true)
        s = s.onEdge(edge(button = 1, id = 3, tsUs = 100, verdict = Verdict.PRESS, seq = 2), true)
        assertEquals(2, s.totalPresses)
        // Two presses, none delivered: one in flight is fine, two is a problem.
        assertEquals(2, s.issueCount)
        s = s.onPress(press(id = 3))
        assertEquals(1, s.issueCount)
    }

    @Test
    fun `disabled ids come from the status`() {
        assertTrue(session().disabledIds.isEmpty())
        assertEquals(listOf(3), session(snapshot(status = status(enabled = listOf(true, false)))).disabledIds)
        assertEquals(listOf(4, 3), session(snapshot(status = status(enabled = listOf(false, false)))).disabledIds)
        assertTrue(session(snapshot(status = null)).disabledIds.isEmpty())
    }
}
