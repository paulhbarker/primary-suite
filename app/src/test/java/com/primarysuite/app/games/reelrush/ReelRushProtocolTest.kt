package com.primarysuite.app.games.reelrush

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelRushProtocolTest {

    @Test
    fun `parses a full state and ignores the timeline`() {
        val s = Fixtures.state(
            phase = "results", round = 2, players = Fixtures.players(true, true, true, false),
            result = Fixtures.result, history = listOf(Fixtures.result),
        )
        assertEquals(Phase.RESULTS, s.phase)
        assertEquals(2, s.round)
        assertEquals(5, s.rounds)
        assertEquals(4, s.players.size)
        assertEquals(3, s.joinedCount)
        assertEquals(PlayerColor.YELLOW, s.players[1].color)
        assertEquals(1, s.history.size)
        val r = s.result!!
        assertEquals(listOf(2), r.winners)
        assertEquals(4, r.entries.size)
        assertEquals(ResultEntry(2, 1, 234, 3, foul = false, closedOut = false), r.entries[0])
        assertNull(r.entries[2].rank)
        assertTrue(r.entries[2].foul)
        assertTrue(r.entries[3].closedOut)
        assertNull(s.final)
    }

    @Test
    fun `parses a player's press`() {
        val p = ReelRushPlayer.parse(
            JSONObject("""{"slot":3,"name":"WHITE","color":"white","joined":true,"score":4,"fouls":1,"status":"pressed","reactionMs":321,"rank":2}"""),
            2,
        )
        assertEquals(PlayerStatus.PRESSED, p.status)
        assertEquals(321, p.reactionMs)
        assertEquals(2, p.rank)
        assertEquals(1, p.fouls)
    }

    @Test
    fun `parses the final standings and titles`() {
        val s = Fixtures.state(phase = "final", final = Fixtures.final)
        val f = s.final!!
        assertTrue(f.tiebreak)
        assertEquals(listOf(2), f.winners)
        assertEquals(Standing(2, 1, 9, 180), f.standings[0])
        assertNull(f.standings[1].bestMs)
        assertEquals(Title("jumpiest", "Jumpiest", listOf(1, 3), "2 fouls"), f.titles[1])
    }

    @Test
    fun `missing fields fall back to the server's defaults`() {
        val s = ReelRushState.parse(JSONObject("""{"phase":"lobby"}"""))
        assertEquals(Phase.LOBBY, s.phase)
        assertEquals(ReelRushSettings.DEFAULTS, s.settings)
        assertTrue(s.players.isEmpty())
        assertTrue(s.history.isEmpty())
        assertNull(s.result)
        assertEquals(5, s.rounds)
        assertEquals(0, s.joinedCount)
    }

    @Test
    fun `a partial settings object keeps the defaults for the rest`() {
        val s = ReelRushSettings.parse(JSONObject("""{"reels":4,"hardMode":true,"names":["A","B"]}"""))
        assertEquals(4, s.reels)
        assertTrue(s.hardMode)
        assertEquals(listOf("A", "B", "WHITE", "BLUE"), s.names)
        assertEquals(ReelRushSettings.DEFAULTS.rounds, s.rounds)
    }

    @Test
    fun `an unknown phase reads as idle and an unknown status as out`() {
        assertEquals(Phase.IDLE, Phase.of("warp"))
        assertEquals(PlayerStatus.OUT, PlayerStatus.of("???"))
        assertEquals(PlayerColor.BLUE, PlayerColor.of("pink", 4))
    }

    @Test
    fun `winners can be null`() {
        val r = RoundResult.parse(JSONObject("""{"round":1,"entries":null,"winners":null}"""))
        assertTrue(r.winners.isEmpty())
        assertTrue(r.entries.isEmpty())
    }

    @Test
    fun `settings round trip through json`() {
        val s = ReelRushSettings.DEFAULTS.copy(reels = 5, volume = 0.85, spinSpeed = 22.5, names = listOf("A", "B", "C", "D"))
        assertEquals(s, ReelRushSettings.parse(s.toJson()))
        val j = s.toJson()
        assertEquals(0.85, j.getDouble("volume"), 0.0)
        assertEquals(4, j.getJSONArray("names").length())
    }

    @Test
    fun `volume snaps to clean twentieths`() {
        assertEquals(0.85, ReelRushSettings.roundVolume(0.85f), 0.0)
        assertEquals(0.0, ReelRushSettings.roundVolume(-1f), 0.0)
        assertEquals(1.0, ReelRushSettings.roundVolume(2f), 0.0)
        assertEquals(0.3, ReelRushSettings.roundVolume(0.29f), 0.0)
    }

    @Test
    fun `next action follows the web's labels`() {
        val voided = """{"round":2,"voided":true,"entries":[],"winners":[]}"""
        val one = """{"round":5,"suddenDeath":true,"entries":[],"winners":[2]}"""
        val tie = """{"round":5,"suddenDeath":true,"entries":[],"winners":[1,2]}"""
        val plain = """{"round":2,"entries":[],"winners":[2]}"""
        assertEquals(NextAction.REPLAY_ROUND, Fixtures.state("results", 2, result = voided).nextAction)
        assertEquals(NextAction.SHOW_WINNER, Fixtures.state("results", 5, suddenDeath = true, result = one).nextAction)
        assertEquals(NextAction.ANOTHER_SUDDEN_DEATH, Fixtures.state("results", 5, suddenDeath = true, result = tie).nextAction)
        assertEquals(NextAction.FINISH_GAME, Fixtures.state("results", 5, rounds = 5, result = plain).nextAction)
        assertEquals(NextAction.NEXT_ROUND, Fixtures.state("results", 2, rounds = 5, result = plain).nextAction)
        // Voided wins over the last round.
        assertEquals(NextAction.REPLAY_ROUND, Fixtures.state("results", 5, rounds = 5, result = voided).nextAction)
    }

    @Test
    fun `the round line shows only while a game is under way`() {
        assertFalse(Fixtures.state("idle", 0).showRound)
        assertFalse(Fixtures.state("lobby", 1).showRound)
        assertFalse(Fixtures.state("final", 5).showRound)
        assertFalse(Fixtures.state("spinning", 0).showRound)
        assertTrue(Fixtures.state("spinning", 1).showRound)
        assertTrue(Fixtures.state("results", 3).showRound)
    }

    @Test
    fun `last round is the result, else the newest history entry`() {
        val h1 = """{"round":1,"entries":[],"winners":[1]}"""
        val h2 = """{"round":2,"entries":[],"winners":[2]}"""
        assertEquals(2, Fixtures.state(history = listOf(h1, h2)).lastRound?.round)
        assertEquals(2, Fixtures.state(result = Fixtures.result, history = listOf(h1)).lastRound?.round)
        assertNull(Fixtures.state().lastRound)
    }

    @Test
    fun `player lookup is by slot`() {
        val s = Fixtures.state()
        assertEquals("BLUE", s.player(4)?.name)
        assertNull(s.player(9))
    }

    // Commands

    @Test
    fun `hello says admin`() {
        val m = ReelRushCommands.hello()
        assertEquals("hello", m.type)
        assertEquals("admin", m.payload.getString("role"))
    }

    @Test
    fun `commands without a payload have the web's types`() {
        listOf(
            ReelRushCommands.openLobby() to "open-lobby",
            ReelRushCommands.startGame() to "start-game",
            ReelRushCommands.closeRound() to "close-round",
            ReelRushCommands.nextRound() to "next-round",
            ReelRushCommands.replayRound() to "replay-round",
            ReelRushCommands.endGame() to "end-game",
            ReelRushCommands.reset() to "reset",
        ).forEach { (m, type) ->
            assertEquals(type, m.type)
            assertEquals(0, m.payload.length())
        }
    }

    @Test
    fun `remove player carries the slot`() {
        val m = ReelRushCommands.removePlayer(3)
        assertEquals("remove-player", m.type)
        assertEquals(3, m.payload.getInt("slot"))
        assertEquals(1, m.payload.length())
    }

    @Test
    fun `settings carries every field the web sends`() {
        val m = ReelRushCommands.settings(ReelRushSettings.DEFAULTS.copy(hardMode = true))
        assertEquals("settings", m.type)
        val p = m.payload
        assertEquals(
            setOf(
                "reels", "rounds", "hardMode", "assist", "roundMinMs", "roundMaxMs", "nearMisses", "rowDecoys",
                "spinSpeed", "dwellMinMs", "dwellMaxMs", "displayLagMs", "names", "volume",
            ),
            p.keys().asSequence().toSet(),
        )
        assertEquals(3, p.getInt("reels"))
        assertTrue(p.getBoolean("hardMode"))
        assertEquals(25.0, p.getDouble("spinSpeed"), 0.0)
        assertEquals("YELLOW", p.getJSONArray("names").getString(1))
    }
}
