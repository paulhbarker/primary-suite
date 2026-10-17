package com.primarysuite.app.games.buttondebug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors `analysis.ts`: same events in, same stats and verdict texts out. */
class ButtonDebugAnalysisTest {

    private fun run(events: List<EdgeEvent>, live: Boolean = true) =
        events.fold(emptyStats()) { s, ev -> applyEvent(s, ev, live) }

    // ---- edges

    @Test
    fun `an edge counts, tracks lag and starts a burst`() {
        val s = applyEvent(emptyStats(), edge(tsUs = 1000, pressed = true, lagUs = 150), live = true)
        assertEquals(1, s.edges)
        assertEquals(150L, s.lagMaxUs)
        assertEquals(150L, s.lagSumUs)
        assertEquals(1, s.lagN)
        assertEquals(Burst(BurstKind.NOISE, 1000, listOf(BurstEdge(0, true))), s.cur)
    }

    @Test
    fun `unknown lag is left out of the lag stats`() {
        val s = applyEvent(emptyStats(), edge(lagUs = -1), live = true)
        assertEquals(1, s.edges)
        assertEquals(0, s.lagN)
        assertEquals(0L, s.lagSumUs)
    }

    @Test
    fun `lag keeps the maximum and the sum`() {
        val s = run(listOf(edge(tsUs = 0, lagUs = 100), edge(tsUs = 10, lagUs = 300), edge(tsUs = 20, lagUs = 200)))
        assertEquals(300L, s.lagMaxUs)
        assertEquals(600L, s.lagSumUs)
        assertEquals(3, s.lagN)
    }

    @Test
    fun `kernel gaps add up`() {
        val s = run(listOf(edge(kernelGap = 2), edge(tsUs = 5, kernelGap = 3), edge(tsUs = 9)))
        assertEquals(5L, s.kernelDropped)
    }

    @Test
    fun `edges in a burst are timed from the first and keep their claimed direction`() {
        val s = run(listOf(edge(tsUs = 5000, pressed = true), edge(tsUs = 5200, pressed = false), edge(tsUs = 5900, pressed = true)))
        assertEquals(5000L, s.cur!!.startUs)
        assertEquals(listOf(BurstEdge(0, true), BurstEdge(200, false), BurstEdge(900, true)), s.cur!!.edges)
        assertEquals(900L, s.cur!!.spanUs)
        assertEquals(900L, burstSpanUs(s.cur!!))
    }

    @Test
    fun `an empty burst spans nothing`() {
        assertEquals(0L, Burst(BurstKind.NOISE, 0, emptyList()).spanUs)
    }

    // ---- presses

    @Test
    fun `a press verdict counts a press, holds the button and remembers where it began`() {
        val s = run(listOf(edge(tsUs = 1000, verdict = Verdict.PRESS, seq = 1)))
        assertEquals(1, s.presses)
        assertEquals(1, s.pressKey)
        assertTrue(s.live)
        assertTrue(s.down)
        assertEquals(1000L, s.pressStartUs)
    }

    @Test
    fun `a replayed press is not live`() {
        val s = run(listOf(edge(tsUs = 1000, verdict = Verdict.PRESS, seq = 1)), live = false)
        assertEquals(1, s.pressKey)
        assertFalse(s.live)
    }

    @Test
    fun `a press while already held keeps the first start`() {
        val s = run(
            listOf(
                edge(tsUs = 1000, verdict = Verdict.PRESS, seq = 1),
                edge(tsUs = 9000, verdict = Verdict.PRESS, seq = 2),
            ),
        )
        assertEquals(2, s.presses)
        assertEquals(1000L, s.pressStartUs)
    }

    @Test
    fun `a late press is counted with its wait`() {
        val s = run(
            listOf(
                edge(tsUs = 1000, verdict = Verdict.PENDING),
                edge(tsUs = 1200, pressed = false, verdict = Verdict.BOUNCE),
                edge(kind = EdgeKind.SETTLE, tsUs = 21200, lagUs = -1, burstUs = 12_500, verdict = Verdict.LATE_PRESS, seq = 4),
            ),
        )
        assertEquals(1, s.presses)
        assertEquals(1, s.latePresses)
        assertEquals(12_500L, s.lateMaxUs)
        assertTrue(s.down)
        // The press began at the burst's first edge.
        assertEquals(1000L, s.pressStartUs)
        // The settle closed the burst; its read said low, so it was a press burst.
        assertEquals(BurstKind.PRESS, s.pressBurst!!.kind)
        assertEquals(200L, s.maxPressBounceUs)
        assertNull(s.cur)
    }

    @Test
    fun `a late press without a burst time waits zero`() {
        val s = run(listOf(edge(kind = EdgeKind.SETTLE, verdict = Verdict.LATE_PRESS, burstUs = null)))
        assertEquals(1, s.latePresses)
        assertEquals(0L, s.lateMaxUs)
    }

    @Test
    fun `the longest late wait wins`() {
        val s = run(
            listOf(
                edge(kind = EdgeKind.SETTLE, verdict = Verdict.LATE_PRESS, burstUs = 5000),
                edge(kind = EdgeKind.SETTLE, verdict = Verdict.LATE_PRESS, burstUs = 3000),
            ),
        )
        assertEquals(5000L, s.lateMaxUs)
    }

    @Test
    fun `a lost press and release are caught by the poll`() {
        var s = applyEvent(emptyStats(), edge(kind = EdgeKind.POLL, tsUs = 7000, lagUs = -1, verdict = Verdict.LOST_PRESS, seq = 3), true)
        assertEquals(1, s.presses)
        assertEquals(1, s.lostPresses)
        assertTrue(s.down)
        // No edges, so the press starts at the poll's own time.
        assertEquals(7000L, s.pressStartUs)
        assertEquals(0, s.edges)

        s = applyEvent(s, edge(kind = EdgeKind.POLL, tsUs = 90_000, lagUs = -1, pressed = false, verdict = Verdict.LOST_RELEASE), true)
        assertEquals(1, s.lostReleases)
        assertFalse(s.down)
    }

    @Test
    fun `a poll drops the burst in progress`() {
        val s = run(listOf(edge(tsUs = 100), edge(kind = EdgeKind.POLL, tsUs = 200, lagUs = -1, verdict = Verdict.LOST_RELEASE, pressed = false)))
        assertNull(s.cur)
    }

    @Test
    fun `a press on a disabled button is dropped, not counted`() {
        val s = run(listOf(edge(tsUs = 500, verdict = Verdict.DISABLED)))
        assertEquals(1, s.dropped)
        assertEquals(0, s.presses)
        assertEquals(0, s.pressKey)
        assertTrue(s.down)
        assertEquals(500L, s.pressStartUs)
    }

    // ---- settle and release

    @Test
    fun `a settle on a held button closes a press burst`() {
        val s = run(Tap.down(10_000))
        assertEquals(1, s.presses)
        assertNull(s.cur)
        val burst = s.pressBurst!!
        assertEquals(BurstKind.PRESS, burst.kind)
        assertEquals(10_000L, burst.startUs)
        assertEquals(listOf(BurstEdge(0, true), BurstEdge(200, false), BurstEdge(700, true)), burst.edges)
        assertEquals(700L, s.maxPressBounceUs)
        assertTrue(s.down)
        assertNull(s.releaseBurst)
    }

    @Test
    fun `a release settle closes a release burst and measures the hold`() {
        val s = run(Tap.down(1000) + Tap.up(501_000))
        assertFalse(s.down)
        assertEquals(BurstKind.RELEASE, s.releaseBurst!!.kind)
        assertEquals(501_000L, s.releaseBurst!!.startUs)
        assertEquals(300L, s.maxReleaseBounceUs)
        assertEquals(500.0, s.lastHoldMs!!, 0.0)
        assertEquals(500.0, s.minHoldMs!!, 0.0)
        assertEquals(0, s.shortPresses)
    }

    @Test
    fun `hold tracks the last and the shortest`() {
        val s = run(Tap.down(0) + Tap.up(300_000) + Tap.down(1_000_000, seq = 2) + Tap.up(1_100_000) + Tap.down(2_000_000, seq = 3) + Tap.up(2_700_000))
        assertEquals(700.0, s.lastHoldMs!!, 0.0)
        assertEquals(100.0, s.minHoldMs!!, 0.0)
        assertEquals(3, s.presses)
    }

    @Test
    fun `a press shorter than 15ms is counted as short`() {
        val s = run(Tap.down(0) + Tap.up(14_999))
        assertEquals(1, s.shortPresses)
        assertEquals(14.999, s.lastHoldMs!!, 1e-9)
    }

    @Test
    fun `a press of exactly 15ms is not short`() {
        val s = run(Tap.down(0) + Tap.up(15_000))
        assertEquals(0, s.shortPresses)
    }

    @Test
    fun `a release with no press behind it has no hold`() {
        val s = run(Tap.up(5000))
        assertNull(s.lastHoldMs)
        assertNull(s.minHoldMs)
        assertEquals(0, s.shortPresses)
        assertEquals(BurstKind.RELEASE, s.releaseBurst!!.kind)
    }

    @Test
    fun `noise on an idle button is a blip with a noise burst`() {
        val s = run(
            listOf(
                edge(tsUs = 1000, pressed = true, verdict = Verdict.PENDING),
                edge(tsUs = 1100, pressed = false, verdict = Verdict.BOUNCE),
                edge(kind = EdgeKind.SETTLE, pressed = false, tsUs = 21_100, lagUs = -1, burstUs = 100, verdict = Verdict.BLIP),
            ),
        )
        assertEquals(1, s.blips)
        assertEquals(BurstKind.NOISE, s.noiseBurst!!.kind)
        assertEquals(2, s.noiseBurst!!.edges.size)
        assertNull(s.pressBurst)
        assertEquals(0, s.presses)
        assertFalse(s.down)
        assertNull(s.cur)
    }

    @Test
    fun `a settle with no edges before it changes nothing but its verdict`() {
        val s = run(listOf(edge(kind = EdgeKind.SETTLE, verdict = Verdict.HOLDING, lagUs = -1)))
        assertNull(s.pressBurst)
        assertEquals(0, s.edges)
    }

    @Test
    fun `an unknown verdict on a settle is read as a press burst`() {
        val s = run(listOf(edge(tsUs = 0), edge(kind = EdgeKind.SETTLE, tsUs = 100, verdict = "from-the-future")))
        assertEquals(BurstKind.PRESS, s.pressBurst!!.kind)
    }

    @Test
    fun `the largest bounces are kept`() {
        val s = run(
            listOf(edge(tsUs = 0), edge(tsUs = 900), edge(kind = EdgeKind.SETTLE, tsUs = 20_000, verdict = Verdict.HOLDING)) +
                listOf(edge(tsUs = 50_000), edge(tsUs = 50_300), edge(kind = EdgeKind.SETTLE, tsUs = 70_000, verdict = Verdict.HOLDING)),
        )
        assertEquals(900L, s.maxPressBounceUs)
        assertEquals(300L, s.pressBurst!!.spanUs)
    }

    @Test
    fun `a replayed tap leaves the same stats as a live one apart from the live flag`() {
        val events = Tap.down(0) + Tap.up(200_000)
        val live = run(events, live = true)
        val replay = run(events, live = false)
        assertEquals(live.copy(live = false), replay)
    }

    @Test
    fun `applyEvent does not change the stats it was given`() {
        val before = run(Tap.down(0))
        val copy = before.copy()
        applyEvent(before, edge(tsUs = 99_999, verdict = Verdict.PRESS, seq = 2), true)
        assertEquals(copy, before)
    }

    // ---- delivered

    @Test
    fun `a delivered press counts and tracks hub time`() {
        var s = applyDelivered(emptyStats(), press(hubUs = 400))
        s = applyDelivered(s, press(hubUs = 900))
        assertEquals(2, s.delivered)
        assertEquals(900L, s.hubMaxUs)
        assertEquals(1300L, s.hubSumUs)
        assertEquals(2, s.hubN)
    }

    @Test
    fun `a delivered press with unknown hub time counts but adds no timing`() {
        val s = applyDelivered(emptyStats(), press(hubUs = -1))
        assertEquals(1, s.delivered)
        assertEquals(0, s.hubN)
        assertEquals(0L, s.hubMaxUs)
    }

    @Test
    fun `a zero hub time is a valid timing`() {
        val s = applyDelivered(emptyStats(), press(hubUs = 0))
        assertEquals(1, s.hubN)
    }

    // ---- problems

    @Test
    fun `a healthy button has no problems`() {
        assertTrue(problems(emptyStats(), stuck = false).isEmpty())
        assertTrue(problems(Stats(presses = 20, delivered = 20), stuck = false).isEmpty())
    }

    @Test
    fun `stuck is worst and comes first`() {
        val p = problems(Stats(presses = 10, delivered = 0, blips = 1), stuck = true)
        assertEquals(
            "Stuck: the debounced state and the line disagree. The backstop should fix this within 40ms; if it stays, something is wrong.",
            p[0],
        )
        assertEquals(3, p.size)
    }

    @Test
    fun `one press in flight is not a problem, two are`() {
        assertTrue(problems(Stats(presses = 5, delivered = 4), false).isEmpty())
        assertEquals(
            listOf("2 presses sent but never reached the games."),
            problems(Stats(presses = 5, delivered = 3), false),
        )
    }

    @Test
    fun `lost edges text counts both kinds`() {
        assertEquals(
            listOf(
                "The kernel lost every edge of 1 press and 0 releases. " +
                    "Caught by the backstop, up to 40ms late. Check the wiring if it keeps happening.",
            ),
            problems(Stats(lostPresses = 1), false),
        )
        assertEquals(
            listOf(
                "The kernel lost every edge of 0 presses and 1 release. " +
                    "Caught by the backstop, up to 40ms late. Check the wiring if it keeps happening.",
            ),
            problems(Stats(lostReleases = 1), false),
        )
        assertEquals(
            "The kernel lost every edge of 2 presses and 3 releases. Caught by the backstop, up to 40ms late. Check the wiring if it keeps happening.",
            problems(Stats(lostPresses = 2, lostReleases = 3), false).single(),
        )
    }

    @Test
    fun `noise text`() {
        assertEquals(
            listOf("1 burst of noise on the idle line (edges, but it never stayed closed). Loose wire or interference."),
            problems(Stats(blips = 1), false),
        )
        assertEquals(
            listOf("3 bursts of noise on the idle line (edges, but it never stayed closed). Loose wire or interference."),
            problems(Stats(blips = 3), false),
        )
    }

    @Test
    fun `short press text`() {
        assertEquals(
            listOf("1 press shorter than 15ms: the contact barely closed, or noise got counted."),
            problems(Stats(shortPresses = 1), false),
        )
        assertEquals(
            listOf("2 presses shorter than 15ms: the contact barely closed, or noise got counted."),
            problems(Stats(shortPresses = 2), false),
        )
    }

    @Test
    fun `a late wait is only worth a look above 10ms`() {
        assertTrue(problems(Stats(lateMaxUs = 10_000), false).isEmpty())
        assertEquals(listOf("A press waited 10.0ms for its bounce to settle."), problems(Stats(lateMaxUs = 10_001), false))
        assertEquals(listOf("A press waited 12.5ms for its bounce to settle."), problems(Stats(lateMaxUs = 12_500), false))
    }

    @Test
    fun `dropped text`() {
        assertEquals(
            listOf("1 press dropped because a game had the button disabled."),
            problems(Stats(dropped = 1), false),
        )
        assertEquals(
            listOf("4 presses dropped because a game had the button disabled."),
            problems(Stats(dropped = 4), false),
        )
    }

    @Test
    fun `problems come in the web's order`() {
        val all = problems(
            Stats(presses = 9, delivered = 1, lostPresses = 1, blips = 1, shortPresses = 1, lateMaxUs = 20_000, dropped = 1),
            stuck = true,
        )
        assertEquals(7, all.size)
        assertTrue(all[0].startsWith("Stuck"))
        assertTrue(all[1].contains("never reached the games"))
        assertTrue(all[2].startsWith("The kernel lost"))
        assertTrue(all[3].contains("of noise"))
        assertTrue(all[4].contains("shorter than"))
        assertTrue(all[5].startsWith("A press waited"))
        assertTrue(all[6].contains("disabled"))
    }

    @Test
    fun `a real tap that was delivered is healthy and a short tap is flagged`() {
        val ok = run(Tap.down(0) + Tap.up(300_000))
        assertTrue(problems(applyDelivered(ok, press()), false).isEmpty())
        val short = run(Tap.down(0) + Tap.up(8_000))
        assertEquals(1, problems(applyDelivered(short, press()), false).size)
    }

    // ---- wifi

    @Test
    fun `wifi presses count and read sequence gaps`() {
        var w = applyWifi(null, press(id = 9, seq = 1, source = "wifi", rssi = -60, remote = "10.0.0.5:1", freeHeap = 90000))
        assertEquals(WifiStats(9, 1, 1, 0, -60, "10.0.0.5:1", 90000, 1), w)
        w = applyWifi(w, press(id = 9, seq = 2, source = "wifi", rssi = -62))
        assertEquals(0L, w.lostSeq)
        w = applyWifi(w, press(id = 9, seq = 6, source = "wifi", rssi = -70))
        assertEquals(3L, w.lostSeq)
        assertEquals(6L, w.lastSeq)
        assertEquals(-70, w.rssi)
        assertEquals(3, w.presses)
        assertEquals(3, w.pressKey)
    }

    @Test
    fun `a restarting sequence is not counted as loss`() {
        var w = applyWifi(null, press(id = 9, seq = 50, source = "wifi"))
        w = applyWifi(w, press(id = 9, seq = 1, source = "wifi"))
        assertEquals(0L, w.lostSeq)
        assertEquals(1L, w.lastSeq)
        w = applyWifi(w, press(id = 9, seq = 2, source = "wifi"))
        assertEquals(0L, w.lostSeq)
    }

    @Test
    fun `a repeated sequence number is no loss`() {
        var w = applyWifi(null, press(id = 9, seq = 4, source = "wifi"))
        w = applyWifi(w, press(id = 9, seq = 4, source = "wifi"))
        assertEquals(0L, w.lostSeq)
    }

    @Test
    fun `a press without a sequence number leaves the sequence alone`() {
        var w = applyWifi(null, press(id = 9, seq = 3, source = "wifi"))
        w = applyWifi(w, press(id = 9, seq = 0, source = "wifi"))
        assertEquals(3L, w.lastSeq)
        assertEquals(2, w.presses)
    }

    @Test
    fun `missing rssi, remote and heap keep what was known`() {
        var w = applyWifi(null, press(id = 9, seq = 1, source = "wifi", rssi = -61, remote = "a", freeHeap = 5))
        w = applyWifi(w, press(id = 9, seq = 2, source = "wifi"))
        assertEquals(-61, w.rssi)
        assertEquals("a", w.remote)
        assertEquals(5L, w.freeHeap)
        assertNull(applyWifi(null, press(id = 9, source = "wifi")).rssi)
    }
}
