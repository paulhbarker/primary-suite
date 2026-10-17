package com.primarysuite.app.games.buttondebug

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonDebugProtocolTest {

    private fun fixture(): JSONObject =
        JSONObject(javaClass.getResource("/buttondebug/snapshot.json")!!.readText())

    @Test
    fun `parses an edge as the server sends it`() {
        val ev = EdgeEvent.parse(
            JSONObject(
                """{"button":1,"id":3,"pin":13,"kind":"settle","pressed":true,"tsUs":123456789,"gapUs":20000,
                   "lagUs":-1,"kernelGap":2,"burstUs":700,"verdict":"late-press","seq":9}""",
            ),
        )!!
        assertEquals(EdgeEvent(1, 3, 13, "settle", true, 123456789, 20000, -1, 2, 700, "late-press", 9), ev)
    }

    @Test
    fun `an edge with only the required field gets the web's falsy defaults`() {
        val ev = EdgeEvent.parse(JSONObject("""{"button":0}"""))!!
        assertEquals(0L, ev.kernelGap)
        assertNull(ev.burstUs)
        assertEquals(0L, ev.seq)
        assertEquals(-1L, ev.gapUs)
        assertEquals(-1L, ev.lagUs)
        assertEquals("", ev.verdict)
        assertFalse(ev.pressed)
    }

    @Test
    fun `an edge without a button is rejected`() {
        assertNull(EdgeEvent.parse(JSONObject("{}")))
        assertNull(EdgeEvent.parse(JSONObject("""{"id":4,"kind":"edge"}""")))
    }

    @Test
    fun `a null burstUs is no burst`() {
        assertNull(EdgeEvent.parse(JSONObject("""{"button":0,"burstUs":null}"""))!!.burstUs)
    }

    @Test
    fun `parses a box press and a wifi press`() {
        val box = DebugPress.parse(JSONObject("""{"id":4,"seq":7,"source":"box","hubUs":450,"atMs":1700000000000}"""))
        assertEquals(DebugPress(4, 7, "box", null, 450, 0, 0, 0, 1700000000000), box)
        assertTrue(box.fromBox)

        val wifi = DebugPress.parse(
            JSONObject("""{"id":9,"seq":3,"source":"wifi","remote":"10.0.0.5:99","hubUs":-1,"rssi":-61,"sentMs":1234,"freeHeap":150000,"atMs":5}"""),
        )
        assertEquals(DebugPress(9, 3, "wifi", "10.0.0.5:99", -1, -61, 1234, 150000, 5), wifi)
        assertFalse(wifi.fromBox)
    }

    @Test
    fun `a press with missing fields has unknown timing`() {
        val p = DebugPress.parse(JSONObject("""{"id":2,"source":"box"}"""))
        assertEquals(-1L, p.hubUs)
        assertEquals(0L, p.seq)
        assertNull(p.remote)
    }

    @Test
    fun `parses the status and reads missing entries safely`() {
        val s = DebugStatus.parse(
            statusJson(
                down = listOf(true, false), levels = listOf(true, false), enabled = listOf(false, true),
                leds = listOf(true, false), ledMode = "follow", quietMs = 7.5, stuck = listOf(false, true),
                activeGames = listOf("a", "b"), droppedEdges = 12, levelErr = "gpio read failed",
            ),
        )
        assertTrue(s.isDown(0))
        assertTrue(s.isLow(0))
        assertFalse(s.isEnabled(0))
        assertTrue(s.isLed(0))
        assertTrue(s.isStuck(1))
        assertEquals("follow", s.ledMode)
        assertEquals(7.5, s.quietMs, 0.0)
        assertEquals(listOf("a", "b"), s.activeGames)
        assertEquals(12L, s.droppedEdges)
        assertEquals("gpio read failed", s.levelErr)

        // Out of range or missing: not held, not stuck, still enabled.
        assertFalse(s.isDown(5))
        assertTrue(s.isEnabled(5))
        assertFalse(s.isStuck(5))
    }

    @Test
    fun `a status with null arrays, as the server sends without a box, parses`() {
        val s = DebugStatus.parse(JSONObject("""{"down":null,"levels":null,"enabled":null,"leds":null,"ledMode":"","quietMs":0,"stuck":null,"activeGames":null,"droppedEdges":0}"""))
        assertTrue(s.activeGames.isEmpty())
        assertTrue(s.down.isEmpty())
        assertNull(s.levelErr)
        assertTrue(s.isEnabled(0))
        assertEquals(DebugStatus.parse(JSONObject("{}")).quietMs, 0.0, 0.0)
    }

    @Test
    fun `parses the snapshot fixture`() {
        val snap = DebugSnapshot.parse(fixture())
        assertTrue(snap.box)
        assertEquals("gpiochip0", snap.chip)
        assertNull(snap.boxError)
        assertEquals(listOf(DebugButton(0, 4, 5, 6), DebugButton(1, 3, 13, 19)), snap.buttons)
        assertEquals(5, snap.events.size)
        assertEquals(3, snap.presses.size)
        assertEquals(listOf(true, false), snap.status!!.enabled)
        assertEquals(20.0, snap.status!!.quietMs, 0.0)
        assertEquals(listOf("lucky-tap"), snap.status!!.activeGames)
        assertEquals(2L, snap.events[1].kernelGap)
        assertEquals(300L, snap.events[2].burstUs)
        assertEquals("wifi", snap.presses[2].source)
    }

    @Test
    fun `a snapshot with no box carries the reason and no buttons`() {
        val snap = DebugSnapshot.parse(
            JSONObject("""{"box":false,"boxError":"BUTTON_BOX is not set","buttons":[],"events":[],"presses":[],"status":{"activeGames":null,"stuck":null,"droppedEdges":0}}"""),
        )
        assertFalse(snap.box)
        assertEquals("BUTTON_BOX is not set", snap.boxError)
        assertTrue(snap.buttons.isEmpty())
        assertTrue(snap.status!!.activeGames.isEmpty())
    }

    @Test
    fun `an empty snapshot parses and bad list entries are skipped`() {
        val snap = DebugSnapshot.parse(JSONObject("""{"events":[1,{"kind":"edge"},{"button":0,"id":1}],"presses":["x"]}"""))
        assertFalse(snap.box)
        assertNull(snap.status)
        assertEquals(1, snap.events.size)
        assertTrue(snap.presses.isEmpty())
    }

    @Test
    fun `commands carry the web's types and payloads`() {
        val ping = ButtonDebugCommands.ping(1234.5)
        assertEquals("button-debug-ping", ping.type)
        assertEquals(1234.5, ping.payload.getDouble("t"), 0.0)

        LedMode.entries.forEach {
            val m = ButtonDebugCommands.ledMode(it)
            assertEquals("button-debug-led-mode", m.type)
            assertEquals(it.mode, m.payload.getString("mode"))
        }
        assertEquals(listOf("game", "follow", "on", "off"), LedMode.entries.map { it.mode })

        assertEquals("button-debug-chase", ButtonDebugCommands.chase().type)
        assertEquals(0, ButtonDebugCommands.chase().payload.length())
        assertEquals("button-debug-enable-all", ButtonDebugCommands.enableAll().type)
        assertEquals("button-debug-reset", ButtonDebugCommands.reset().type)

        val d = ButtonDebugCommands.debounce(12.5)
        assertEquals("button-debug-debounce", d.type)
        assertEquals(12.5, d.payload.getDouble("ms"), 0.0)
    }

    @Test
    fun `the debounce field accepts a number from 0 to 500 only`() {
        assertEquals(20.0, parseDebounce("20")!!, 0.0)
        assertEquals(7.5, parseDebounce(" 7.5 ")!!, 0.0)
        assertEquals(0.0, parseDebounce("0")!!, 0.0)
        assertEquals(500.0, parseDebounce("500")!!, 0.0)
        assertNull(parseDebounce(""))
        assertNull(parseDebounce("  "))
        assertNull(parseDebounce("abc"))
        assertNull(parseDebounce("-1"))
        assertNull(parseDebounce("500.1"))
        assertNull(parseDebounce("NaN"))
        assertNull(parseDebounce("Infinity"))
    }

    @Test
    fun `presets are the web's`() {
        assertEquals(listOf(5, 10, 20, 30, 50), ButtonDebugProtocol.DEBOUNCE_PRESETS)
    }
}
