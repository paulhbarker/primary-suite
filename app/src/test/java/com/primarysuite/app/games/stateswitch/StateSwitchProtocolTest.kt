package com.primarysuite.app.games.stateswitch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateSwitchProtocolTest {

    @Test
    fun `parses settings as the server sends them`() {
        val s = StateSwitchSettings.parse(JSONObject("""{"game":"go-stop","timeout":2.5,"limitEnabled":true,"limit":12}"""))
        assertEquals(StateSwitchSettings("go-stop", 2.5f, limitEnabled = true, limit = 12), s)
    }

    @Test
    fun `settings parse leniently`() {
        val s = StateSwitchSettings.parse(JSONObject("{}"))
        assertEquals(StateSwitchSettings("fast-slow", 0f, limitEnabled = false, limit = 20), s)
        assertEquals(0f, StateSwitchSettings.parse(JSONObject("""{"timeout":0}""")).timeout)
    }

    @Test
    fun `keeps an unknown game type as sent`() {
        assertEquals("rock-paper", StateSwitchSettings.parse(JSONObject("""{"game":"rock-paper"}""")).game)
        assertNull(GameType.of("rock-paper"))
    }

    @Test
    fun `settings encode with the server's field names and exact tenths`() {
        val m = StateSwitchCommands.settings(StateSwitchSettings("loud-soft", 2.3f, true, 8))
        assertEquals("state-switch-game-settings", m.type)
        val sent = JSONObject(m.encode())
        assertEquals("state-switch-game-settings", sent.getString("type"))
        val p = sent.getJSONObject("payload")
        assertEquals(setOf("game", "timeout", "limitEnabled", "limit"), p.keys().asSequence().toSet())
        assertEquals("loud-soft", p.getString("game"))
        assertTrue("timeout was ${p.get("timeout")}", m.encode().contains("\"timeout\":2.3,") || m.encode().contains("\"timeout\":2.3}"))
        assertEquals(true, p.getBoolean("limitEnabled"))
        assertEquals(8, p.getInt("limit"))
    }

    @Test
    fun `commands match the web admin`() {
        listOf(
            StateSwitchCommands.reset() to "state-switch-reset",
            StateSwitchCommands.resetActuations() to "state-switch-reset-actuations",
            StateSwitchCommands.press() to "button-press",
        ).forEach { (m, type) ->
            val json = JSONObject(m.encode())
            assertEquals(type, json.getString("type"))
            assertEquals(0, json.getJSONObject("payload").length())
        }
    }

    @Test
    fun `parses state`() {
        assertEquals(StateSwitchState(7, 1), StateSwitchState.parse(JSONObject("""{"actuations":7,"current":1}""")))
        assertEquals(StateSwitchState(), StateSwitchState.parse(JSONObject("{}")))
    }

    @Test
    fun `slider values snap to the web's steps`() {
        assertEquals(2.3f, StateSwitchSettings.roundTimeout(2.3000002f))
        assertEquals(2.3f, StateSwitchSettings.roundTimeout(2.2999998f))
        assertEquals(9f, StateSwitchSettings.roundTimeout(12f))
        assertEquals(0f, StateSwitchSettings.roundTimeout(-1f))
        assertEquals(8, StateSwitchSettings.roundLimit(7.9f))
        assertEquals(20, StateSwitchSettings.roundLimit(25f))
        assertEquals(0, StateSwitchSettings.roundLimit(0.4f))
    }

    @Test
    fun `game types and labels follow the web admin`() {
        assertEquals(
            listOf("fast-slow", "go-stop", "loud-soft", "left-right", "staccato-legato", "clapping-marching", "standing-sitting", "singing-humming", "random"),
            GameType.entries.map { it.slug },
        )
        assertEquals("Stop / Go", GameType.GO_STOP.label)
    }

    @Test
    fun `web defaults are what Reset All leaves`() {
        assertEquals(StateSwitchSettings("fast-slow", 2.5f, limitEnabled = false, limit = 20), StateSwitchSettings.WEB_DEFAULTS)
    }
}
