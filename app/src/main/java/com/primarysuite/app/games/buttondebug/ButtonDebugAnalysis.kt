package com.primarysuite.app.games.buttondebug

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
 * A port of `frontend/src/games/button-debug/analysis.ts`: turns the event
 * stream into per-button health stats. The functions follow the TypeScript one
 * for one, so the phone gives the same verdicts as the web page.
 *
 * The server never trusts edge directions (the kernel gets them wrong during
 * bounce). Each burst of edges ends with a "settle" event that reads the line,
 * and that read is the truth this analysis works from.
 */

/** Shorter than any real press: the contact barely closed. */
const val SHORT_PRESS_MS = 15

/** A press that waited this long for the bounce to settle is worth a look. */
const val SLOW_LATE_PRESS_US = 10_000L

enum class BurstKind { PRESS, RELEASE, NOISE }

/** One edge in a burst. [tUs] is relative to the burst's start. */
data class BurstEdge(val tUs: Long, val claimed: Boolean)

data class Burst(val kind: BurstKind, val startUs: Long, val edges: List<BurstEdge>) {
    /** Time from the first edge to the last. */
    val spanUs: Long get() = edges.lastOrNull()?.tUs ?: 0
}

fun burstSpanUs(b: Burst): Long = b.spanUs

/** What the page tracks for one button of the box. Field for field, `Stats` in `analysis.ts`. */
data class Stats(
    /** Sent to the games. */
    val presses: Int = 0,
    /** Reached the games through the hub. */
    val delivered: Int = 0,
    /** The first read hit a bounce; sent at settle. */
    val latePresses: Int = 0,
    val lateMaxUs: Long = 0,
    /** Backstop: no edges at all. */
    val lostPresses: Int = 0,
    val lostReleases: Int = 0,
    /** Noise on an idle line. */
    val blips: Int = 0,
    val shortPresses: Int = 0,
    /** A game had the button disabled. */
    val dropped: Int = 0,
    val kernelDropped: Long = 0,
    val edges: Int = 0,
    val lastHoldMs: Double? = null,
    val minHoldMs: Double? = null,
    /** The last completed bursts. */
    val pressBurst: Burst? = null,
    val releaseBurst: Burst? = null,
    val noiseBurst: Burst? = null,
    val maxPressBounceUs: Long = 0,
    val maxReleaseBounceUs: Long = 0,
    val lagMaxUs: Long = 0,
    val lagSumUs: Long = 0,
    val lagN: Int = 0,
    val hubMaxUs: Long = 0,
    val hubSumUs: Long = 0,
    val hubN: Int = 0,
    /** Debounced, as the events tell it. */
    val down: Boolean = false,
    /** First edge of the current press. */
    val pressStartUs: Long? = null,
    /** The burst in progress: edges since the last settle. */
    val cur: Burst? = null,
    /** Bumps on every press sent. */
    val pressKey: Int = 0,
    /** [pressKey] changed from a live event, not a replay. */
    val live: Boolean = false,
)

fun emptyStats() = Stats()

private val PRESS_VERDICTS = setOf(Verdict.PRESS, Verdict.LATE_PRESS, Verdict.LOST_PRESS)

/** Applies one event to a button's stats. Returns a new object. */
fun applyEvent(prev: Stats, ev: EdgeEvent, live: Boolean): Stats {
    var s = prev

    if (ev.kind == EdgeKind.EDGE) {
        s = s.copy(edges = s.edges + 1)
        if (ev.lagUs >= 0) {
            s = s.copy(lagMaxUs = max(s.lagMaxUs, ev.lagUs), lagSumUs = s.lagSumUs + ev.lagUs, lagN = s.lagN + 1)
        }
        if (ev.kernelGap != 0L) s = s.copy(kernelDropped = s.kernelDropped + ev.kernelGap)
        val cur = s.cur ?: Burst(BurstKind.NOISE, ev.tsUs, emptyList())
        s = s.copy(cur = cur.copy(edges = cur.edges + BurstEdge(ev.tsUs - cur.startUs, ev.pressed)))
    }

    if (ev.verdict in PRESS_VERDICTS || ev.verdict == Verdict.DISABLED) {
        if (!s.down) s = s.copy(pressStartUs = s.cur?.startUs ?: ev.tsUs)
        s = s.copy(down = true)
    }
    if (ev.verdict in PRESS_VERDICTS) {
        s = s.copy(presses = s.presses + 1, pressKey = s.pressKey + 1, live = live)
    }

    when (ev.verdict) {
        Verdict.LATE_PRESS -> s = s.copy(latePresses = s.latePresses + 1, lateMaxUs = max(s.lateMaxUs, ev.burstUs ?: 0))
        Verdict.LOST_PRESS -> s = s.copy(lostPresses = s.lostPresses + 1)
        Verdict.LOST_RELEASE -> s = s.copy(lostReleases = s.lostReleases + 1, down = false)
        Verdict.DISABLED -> s = s.copy(dropped = s.dropped + 1)
        Verdict.BLIP -> s = s.copy(blips = s.blips + 1)
    }

    // A settle closes the burst; its read says what the burst was.
    val cur = s.cur
    if (ev.kind == EdgeKind.SETTLE && cur != null) {
        val kind = when (ev.verdict) {
            Verdict.RELEASE -> BurstKind.RELEASE
            Verdict.BLIP -> BurstKind.NOISE
            else -> BurstKind.PRESS
        }
        val done = cur.copy(kind = kind)
        val span = done.spanUs
        when (kind) {
            BurstKind.PRESS -> s = s.copy(pressBurst = done, maxPressBounceUs = max(s.maxPressBounceUs, span))
            BurstKind.RELEASE -> s = s.copy(releaseBurst = done, maxReleaseBounceUs = max(s.maxReleaseBounceUs, span))
            BurstKind.NOISE -> s = s.copy(noiseBurst = done)
        }
        val pressStart = s.pressStartUs
        if (kind == BurstKind.RELEASE && pressStart != null) {
            val hold = (done.startUs - pressStart) / 1000.0
            s = s.copy(
                lastHoldMs = hold,
                minHoldMs = s.minHoldMs?.let { min(it, hold) } ?: hold,
                shortPresses = if (hold < SHORT_PRESS_MS) s.shortPresses + 1 else s.shortPresses,
            )
        }
        s = s.copy(cur = null)
    }
    if (ev.verdict == Verdict.RELEASE) s = s.copy(down = false)
    if (ev.kind == EdgeKind.POLL) s = s.copy(cur = null)
    return s
}

/** A box press that reached the games. */
fun applyDelivered(prev: Stats, p: DebugPress): Stats {
    var s = prev.copy(delivered = prev.delivered + 1)
    if (p.hubUs >= 0) {
        s = s.copy(hubMaxUs = max(s.hubMaxUs, p.hubUs), hubSumUs = s.hubSumUs + p.hubUs, hubN = s.hubN + 1)
    }
    return s
}

private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

/** Problems worth shouting about, worst first. The texts are the web's. */
fun problems(s: Stats, stuck: Boolean): List<String> {
    val out = mutableListOf<String>()
    if (stuck) {
        out += "Stuck: the debounced state and the line disagree. The backstop should fix this within 40ms; if it stays, something is wrong."
    }
    if (s.presses > s.delivered + 1) out += "${plural(s.presses - s.delivered, "press", "presses")} sent but never reached the games."
    if (s.lostPresses + s.lostReleases > 0) {
        out += "The kernel lost every edge of ${plural(s.lostPresses, "press", "presses")} and ${plural(s.lostReleases, "release")}. " +
            "Caught by the backstop, up to 40ms late. Check the wiring if it keeps happening."
    }
    if (s.blips > 0) {
        out += "${plural(s.blips, "burst")} of noise on the idle line (edges, but it never stayed closed). Loose wire or interference."
    }
    if (s.shortPresses > 0) {
        out += "${plural(s.shortPresses, "press", "presses")} shorter than ${SHORT_PRESS_MS}ms: the contact barely closed, or noise got counted."
    }
    if (s.lateMaxUs > SLOW_LATE_PRESS_US) {
        out += "A press waited ${String.format(Locale.US, "%.1f", s.lateMaxUs / 1000.0)}ms for its bounce to settle."
    }
    if (s.dropped > 0) out += "${plural(s.dropped, "press", "presses")} dropped because a game had the button disabled."
    return out
}

/** What the page tracks for one Wi-Fi (ESP) button. */
data class WifiStats(
    val id: Int,
    val presses: Int = 0,
    val lastSeq: Long? = null,
    /** Sequence numbers that never arrived. */
    val lostSeq: Long = 0,
    val rssi: Int? = null,
    val remote: String? = null,
    val freeHeap: Long? = null,
    val pressKey: Int = 0,
)

fun applyWifi(prev: WifiStats?, p: DebugPress): WifiStats {
    var s = prev ?: WifiStats(id = p.id)
    s = s.copy(presses = s.presses + 1, pressKey = s.pressKey + 1)
    if (p.seq != 0L) {
        // The firmware restarts at 1 after a reboot; don't count that as loss.
        val last = s.lastSeq
        if (last != null && p.seq > last + 1) s = s.copy(lostSeq = s.lostSeq + (p.seq - last - 1))
        s = s.copy(lastSeq = p.seq)
    }
    if (p.rssi != 0) s = s.copy(rssi = p.rssi)
    if (p.remote != null) s = s.copy(remote = p.remote)
    if (p.freeHeap != 0L) s = s.copy(freeHeap = p.freeHeap)
    return s
}
