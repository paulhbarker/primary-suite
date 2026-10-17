package com.primarysuite.app.games.jeopardy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The JSON each host command puts on the wire, matching what the web host sends. */
class HostCommandsTest {

    private fun wire(m: com.primarysuite.app.core.protocol.GameMessage) = JSONObject(m.encode())

    private fun assertWire(type: String, payload: String, m: com.primarysuite.app.core.protocol.GameMessage) {
        val json = wire(m)
        assertEquals(type, json.getString("type"))
        assertEquals(JSONObject(payload).toString(), json.getJSONObject("payload").toString())
    }

    @Test
    fun `commands with payloads`() {
        assertWire("jeopardy-hello", """{"role":"host"}""", HostCommands.hello())
        assertWire("jeopardy-load-board", """{"boardId":"b1"}""", HostCommands.loadBoard("b1"))
        assertWire("jeopardy-set-control", """{"playerId":"p1"}""", HostCommands.setControl("p1"))
        assertWire("jeopardy-set-control", """{"playerId":""}""", HostCommands.setControl(null))
        assertWire("jeopardy-select-clue", """{"col":2,"row":4}""", HostCommands.selectClue(2, 4))
        assertWire("jeopardy-select-responder", """{"playerId":"p2"}""", HostCommands.selectResponder("p2"))
        assertWire("jeopardy-judge", """{"correct":false}""", HostCommands.judge(false))
        assertWire("jeopardy-dd-wager", """{"amount":500}""", HostCommands.wager(500))
        assertWire("jeopardy-dd-wager", """{"trueDailyDouble":true}""", HostCommands.trueDailyDouble())
        assertWire("jeopardy-adjust-score", """{"playerId":"p1","delta":-400}""", HostCommands.adjustScore("p1", -400))
        assertWire("jeopardy-adjust-score", """{"playerId":"p1","score":1200}""", HostCommands.setScore("p1", 1200))
        assertWire("jeopardy-adjust-score", """{"playerId":"p1","delta":-5,"drain":true}""", HostCommands.drainTick("p1"))
        assertWire("jeopardy-assign-button", """{"playerId":""}""", HostCommands.assignButton(null))
        assertWire("jeopardy-move-player", """{"playerId":"p2","index":0}""", HostCommands.movePlayer("p2", 0))
        assertWire("jeopardy-rename-player", """{"playerId":"p2","name":"Bobby"}""", HostCommands.renamePlayer("p2", "Bobby"))
    }

    @Test
    fun `commands without payloads`() {
        mapOf(
            "jeopardy-start-game" to HostCommands.startGame(),
            "jeopardy-next" to HostCommands.next(),
            "jeopardy-arm" to HostCommands.arm(),
            "jeopardy-reveal" to HostCommands.reveal(),
            "jeopardy-cancel-clue" to HostCommands.cancelClue(),
            "jeopardy-continue" to HostCommands.continueToBoard(),
            "jeopardy-end-round" to HostCommands.endRound(),
            "jeopardy-next-round" to HostCommands.nextRound(),
            "jeopardy-end-game" to HostCommands.endGame(),
            "jeopardy-reset-game" to HostCommands.resetGame(),
            "jeopardy-undo" to HostCommands.undo(),
        ).forEach { (type, m) -> assertWire(type, "{}", m) }
    }

    @Test
    fun `settings are sent whole`() {
        val json = wire(HostCommands.updateSettings(Settings(buzzerMode = BuzzerMode.NONE)))
        assertEquals("jeopardy-update-settings", json.getString("type"))
        assertEquals("none", json.getJSONObject("payload").getString("buzzerMode"))
        assertEquals(14, json.getJSONObject("payload").length())
    }
}
