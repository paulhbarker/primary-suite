package com.primarysuite.app.games.buttondebug

import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.TimeZone

class ButtonDebugFormatTest {

    @Test
    fun `microseconds read as the web shows them`() {
        assertEquals("–", fmtUs(null as Double?))
        assertEquals("–", fmtUs(null as Long?))
        assertEquals("–", fmtUs(-1L))
        assertEquals("0µs", fmtUs(0L))
        assertEquals("999µs", fmtUs(999L))
        assertEquals("1.00ms", fmtUs(1000L))
        assertEquals("12.35ms", fmtUs(12_346L))
        assertEquals("99.99ms", fmtUs(99_994L))
        assertEquals("100ms", fmtUs(100_000L))
        assertEquals("1235ms", fmtUs(1_234_567L))
    }

    @Test
    fun `fractional microseconds round half up like Math round`() {
        assertEquals("3µs", fmtUs(2.5))
        assertEquals("2µs", fmtUs(2.4))
        assertEquals("151ms", fmtUs(150_500.0))
    }

    @Test
    fun `a zero counter shows a dash`() {
        assertEquals("–", fmtUsOrDash(0))
        assertEquals("250µs", fmtUsOrDash(250))
    }

    @Test
    fun `milliseconds and averages`() {
        assertEquals("–", fmtMs(null))
        assertEquals("500ms", fmtMs(500.0, 0))
        assertEquals("14.999ms", fmtMs(14.999, 3))
        assertEquals(null, avgUs(0, 0))
        assertEquals(150.0, avgUs(300, 2)!!, 0.0)
    }

    @Test
    fun `numbers print as JavaScript does`() {
        assertEquals("20", fmtNumber(20.0))
        assertEquals("7.5", fmtNumber(7.5))
        assertEquals("0", fmtNumber(0.0))
    }

    @Test
    fun `log text follows the web's table`() {
        val e = edge(tsUs = 1_250_500, pressed = true, gapUs = 300, lagUs = 120, verdict = Verdict.PRESS, seq = 12, kernelGap = 2)
        assertEquals("250.500", logTime(e, 1_000_000))
        assertEquals("", logTime(e, null))
        assertEquals("edge ↓", logEvent(e))
        assertEquals("edge ↑", logEvent(e.copy(pressed = false)))
        assertEquals("press #12", logVerdict(e))
        assertEquals("bounce", logVerdict(edge(verdict = Verdict.BOUNCE)))
        assertEquals("300µs", logGap(e))
        assertEquals("–", logGap(e.copy(gapUs = -1)))
        assertEquals("120µs", logLagOrBurst(e))

        val settle = edge(kind = EdgeKind.SETTLE, pressed = false, lagUs = -1, burstUs = 2500, verdict = Verdict.RELEASE)
        assertEquals("settle: HIGH", logEvent(settle))
        assertEquals("settle: LOW", logEvent(settle.copy(pressed = true)))
        assertEquals("2.50ms", logLagOrBurst(settle))
        assertEquals("–", logLagOrBurst(settle.copy(burstUs = null)))
        assertEquals("poll: LOW", logEvent(settle.copy(kind = EdgeKind.POLL, pressed = true)))
    }

    @Test
    fun `wifi time is a 24 hour clock in the given zone`() {
        assertEquals("22:13:20", clockTime(1_700_000_000_000, TimeZone.getTimeZone("UTC"), Locale.US))
        assertEquals("23:13:20", clockTime(1_700_000_000_000, TimeZone.getTimeZone("Europe/Berlin"), Locale.US))
    }

    @Test
    fun `wave geometry`() {
        val short = Burst(BurstKind.PRESS, 0, listOf(BurstEdge(0, true), BurstEdge(200, false)))
        assertEquals(1000.0, waveWindowUs(short), 0.0)
        val long = Burst(BurstKind.PRESS, 0, listOf(BurstEdge(0, true), BurstEdge(10_000, false)))
        assertEquals(10_000 * 1.15 + 100, waveWindowUs(long), 1e-9)
        // First edge sits at the lead; an edge at the full window sits at the right margin.
        assertEquals(6.0, waveX(0, 1000.0), 0.0)
        assertEquals(134.0, waveX(1000, 1000.0), 1e-9)
        assertEquals(70.0, waveX(500, 1000.0), 1e-9)
    }

    @Test
    fun `colours follow the web's palette, defaults and order`() {
        assertEquals(listOf("green", "yellow", "white", "blue", "red"), ButtonColor.entries.map { it.key })
        assertEquals(
            mapOf(1 to ButtonColor.GREEN, 2 to ButtonColor.YELLOW, 3 to ButtonColor.WHITE, 4 to ButtonColor.BLUE),
            ButtonColor.DEFAULTS,
        )
        assertEquals(0xFF16A34AL, ButtonColor.GREEN.bg)
        assertEquals(0xFF1D4ED8L, ButtonColor.BLUE.edge)
        assertEquals(ButtonColor.RED, ButtonColor.of("red"))
        assertEquals(null, ButtonColor.of("purple"))
        assertEquals(ButtonColor.WHITE, emptyMap<Int, ButtonColor>().colorFor(9))
    }

    @Test
    fun `tiles sort by box order, with unpicked buttons last and ties by id`() {
        val buttons = listOf(DebugButton(0, 1, 0, 0), DebugButton(1, 2, 0, 0), DebugButton(2, 3, 0, 0), DebugButton(3, 4, 0, 0))
        assertEquals(listOf(4, 3, 2, 1), buttons.sortedByBoxOrder(ButtonColor.DEFAULTS).map { it.id })

        // The user says id 1 is blue and id 4 is green.
        val swapped = ButtonColor.DEFAULTS + (1 to ButtonColor.BLUE) + (4 to ButtonColor.GREEN)
        assertEquals(listOf(1, 3, 2, 4), buttons.sortedByBoxOrder(swapped).map { it.id })

        // No pick: sorts as red, last; ties by id.
        val extra = buttons + DebugButton(4, 8, 0, 0) + DebugButton(5, 7, 0, 0)
        assertEquals(listOf(4, 3, 2, 1, 7, 8), extra.sortedByBoxOrder(ButtonColor.DEFAULTS).map { it.id })
    }
}

@RunWith(AndroidJUnit4::class)
class PrefsButtonColorStoreTest {

    private fun prefs(): SharedPreferences =
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("test-colors", 0).also { it.edit().clear().commit() }

    @Test
    fun `saves in the web's format and loads it back`() {
        val p = prefs()
        val store = PrefsButtonColorStore(p)
        assertTrue(store.load().isEmpty())

        store.save(mapOf(1 to ButtonColor.RED, 4 to ButtonColor.GREEN))
        assertEquals(mapOf(1 to ButtonColor.RED, 4 to ButtonColor.GREEN), PrefsButtonColorStore(p).load())

        val json = org.json.JSONObject(p.getString("button-debug-colors-v3", null)!!)
        assertEquals("red", json.getString("1"))
        assertEquals("green", json.getString("4"))
    }

    @Test
    fun `bad saved data is ignored entry by entry`() {
        val p = prefs()
        p.edit().putString(PrefsButtonColorStore.KEY, """{"1":"red","2":"purple","x":"blue","3":5}""").commit()
        assertEquals(mapOf(1 to ButtonColor.RED), PrefsButtonColorStore(p).load())
        p.edit().putString(PrefsButtonColorStore.KEY, "not json").commit()
        assertTrue(PrefsButtonColorStore(p).load().isEmpty())
    }
}
