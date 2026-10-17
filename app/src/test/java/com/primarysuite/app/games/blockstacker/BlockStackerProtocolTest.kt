package com.primarysuite.app.games.blockstacker

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BlockStackerProtocolTest {

    private val serverState = """
        {"type":"block-stacker-game-state","payload":{
          "settings":{"maxLevels":20,"initialSpeed":2.4,"speedIncrement":0.5,"gameEnabled":false},
          "state":{"status":"active","totalGamesPlayed":7,"highScore":12,"lastUpdated":"2026-10-17T10:15:30.123456789+02:00"}}}
    """.trimIndent()

    @Test
    fun `parses the full state the server sends`() {
        val full = BlockStackerFullState.parse(GameMessage.decode(serverState)!!.payload)
        assertEquals(BlockStackerSettings(20, 2.4, 0.5, false), full.settings)
        assertEquals("active", full.state.status)
        assertEquals(7, full.state.totalGamesPlayed)
        assertEquals(12, full.state.highScore)
        assertEquals(Instant.parse("2026-10-17T08:15:30.123456789Z"), full.state.lastUpdated)
    }

    @Test
    fun `parses a Z time and rejects junk`() {
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), BlockStackerState.parse(JSONObject("""{"lastUpdated":"2026-01-02T03:04:05Z"}""")).lastUpdated)
        assertNull(BlockStackerState.parse(JSONObject("""{"lastUpdated":"yesterday"}""")).lastUpdated)
    }

    @Test
    fun `missing fields fall back to the defaults`() {
        val full = BlockStackerFullState.parse(JSONObject())
        assertEquals(BlockStackerSettings.DEFAULTS, full.settings)
        assertEquals(BlockStackerState(), full.state)

        val partial = BlockStackerSettings.parse(JSONObject("""{"maxLevels":3}"""))
        assertEquals(BlockStackerSettings.DEFAULTS.copy(maxLevels = 3), partial)
        assertNotNull(BlockStackerSettings.parse(null))
    }

    @Test
    fun `defaults match the web admin`() {
        assertEquals(BlockStackerSettings(15, 1.7, 0.8, true), BlockStackerSettings.DEFAULTS)
    }

    @Test
    fun `update settings carries the four fields`() {
        val m = BlockStackerCommands.updateSettings(BlockStackerSettings(9, 1.7, 0.3, false))
        assertEquals("block-stacker-update-settings", m.type)
        assertEquals(setOf("maxLevels", "initialSpeed", "speedIncrement", "gameEnabled"), m.payload.keys().asSequence().toSet())
        assertEquals(9, m.payload.getInt("maxLevels"))
        assertEquals(1.7, m.payload.getDouble("initialSpeed"), 0.0)
        assertEquals(0.3, m.payload.getDouble("speedIncrement"), 0.0)
        assertEquals(false, m.payload.getBoolean("gameEnabled"))
    }

    @Test
    fun `speeds are sent as exact tenths`() {
        val rounded = BlockStackerSettings.roundToTenth(2.3000001)
        assertEquals("2.3", BlockStackerCommands.updateSettings(BlockStackerSettings.DEFAULTS.copy(initialSpeed = rounded)).payload.get("initialSpeed").toString())
        assertEquals(1.7, BlockStackerSettings.roundToTenth(1.7000001), 0.0)
        assertEquals(0.1, BlockStackerSettings.roundToTenth(0.14), 0.0)
    }

    @Test
    fun `a press is a button-press with id 1`() {
        val m = BlockStackerCommands.press()
        assertEquals("button-press", m.type)
        assertEquals(1, m.payload.getInt("id"))
        assertEquals(setOf("id"), m.payload.keys().asSequence().toSet())
    }
}
