package com.primarysuite.app.games.reelrush

import org.json.JSONObject

/** Builds server `state` payloads and the model objects the tests share. */
object Fixtures {
    val settings = ReelRushSettings.DEFAULTS

    private fun player(slot: Int, name: String, color: String, joined: Boolean, score: Int = 0, fouls: Int = 0, status: String = "out", rank: Int = 0, reactionMs: Int? = null): String =
        """{"slot":$slot,"name":"$name","color":"$color","joined":$joined,"score":$score,"fouls":$fouls,"status":"$status"""" +
            (if (rank > 0) ""","rank":$rank""" else "") + (if (reactionMs != null) ""","reactionMs":$reactionMs""" else "") + "}"

    val noPlayers = listOf(
        player(1, "GREEN", "green", false), player(2, "YELLOW", "yellow", false),
        player(3, "WHITE", "white", false), player(4, "BLUE", "blue", false),
    )

    fun players(vararg joined: Boolean): List<String> = listOf(
        player(1, "GREEN", "green", joined[0]), player(2, "YELLOW", "yellow", joined[1]),
        player(3, "WHITE", "white", joined[2]), player(4, "BLUE", "blue", joined[3]),
    )

    fun stateJson(
        phase: String = "idle",
        round: Int = 0,
        rounds: Int = 5,
        suddenDeath: Boolean = false,
        players: List<String> = noPlayers,
        result: String? = null,
        history: List<String> = emptyList(),
        final: String? = null,
        settings: JSONObject = ReelRushSettings.DEFAULTS.toJson(),
    ): JSONObject = JSONObject(
        """{"phase":"$phase","settings":$settings,"round":$round,"rounds":$rounds,"suddenDeath":$suddenDeath,""" +
            """"players":[${players.joinToString(",")}],""" +
            (if (result != null) """"result":$result,""" else "") +
            (if (final != null) """"final":$final,""" else "") +
            """"history":[${history.joinToString(",")}],"serverTime":1.7e12,""" +
            """"timeline":{"roundId":1,"reels":[],"spins":[],"scenes":[],"participants":[1,2]}}""",
    )

    fun state(
        phase: String = "idle",
        round: Int = 0,
        rounds: Int = 5,
        suddenDeath: Boolean = false,
        players: List<String> = noPlayers,
        result: String? = null,
        history: List<String> = emptyList(),
        final: String? = null,
        settings: JSONObject = ReelRushSettings.DEFAULTS.toJson(),
    ): ReelRushState = ReelRushState.parse(stateJson(phase, round, rounds, suddenDeath, players, result, history, final, settings))

    val result = """{"roundId":3,"round":2,"suddenDeath":false,"voided":false,"target":"cherry","entries":[""" +
        """{"slot":2,"rank":1,"reactionMs":234,"points":3,"foul":false,"closedOut":false},""" +
        """{"slot":1,"rank":2,"reactionMs":410,"points":2,"foul":false,"closedOut":false},""" +
        """{"slot":3,"foul":true,"closedOut":false,"points":0},""" +
        """{"slot":4,"closedOut":true,"foul":false,"points":0}],"winners":[2]}"""

    val final = """{"standings":[{"slot":2,"rank":1,"score":9,"fouls":0,"bestMs":180,"avgMs":300,"wins":3},""" +
        """{"slot":1,"rank":2,"score":6,"fouls":1,"wins":1}],"winners":[2],"tiebreak":true,""" +
        """"titles":[{"key":"fastest","label":"Fastest","slots":[2],"value":"0.180s"},""" +
        """{"key":"jumpiest","label":"Jumpiest","slots":[1,3],"value":"2 fouls"}]}"""
}
