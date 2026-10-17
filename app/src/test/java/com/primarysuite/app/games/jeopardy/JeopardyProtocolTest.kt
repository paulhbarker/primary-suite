package com.primarysuite.app.games.jeopardy

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JeopardyProtocolTest {

    @Test
    fun `lobby state`() {
        val s = Fixtures.state("lobby")
        assertEquals(Phase.LOBBY, s.phase)
        assertNull(s.boardId)
        assertNull(s.round)
        assertNull(s.clue)
        assertEquals(listOf("Amy", "Bob", "Cat"), s.players.map { it.name })
        assertEquals(Settings(), s.settings)
        assertFalse(s.canUndo)
        assertTrue(s.log.isEmpty())
    }

    @Test
    fun `players carry button, phone and colour`() {
        val (amy, bob, cat) = Fixtures.state("lobby").players
        assertEquals(4, amy.buttonId)
        assertEquals("#2f6fdf", amy.color)
        assertEquals("#ffffff", amy.ink)
        assertFalse(amy.hasPhone)
        assertNull(bob.buttonId)
        assertNull(bob.color)
        assertTrue(bob.hasPhone)
        assertEquals(-200, bob.score)
        assertFalse(cat.connected)
    }

    @Test
    fun `board state with tiles, daily double and hidden category`() {
        val s = Fixtures.state("board")
        val round = s.round!!
        assertEquals(Phase.BOARD, s.phase)
        assertEquals("Science Night", s.boardName)
        assertEquals("p1", s.control)
        assertEquals(0, round.index)
        assertEquals(2, round.count)
        assertEquals(listOf(200, 400, 600, 800, 1000), round.values)
        assertEquals(6, round.tiles.size)
        assertEquals(5, round.tiles[0].size)
        assertEquals(TileState.USED, round.tiles[0][0].state)
        assertTrue(round.tiles[2][3].dailyDouble)
        assertFalse(round.tiles[2][2].dailyDouble)
        assertEquals(TileState.EMPTY, round.tiles[5][0].state)
        assertFalse(round.categories[5].visible)
        assertEquals("desc", round.categories[0].description)
    }

    @Test
    fun `host-only fields`() {
        val s = Fixtures.state("board")
        assertTrue(s.canUndo)
        assertEquals(2, s.log.size)
        assertEquals(java.time.Instant.parse("2026-10-17T18:00:01.123Z").toEpochMilli(), s.log[0].atMs)
        assertEquals(200, s.log[0].delta)
        assertEquals("p1", s.log[0].playerId)
        assertNull(s.log[1].playerId)
        assertEquals(ButtonPress(4, java.time.Instant.parse("2026-10-17T18:00:02.5Z").toEpochMilli(), "p1"), s.lastButton)
    }

    @Test
    fun `answering clue with an image response and a countdown`() {
        val s = Fixtures.state("answering")
        val clue = s.clue!!
        assertEquals(Phase.ANSWERING, s.phase)
        assertEquals(600, clue.value)
        assertEquals("CAT 2", clue.category)
        assertEquals(BuzzerMode.MANUAL, clue.mode)
        assertEquals(Side(isImage = false, text = "This planet is red"), clue.clue)
        assertEquals(Side(isImage = true, imageUrl = "/api/jeopardy/images/mars.jpg", topCaption = "What is", bottomCaption = "Mars?"), clue.response)
        assertEquals("p2", clue.responder)
        assertEquals(listOf("p3"), clue.lockedOut)
        assertNull(clue.minWager)
        assertEquals(1760700005000, s.deadline)
        assertEquals("answer", s.deadlineKind)
        assertEquals(5000, s.deadlineTotalMs)
        assertEquals("p3", s.assigningButtonFor)
        assertEquals("Bob", s.nameOf(clue.responder))
        assertEquals("—", s.nameOf("nobody"))
    }

    @Test
    fun `daily double wager bounds`() {
        val clue = Fixtures.state("dd-wager").clue!!
        assertTrue(clue.dailyDouble)
        assertEquals("p1", clue.wagerer)
        assertEquals(5, clue.minWager)
        assertEquals(1000, clue.maxWager)
        assertFalse(clue.wagerSet)
    }

    @Test
    fun `missing and unknown values fall back safely`() {
        val s = JeopardyParser.state(JSONObject("""{"phase":"some-new-phase","settings":{"buzzerMode":"psychic"}}"""))
        assertEquals(Phase.LOBBY, s.phase)
        assertEquals(BuzzerMode.MANUAL, s.settings.buzzerMode)
        assertTrue(s.players.isEmpty())
        assertEquals(0L, s.deadline)
    }

    @Test
    fun `settings round trip`() {
        val custom = Settings(dailyDoubles = false, buzzerMode = BuzzerMode.AUTOMATIC, buzzWindowMs = 0, hideResponses = true, soundVolume = 0.25)
        assertEquals(custom, JeopardyParser.settings(custom.toJson()))
    }

    @Test
    fun `board list`() {
        val boards = JeopardyParser.boards(
            JSONArray("""[{"id":"b1","name":"Science","updatedAt":"2026-10-01T00:00:00Z","roundCount":2},{"name":"no id"},{"id":"b2","name":"Kids","roundCount":1}]"""),
        )
        assertEquals(listOf(BoardSummary("b1", "Science", 2), BoardSummary("b2", "Kids", 1)), boards)
    }
}
