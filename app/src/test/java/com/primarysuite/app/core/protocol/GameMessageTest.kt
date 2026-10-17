package com.primarysuite.app.core.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMessageTest {

    @Test
    fun `encodes type and payload`() {
        val json = JSONObject(GameMessage("nfc-check-in", JSONObject().put("number", 42)).encode())

        assertEquals("nfc-check-in", json.getString("type"))
        assertEquals(42, json.getJSONObject("payload").getInt("number"))
    }

    @Test
    fun `encodes an empty payload as an object`() {
        val json = JSONObject(GameMessage("admin-reset-game").encode())
        assertEquals(0, json.getJSONObject("payload").length())
    }

    @Test
    fun `decodes a server message`() {
        val message = GameMessage.decode("""{"type":"lucky-tap-full-state","payload":{"numWinners":3,"checkedInNumbers":[1,2]}}""")!!

        assertEquals("lucky-tap-full-state", message.type)
        assertEquals(3, message.payload.getInt("numWinners"))
        assertEquals(2, message.payload.getJSONArray("checkedInNumbers").length())
    }

    @Test
    fun `missing or non-object payload decodes as empty`() {
        assertEquals(0, GameMessage.decode("""{"type":"ping"}""")!!.payload.length())
        assertEquals(0, GameMessage.decode("""{"type":"ping","payload":[1,2]}""")!!.payload.length())
    }

    @Test
    fun `rejects frames that are not game messages`() {
        listOf("", "not json", "[]", "{}", """{"type":""}""", """{"type":7}""", """{"payload":{}}""")
            .forEach { assertNull(it, GameMessage.decode(it)) }
    }

    @Test
    fun `round trips and compares by content`() {
        val original = GameMessage("card-scanned", JSONObject().put("number", 7))
        assertEquals(original, GameMessage.decode(original.encode()))
    }

    @Test
    fun `blank type is not allowed`() {
        assertTrue(runCatching { GameMessage(" ") }.isFailure)
    }
}
