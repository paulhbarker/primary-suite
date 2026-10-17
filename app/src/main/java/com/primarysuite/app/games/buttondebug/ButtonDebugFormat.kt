package com.primarysuite.app.games.buttondebug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToLong

/** Text and geometry helpers for the page, ported from `ButtonDebug.tsx`. */

const val DASH = "–"

/** Microseconds as the web shows them: µs under 1 ms, ms with two decimals under 100 ms, then whole ms. */
fun fmtUs(us: Double?): String {
    if (us == null || us < 0) return DASH
    return when {
        us < 1000 -> "${us.roundToLong()}µs"
        us < 100_000 -> String.format(Locale.US, "%.2fms", us / 1000)
        else -> "${(us / 1000).roundToLong()}ms"
    }
}

fun fmtUs(us: Long?): String = fmtUs(us?.toDouble())

/** The web passes `x || null`, so a zero counter shows a dash. */
fun fmtUsOrDash(us: Long): String = fmtUs(us.takeIf { it != 0L })

fun fmtMs(ms: Double?, digits: Int = 1): String = if (ms == null) DASH else String.format(Locale.US, "%.${digits}fms", ms)

/** The average, or null when there is nothing to average (`avg` on the web). */
fun avgUs(sum: Long, n: Int): Double? = if (n != 0) sum.toDouble() / n else null

/** A number as JavaScript prints it: 20.0 as "20", 7.5 as "7.5". */
fun fmtNumber(v: Double): String = if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

/** Log time: ms since the first edge seen, three decimals. */
fun logTime(ev: EdgeEvent, firstTsUs: Long?): String =
    if (firstTsUs == null) "" else String.format(Locale.US, "%.3f", (ev.tsUs - firstTsUs) / 1000.0)

/** "edge ↓" for a claimed press, "settle: LOW" for a line read. */
fun logEvent(ev: EdgeEvent): String =
    if (ev.kind == EdgeKind.EDGE) "edge ${if (ev.pressed) "↓" else "↑"}" else "${ev.kind}: ${if (ev.pressed) "LOW" else "HIGH"}"

/** The verdict, with the press sequence number when there is one. */
fun logVerdict(ev: EdgeEvent): String = ev.verdict + if (ev.seq != 0L) " #${ev.seq}" else ""

fun logGap(ev: EdgeEvent): String = if (ev.gapUs < 0) DASH else fmtUs(ev.gapUs)

/** Edge: kernel timestamp to server. Settle: time since the burst's first edge. */
fun logLagOrBurst(ev: EdgeEvent): String = if (ev.kind == EdgeKind.EDGE) fmtUs(ev.lagUs) else fmtUs(ev.burstUs)

/** The Wi-Fi press time, as a 24 hour clock. */
fun clockTime(atMs: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("HH:mm:ss", locale).apply { timeZone = zone }.format(Date(atMs))

/** The wave's time axis: at least 1 ms, or the burst's span plus a margin. */
fun waveWindowUs(burst: Burst): Double = max(1000.0, burst.spanUs * 1.15 + 100)

/** Where an edge at [tUs] sits along a wave of [width] units with [lead] units of margin each side. */
fun waveX(tUs: Long, windowUs: Double, width: Double = WAVE_W, lead: Double = WAVE_LEAD): Double =
    lead + (tUs / windowUs) * (width - lead * 2)

const val WAVE_W = 140.0
const val WAVE_H = 34.0
const val WAVE_LEAD = 6.0
