package com.primarysuite.app.games.spinwheel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpinWheelProtocolTest {

    @Test
    fun `parses an armed wheel`() {
        val s = WheelState.parse(Fixtures.stateJson())
        assertEquals(WheelPhase.ARMED, s.phase)
        assertFalse(s.locked)
        assertEquals(Fixtures.values, s.values)
        assertEquals(List(10) { 1 }, s.weights)
        assertNull(s.forcedNext)
        assertNull(s.spinValue)
        assertNull(s.lastResult)
        assertEquals(emptyList<WheelResult>(), s.history)
        assertEquals(WheelSettings.DEFAULTS, s.settings)
        assertEquals(1_700_000_000_000, s.serverTime)
    }

    @Test
    fun `parses a landed wheel with a rigged result and history`() {
        val s = WheelState.parse(
            Fixtures.stateJson(
                phase = "landed", locked = true, forcedNext = 700, spinValue = 500,
                lastResult = Fixtures.result(2, 500, button = 3, rigged = true),
                history = listOf(Fixtures.result(2, 500, button = 3, rigged = true), Fixtures.result(1, 100)),
            ),
        )
        assertEquals(WheelPhase.LANDED, s.phase)
        assertTrue(s.locked)
        assertEquals(700, s.forcedNext)
        assertEquals(500, s.spinValue)
        assertEquals(WheelResult(2, 500, 1_700_000_000_000, 3, true), s.lastResult)
        assertEquals(listOf(2, 1), s.history.map { it.id })
        assertEquals(1, s.count(500))
        assertEquals(0, s.count(900))
    }

    @Test
    fun `an unknown phase reads as armed and missing settings as the defaults`() {
        val s = WheelState.parse(JSONObject("""{"phase":"warp"}"""))
        assertEquals(WheelPhase.ARMED, s.phase)
        assertEquals(WheelSettings.DEFAULTS, s.settings)
        assertEquals(emptyList<Int>(), s.values)
    }

    @Test
    fun `odds are smallest value first with their weights`() {
        val weights = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val s = WheelState.parse(
            JSONObject("""{"values":[1000,100,200,300,400,500,600,700,800,900],"weights":$weights}"""),
        )
        assertEquals(100 to 2, s.odds.first())
        assertEquals(1000 to 1, s.odds.last())
        assertEquals(55, s.totalWeight)
    }

    @Test
    fun `weightsWith changes one value and refuses bad weights`() {
        val s = Fixtures.state()
        assertEquals(listOf(1, 1, 5, 1, 1, 1, 1, 1, 1, 1), s.weightsWith(300, 5))
        assertNull(s.weightsWith(300, 11))
        assertNull(s.weightsWith(300, -1))
        assertNull(s.weightsWith(150, 2))
        val one = Fixtures.state(weights = listOf(0, 0, 0, 3, 0, 0, 0, 0, 0, 0))
        assertNull(one.weightsWith(400, 0))
    }

    @Test
    fun `commands match the web admin`() {
        assertEquals("hello", SpinWheelCommands.hello().type)
        assertEquals("admin", SpinWheelCommands.hello().payload.getString("role"))
        assertEquals("spin", SpinWheelCommands.spin().type)
        assertEquals(0, SpinWheelCommands.spin().payload.length())
        assertTrue(SpinWheelCommands.setLocked(true).payload.getBoolean("locked"))
        assertEquals(700, SpinWheelCommands.forceNext(700).payload.getInt("value"))
        val clear = SpinWheelCommands.forceNext(null).payload
        assertTrue(clear.has("value") && clear.isNull("value"))
        assertEquals("[1,2,3]", SpinWheelCommands.setWeights(listOf(1, 2, 3)).payload.getJSONArray("weights").toString())
        assertEquals("clear-history", SpinWheelCommands.clearHistory().type)
    }

    @Test
    fun `settings go out whole`() {
        val p = SpinWheelCommands.setSettings(WheelSettings(5000, 7500, 3000, 0.45)).payload
        assertEquals("set-settings", SpinWheelCommands.setSettings(WheelSettings.DEFAULTS).type)
        assertEquals(setOf("spinMinMs", "spinMaxMs", "celebrateMs", "volume"), p.keys().asSequence().toSet())
        assertEquals(5000, p.getInt("spinMinMs"))
        assertEquals(7500, p.getInt("spinMaxMs"))
        assertEquals(3000, p.getInt("celebrateMs"))
        assertEquals(0.45, p.getDouble("volume"), 0.0)
        assertEquals(WheelSettings(5000, 7500, 3000, 0.45), WheelSettings.parse(p))
    }

    @Test
    fun `volume snaps to the web's steps`() {
        assertEquals(0.45, WheelSettings.roundVolume(0.46f), 0.0)
        assertEquals(1.0, WheelSettings.roundVolume(1.4f), 0.0)
        assertEquals(0.0, WheelSettings.roundVolume(-1f), 0.0)
    }

    @Test
    fun `every value has its own colour`() {
        val fills = Fixtures.values.map { ValueColors.of(it).fill }
        assertEquals(10, fills.toSet().size)
        assertEquals(0xFFB3121F, ValueColors.of(200).fill)
    }
}
