package com.primarysuite.app.games.reelrush

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Reel Rush protocol, from `backend/cmd/reelrush/{messages,view,settings,engine}.go`
 * and the web admin (`frontend/src/games/reel-rush/admin/Admin.tsx`).
 */
object ReelRushProtocol {
    const val HELLO = "hello"
    const val ROLE_ADMIN = "admin"
    const val STATE = "state"
    const val ERROR = "error"
    const val SETTINGS = "settings"
    const val OPEN_LOBBY = "open-lobby"
    const val REMOVE_PLAYER = "remove-player"
    const val START_GAME = "start-game"
    const val CLOSE_ROUND = "close-round"
    const val NEXT_ROUND = "next-round"
    const val REPLAY_ROUND = "replay-round"
    const val END_GAME = "end-game"
    const val RESET = "reset"

    /** Players needed to start a game. */
    const val MIN_PLAYERS = 2

    const val MAX_NAME_LENGTH = 10

    /** Allowed ranges, from `Settings.Validate` on the server. */
    val REELS = 2..5
    val ROUNDS = 1..50
    val ROUND_MS = 1000..30000
    val NEAR_MISSES = 0..4
    val DWELL_MS = 100..5000
    val DISPLAY_LAG_MS = 0..300
    const val SPIN_SPEED_MIN = 15.0
    const val SPIN_SPEED_MAX = 40.0

    /** The web's volume slider: 0 to 1 in steps of 0.05. */
    const val VOLUME_STEP = 0.05f
}

enum class Phase(val slug: String) {
    IDLE("idle"),
    LOBBY("lobby"),
    INTRO("intro"),
    SPINNING("spinning"),
    WINDOW("window"),
    RESULTS("results"),
    FINAL("final"),
    ;

    /** A round is being played: the countdown, the spin, or the press window. */
    val inRound: Boolean get() = this == INTRO || this == SPINNING || this == WINDOW

    companion object {
        /** An unknown phase from a newer server reads as idle. */
        fun of(slug: String): Phase = entries.firstOrNull { it.slug == slug } ?: IDLE
    }
}

/** Player colours by slot, matching the physical buttons (`PLAYER_COLORS` on the web). */
enum class PlayerColor(val slug: String, val rgb: Long) {
    GREEN("green", 0xFF3FD46A),
    YELLOW("yellow", 0xFFFFD23A),
    WHITE("white", 0xFFF4F4F4),
    BLUE("blue", 0xFF3D8BFF),
    ;

    companion object {
        private val BY_SLOT = listOf(GREEN, YELLOW, WHITE, BLUE)
        fun of(slug: String, slot: Int): PlayerColor =
            entries.firstOrNull { it.slug == slug } ?: BY_SLOT.getOrElse(slot - 1) { WHITE }
    }
}

enum class PlayerStatus(val slug: String) {
    OUT("out"),
    WAITING("waiting"),
    PRESSED("pressed"),
    FOUL("foul"),
    CLOSED("closed"),
    ;

    companion object {
        fun of(slug: String): PlayerStatus = entries.firstOrNull { it.slug == slug } ?: OUT
    }
}

private fun JSONObject.intOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it) }

private inline fun <T> JSONArray?.objects(parse: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }.map(parse)

/** The host's settings. They live in the server's memory only. */
data class ReelRushSettings(
    val reels: Int,
    val rounds: Int,
    val hardMode: Boolean,
    val assist: Boolean,
    val roundMinMs: Int,
    val roundMaxMs: Int,
    val nearMisses: Int,
    val rowDecoys: Boolean,
    val spinSpeed: Double,
    val dwellMinMs: Int,
    val dwellMaxMs: Int,
    val displayLagMs: Int,
    val names: List<String>,
    val volume: Double,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("reels", reels)
        .put("rounds", rounds)
        .put("hardMode", hardMode)
        .put("assist", assist)
        .put("roundMinMs", roundMinMs)
        .put("roundMaxMs", roundMaxMs)
        .put("nearMisses", nearMisses)
        .put("rowDecoys", rowDecoys)
        .put("spinSpeed", spinSpeed)
        .put("dwellMinMs", dwellMinMs)
        .put("dwellMaxMs", dwellMaxMs)
        .put("displayLagMs", displayLagMs)
        .put("names", JSONArray(names))
        .put("volume", volume)

    companion object {
        /** The server's `DefaultSettings`, used for fields a message leaves out. */
        val DEFAULTS = ReelRushSettings(
            reels = 3,
            rounds = 5,
            hardMode = false,
            assist = false,
            roundMinMs = 1000,
            roundMaxMs = 10000,
            nearMisses = 2,
            rowDecoys = true,
            spinSpeed = 25.0,
            dwellMinMs = 250,
            dwellMaxMs = 1500,
            displayLagMs = 50,
            names = listOf("GREEN", "YELLOW", "WHITE", "BLUE"),
            volume = 0.8,
        )

        fun parse(p: JSONObject?): ReelRushSettings {
            val d = DEFAULTS
            if (p == null) return d
            val given = p.optJSONArray("names")
            val names = d.names.mapIndexed { i, fallback -> given?.optString(i, fallback) ?: fallback }
            return ReelRushSettings(
                reels = p.optInt("reels", d.reels),
                rounds = p.optInt("rounds", d.rounds),
                hardMode = p.optBoolean("hardMode", d.hardMode),
                assist = p.optBoolean("assist", d.assist),
                roundMinMs = p.optInt("roundMinMs", d.roundMinMs),
                roundMaxMs = p.optInt("roundMaxMs", d.roundMaxMs),
                nearMisses = p.optInt("nearMisses", d.nearMisses),
                rowDecoys = p.optBoolean("rowDecoys", d.rowDecoys),
                spinSpeed = p.optDouble("spinSpeed", d.spinSpeed),
                dwellMinMs = p.optInt("dwellMinMs", d.dwellMinMs),
                dwellMaxMs = p.optInt("dwellMaxMs", d.dwellMaxMs),
                displayLagMs = p.optInt("displayLagMs", d.displayLagMs),
                names = names,
                volume = p.optDouble("volume", d.volume),
            )
        }

        /** Snaps a slider value to the web's 0.05 steps as a clean decimal (0.85, not 0.8500000238). */
        fun roundVolume(v: Float): Double = Math.round(v.coerceIn(0f, 1f) * 20) / 20.0
    }
}

data class ReelRushPlayer(
    val slot: Int,
    val name: String,
    val color: PlayerColor,
    val joined: Boolean,
    val score: Int,
    val fouls: Int,
    val status: PlayerStatus,
    val reactionMs: Int?,
    val rank: Int,
) {
    companion object {
        fun parse(p: JSONObject, index: Int): ReelRushPlayer {
            val slot = p.optInt("slot", index + 1)
            return ReelRushPlayer(
                slot = slot,
                name = p.optString("name", "Player $slot"),
                color = PlayerColor.of(p.optString("color"), slot),
                joined = p.optBoolean("joined", false),
                score = p.optInt("score", 0),
                fouls = p.optInt("fouls", 0),
                status = PlayerStatus.of(p.optString("status")),
                reactionMs = p.intOrNull("reactionMs"),
                rank = p.optInt("rank", 0),
            )
        }
    }
}

data class ResultEntry(
    val slot: Int,
    val rank: Int?,
    val reactionMs: Int?,
    val points: Int,
    val foul: Boolean,
    val closedOut: Boolean,
) {
    companion object {
        fun parse(p: JSONObject) = ResultEntry(
            slot = p.optInt("slot"),
            rank = p.intOrNull("rank"),
            reactionMs = p.intOrNull("reactionMs"),
            points = p.optInt("points", 0),
            foul = p.optBoolean("foul", false),
            closedOut = p.optBoolean("closedOut", false),
        )
    }
}

data class RoundResult(
    val round: Int,
    val suddenDeath: Boolean,
    val voided: Boolean,
    val entries: List<ResultEntry>,
    val winners: List<Int>,
) {
    companion object {
        fun parse(p: JSONObject) = RoundResult(
            round = p.optInt("round", 0),
            suddenDeath = p.optBoolean("suddenDeath", false),
            voided = p.optBoolean("voided", false),
            entries = p.optJSONArray("entries").objects(ResultEntry::parse),
            winners = p.optJSONArray("winners").ints(),
        )
    }
}

data class Standing(val slot: Int, val rank: Int, val score: Int, val bestMs: Int?) {
    companion object {
        fun parse(p: JSONObject) = Standing(
            slot = p.optInt("slot"),
            rank = p.optInt("rank", 0),
            score = p.optInt("score", 0),
            bestMs = p.intOrNull("bestMs"),
        )
    }
}

/** A fun stat award (fastest, steadiest, jumpiest). */
data class Title(val key: String, val label: String, val slots: List<Int>, val value: String) {
    companion object {
        fun parse(p: JSONObject) = Title(
            key = p.optString("key"),
            label = p.optString("label"),
            slots = p.optJSONArray("slots").ints(),
            value = p.optString("value"),
        )
    }
}

data class FinalView(
    val standings: List<Standing>,
    val winners: List<Int>,
    val tiebreak: Boolean,
    val titles: List<Title>,
) {
    companion object {
        fun parse(p: JSONObject) = FinalView(
            standings = p.optJSONArray("standings").objects(Standing::parse),
            winners = p.optJSONArray("winners").ints(),
            tiebreak = p.optBoolean("tiebreak", false),
            titles = p.optJSONArray("titles").objects(Title::parse),
        )
    }
}

/** What the next-round button says in the results phase. */
enum class NextAction { NEXT_ROUND, REPLAY_ROUND, SHOW_WINNER, ANOTHER_SUDDEN_DEATH, FINISH_GAME }

/** The whole game, as the server sends it. The display's `timeline` is not read. */
data class ReelRushState(
    val phase: Phase,
    val settings: ReelRushSettings,
    val round: Int,
    val rounds: Int,
    val suddenDeath: Boolean,
    val players: List<ReelRushPlayer>,
    val result: RoundResult?,
    val history: List<RoundResult>,
    val final: FinalView?,
) {
    val joinedCount: Int get() = players.count { it.joined }

    /** The web shows the round number once a round exists and the game is under way. */
    val showRound: Boolean get() = round > 0 && phase != Phase.LOBBY && phase != Phase.IDLE && phase != Phase.FINAL

    /** The web's nextLabel logic. */
    val nextAction: NextAction
        get() = when {
            result?.voided == true -> NextAction.REPLAY_ROUND
            suddenDeath -> if (result?.winners?.size == 1) NextAction.SHOW_WINNER else NextAction.ANOTHER_SUDDEN_DEATH
            round >= rounds -> NextAction.FINISH_GAME
            else -> NextAction.NEXT_ROUND
        }

    /** The round the "last round" card shows: the current result, else the latest in the history. */
    val lastRound: RoundResult? get() = result ?: history.lastOrNull()

    fun player(slot: Int): ReelRushPlayer? = players.firstOrNull { it.slot == slot } ?: players.getOrNull(slot - 1)

    companion object {
        fun parse(p: JSONObject): ReelRushState {
            val settings = ReelRushSettings.parse(p.optJSONObject("settings"))
            return ReelRushState(
                phase = Phase.of(p.optString("phase")),
                settings = settings,
                round = p.optInt("round", 0),
                rounds = p.optInt("rounds", settings.rounds),
                suddenDeath = p.optBoolean("suddenDeath", false),
                players = (0 until (p.optJSONArray("players")?.length() ?: 0))
                    .mapNotNull { i -> p.optJSONArray("players")?.optJSONObject(i)?.let { ReelRushPlayer.parse(it, i) } },
                result = p.optJSONObject("result")?.let(RoundResult::parse),
                history = p.optJSONArray("history").objects(RoundResult::parse),
                final = p.optJSONObject("final")?.let(FinalView::parse),
            )
        }
    }
}

/** What the admin sends. */
object ReelRushCommands {
    fun hello() = GameMessage(ReelRushProtocol.HELLO, JSONObject().put("role", ReelRushProtocol.ROLE_ADMIN))
    fun settings(s: ReelRushSettings) = GameMessage(ReelRushProtocol.SETTINGS, s.toJson())
    fun openLobby() = GameMessage(ReelRushProtocol.OPEN_LOBBY)
    fun removePlayer(slot: Int) = GameMessage(ReelRushProtocol.REMOVE_PLAYER, JSONObject().put("slot", slot))
    fun startGame() = GameMessage(ReelRushProtocol.START_GAME)
    fun closeRound() = GameMessage(ReelRushProtocol.CLOSE_ROUND)
    fun nextRound() = GameMessage(ReelRushProtocol.NEXT_ROUND)
    fun replayRound() = GameMessage(ReelRushProtocol.REPLAY_ROUND)
    fun endGame() = GameMessage(ReelRushProtocol.END_GAME)
    fun reset() = GameMessage(ReelRushProtocol.RESET)
}
