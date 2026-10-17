package com.primarysuite.app.games.jeopardy

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/*
 * The host's view of a Jeopardy! game, parsed from `jeopardy-state`. Mirrors
 * StateView in simple-games backend/cmd/jeopardy/view.go. Parsing is lenient:
 * missing fields take their Go zero value, so an older or newer server still
 * renders.
 */

enum class Phase(val wire: String) {
    LOBBY("lobby"),
    ROUND_INTRO("round-intro"),
    BOARD("board"),
    CLUE("clue"),
    ARMED("armed"),
    ANSWERING("answering"),
    DD_WAGER("dd-wager"),
    DD_CLUE("dd-clue"),
    REVEAL("reveal"),
    ROUND_END("round-end"),
    GAME_OVER("game-over"),
    ;

    companion object {
        fun from(wire: String): Phase = entries.firstOrNull { it.wire == wire } ?: LOBBY
    }
}

enum class BuzzerMode(val wire: String) {
    MANUAL("manual"),
    AUTOMATIC("automatic"),
    NONE("none"),
    ;

    companion object {
        fun from(wire: String): BuzzerMode = entries.firstOrNull { it.wire == wire } ?: MANUAL
    }
}

/** One side of a clue: text, or an image with optional captions. */
data class Side(
    val isImage: Boolean,
    val text: String = "",
    val imageUrl: String = "",
    val topCaption: String = "",
    val bottomCaption: String = "",
)

data class Player(
    val id: String,
    val name: String,
    val score: Int,
    val connected: Boolean,
    val hasPhone: Boolean,
    val buttonId: Int?,
    val draining: Boolean,
    /** The player's box button colour (`#rrggbb`), and a text colour that reads on it. */
    val color: String?,
    val ink: String?,
)

data class Category(val title: String, val description: String, val visible: Boolean, val revealed: Boolean)

enum class TileState { AVAILABLE, USED, EMPTY }

data class Tile(val state: TileState, val dailyDouble: Boolean)

data class Round(
    val index: Int,
    val count: Int,
    val categories: List<Category>,
    val values: List<Int>,
    /** `tiles[col][row]`. */
    val tiles: List<List<Tile>>,
)

data class ActiveClue(
    val col: Int,
    val row: Int,
    val value: Int,
    val category: String,
    val dailyDouble: Boolean,
    val mode: BuzzerMode,
    val clue: Side?,
    val response: Side?,
    val responder: String?,
    val lockedOut: List<String>,
    val wagerer: String?,
    val wager: Int,
    val wagerSet: Boolean,
    val minWager: Int?,
    val maxWager: Int?,
    /** correct, incorrect, timeout or skipped once the clue has ended. */
    val result: String?,
)

data class Settings(
    val dailyDoubles: Boolean = true,
    val buzzerMode: BuzzerMode = BuzzerMode.MANUAL,
    val earlyBuzzPenalty: Boolean = true,
    val earlyBuzzPenaltyMs: Int = 250,
    val deductIncorrect: Boolean = true,
    val rearmAfterIncorrect: Boolean = true,
    val buzzWindowMs: Int = 5000,
    val answerWindowMs: Int = 5000,
    val readMsPerChar: Int = 60,
    val readMinMs: Int = 2000,
    val lowestScoreStartsRound: Boolean = true,
    val revealCategories: Boolean = true,
    val hideResponses: Boolean = false,
    val soundVolume: Double = 0.8,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("dailyDoubles", dailyDoubles)
        .put("buzzerMode", buzzerMode.wire)
        .put("earlyBuzzPenalty", earlyBuzzPenalty)
        .put("earlyBuzzPenaltyMs", earlyBuzzPenaltyMs)
        .put("deductIncorrect", deductIncorrect)
        .put("rearmAfterIncorrect", rearmAfterIncorrect)
        .put("buzzWindowMs", buzzWindowMs)
        .put("answerWindowMs", answerWindowMs)
        .put("readMsPerChar", readMsPerChar)
        .put("readMinMs", readMinMs)
        .put("lowestScoreStartsRound", lowestScoreStartsRound)
        .put("revealCategories", revealCategories)
        .put("hideResponses", hideResponses)
        .put("soundVolume", soundVolume)
}

data class LogEntry(val atMs: Long, val text: String, val playerId: String?, val delta: Int)

data class ButtonPress(val buttonId: Int, val atMs: Long, val playerId: String?)

data class HostState(
    val seq: Int,
    /** Server clock (unix ms) when this state was sent. */
    val serverTime: Long,
    val phase: Phase,
    val boardId: String?,
    val boardName: String?,
    val settings: Settings,
    val players: List<Player>,
    val control: String?,
    val round: Round?,
    val clue: ActiveClue?,
    /** Unix ms when the running countdown ends; 0 = none. */
    val deadline: Long,
    /** read (automatic arming), buzz, or answer. */
    val deadlineKind: String,
    val deadlineTotalMs: Long,
    val canUndo: Boolean,
    val log: List<LogEntry>,
    val assigningButtonFor: String?,
    val lastButton: ButtonPress?,
) {
    fun player(id: String?): Player? = id?.let { pid -> players.firstOrNull { it.id == pid } }
    fun nameOf(id: String?): String = player(id)?.name ?: "—"
}

/** A board on the server, from `GET /api/jeopardy/boards`. */
data class BoardSummary(val id: String, val name: String, val roundCount: Int)

/** A game event; only some kinds matter to the host. */
data class GameEvent(val kind: String, val playerId: String?)

// --- Parsing ---

object JeopardyParser {

    fun state(p: JSONObject): HostState = HostState(
        seq = p.optInt("seq"),
        serverTime = p.optLong("serverTime"),
        phase = Phase.from(p.optString("phase")),
        boardId = p.str("boardId"),
        boardName = p.str("boardName"),
        settings = p.optJSONObject("settings")?.let(::settings) ?: Settings(),
        players = p.objects("players").map(::player),
        control = p.str("control"),
        round = p.optJSONObject("round")?.let(::round),
        clue = p.optJSONObject("clue")?.let(::clue),
        deadline = p.optLong("deadline"),
        deadlineKind = p.optString("deadlineKind"),
        deadlineTotalMs = p.optLong("deadlineTotalMs"),
        canUndo = p.optBoolean("canUndo"),
        log = p.objects("log").map { LogEntry(time(it.optString("at")), it.optString("text"), it.str("playerId"), it.optInt("delta")) },
        assigningButtonFor = p.str("assigningButtonFor"),
        lastButton = p.optJSONObject("lastButton")?.let {
            ButtonPress(it.optInt("buttonId"), time(it.optString("at")), it.str("playerId"))
        },
    )

    fun settings(o: JSONObject): Settings {
        val d = Settings()
        return Settings(
            dailyDoubles = o.optBoolean("dailyDoubles", d.dailyDoubles),
            buzzerMode = BuzzerMode.from(o.optString("buzzerMode", d.buzzerMode.wire)),
            earlyBuzzPenalty = o.optBoolean("earlyBuzzPenalty", d.earlyBuzzPenalty),
            earlyBuzzPenaltyMs = o.optInt("earlyBuzzPenaltyMs", d.earlyBuzzPenaltyMs),
            deductIncorrect = o.optBoolean("deductIncorrect", d.deductIncorrect),
            rearmAfterIncorrect = o.optBoolean("rearmAfterIncorrect", d.rearmAfterIncorrect),
            buzzWindowMs = o.optInt("buzzWindowMs", d.buzzWindowMs),
            answerWindowMs = o.optInt("answerWindowMs", d.answerWindowMs),
            readMsPerChar = o.optInt("readMsPerChar", d.readMsPerChar),
            readMinMs = o.optInt("readMinMs", d.readMinMs),
            lowestScoreStartsRound = o.optBoolean("lowestScoreStartsRound", d.lowestScoreStartsRound),
            revealCategories = o.optBoolean("revealCategories", d.revealCategories),
            hideResponses = o.optBoolean("hideResponses", d.hideResponses),
            soundVolume = o.optDouble("soundVolume", d.soundVolume),
        )
    }

    fun boards(array: JSONArray): List<BoardSummary> = (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val id = o.str("id") ?: return@mapNotNull null
        BoardSummary(id, o.optString("name"), o.optInt("roundCount"))
    }

    fun event(p: JSONObject) = GameEvent(p.optString("kind"), p.str("playerId"))

    private fun player(o: JSONObject) = Player(
        id = o.optString("id"),
        name = o.optString("name"),
        score = o.optInt("score"),
        connected = o.optBoolean("connected"),
        hasPhone = o.optBoolean("hasPhone"),
        buttonId = if (o.has("buttonId") && !o.isNull("buttonId")) o.optInt("buttonId") else null,
        draining = o.optBoolean("draining"),
        color = o.str("color"),
        ink = o.str("ink"),
    )

    private fun round(o: JSONObject): Round {
        val tiles = o.optJSONArray("tiles")
        return Round(
            index = o.optInt("index"),
            count = o.optInt("count", 1),
            categories = o.objects("categories").map {
                Category(it.optString("title"), it.optString("description"), it.optBoolean("visible"), it.optBoolean("revealed"))
            },
            values = o.optJSONArray("values")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
            tiles = tiles?.let { cols ->
                (0 until cols.length()).map { c ->
                    val rows = cols.optJSONArray(c) ?: JSONArray()
                    (0 until rows.length()).map { r ->
                        val t = rows.optJSONObject(r) ?: JSONObject()
                        Tile(
                            state = when (t.optString("state")) {
                                "used" -> TileState.USED
                                "empty" -> TileState.EMPTY
                                else -> TileState.AVAILABLE
                            },
                            dailyDouble = t.optBoolean("dailyDouble"),
                        )
                    }
                }
            } ?: emptyList(),
        )
    }

    private fun clue(o: JSONObject) = ActiveClue(
        col = o.optInt("col"),
        row = o.optInt("row"),
        value = o.optInt("value"),
        category = o.optString("category"),
        dailyDouble = o.optBoolean("dailyDouble"),
        mode = BuzzerMode.from(o.optString("mode")),
        clue = o.optJSONObject("clue")?.let(::side),
        response = o.optJSONObject("response")?.let(::side),
        responder = o.str("responder"),
        lockedOut = o.optJSONArray("lockedOut")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        wagerer = o.str("wagerer"),
        wager = o.optInt("wager"),
        wagerSet = o.optBoolean("wagerSet"),
        minWager = if (o.has("minWager")) o.optInt("minWager") else null,
        maxWager = if (o.has("maxWager")) o.optInt("maxWager") else null,
        result = o.str("result"),
    )

    private fun side(o: JSONObject) = Side(
        isImage = o.optString("type") == "image",
        text = o.optString("text"),
        imageUrl = o.optString("imageUrl"),
        topCaption = o.optString("topCaption"),
        bottomCaption = o.optString("bottomCaption"),
    )

    /** Go marshals time.Time as RFC 3339 with up to nine fractional digits. */
    private fun time(s: String): Long = try {
        OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (_: Exception) {
        0L
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}

// --- Commands the host sends ---

object HostCommands {
    private fun msg(type: String, payload: JSONObject = JSONObject()) = GameMessage("jeopardy-$type", payload)

    fun hello() = msg("hello", JSONObject().put("role", "host"))
    fun loadBoard(boardId: String) = msg("load-board", JSONObject().put("boardId", boardId))
    fun setControl(playerId: String?) = msg("set-control", JSONObject().put("playerId", playerId ?: ""))
    fun startGame() = msg("start-game")
    fun next() = msg("next")
    fun selectClue(col: Int, row: Int) = msg("select-clue", JSONObject().put("col", col).put("row", row))
    fun endRound() = msg("end-round")
    fun arm() = msg("arm")
    fun selectResponder(playerId: String) = msg("select-responder", JSONObject().put("playerId", playerId))
    fun judge(correct: Boolean) = msg("judge", JSONObject().put("correct", correct))
    fun wager(amount: Int) = msg("dd-wager", JSONObject().put("amount", amount))
    fun trueDailyDouble() = msg("dd-wager", JSONObject().put("trueDailyDouble", true))
    fun reveal() = msg("reveal")
    fun cancelClue() = msg("cancel-clue")
    fun continueToBoard() = msg("continue")
    fun nextRound() = msg("next-round")
    fun endGame() = msg("end-game")
    fun resetGame() = msg("reset-game")
    fun undo() = msg("undo")
    fun updateSettings(settings: Settings) = msg("update-settings", settings.toJson())
    fun addPlayer(name: String) = msg("add-player", JSONObject().put("name", name))
    fun renamePlayer(playerId: String, name: String) = msg("rename-player", JSONObject().put("playerId", playerId).put("name", name))
    fun adjustScore(playerId: String, delta: Int) = msg("adjust-score", JSONObject().put("playerId", playerId).put("delta", delta))
    fun setScore(playerId: String, score: Int) = msg("adjust-score", JSONObject().put("playerId", playerId).put("score", score))
    fun drainTick(playerId: String) =
        msg("adjust-score", JSONObject().put("playerId", playerId).put("delta", -DRAIN_AMOUNT).put("drain", true))
    fun assignButton(playerId: String?) = msg("assign-button", JSONObject().put("playerId", playerId ?: ""))
    fun clearButton(playerId: String) = msg("clear-button", JSONObject().put("playerId", playerId))
    fun movePlayer(playerId: String, index: Int) = msg("move-player", JSONObject().put("playerId", playerId).put("index", index))
    fun removePlayer(playerId: String) = msg("remove-player", JSONObject().put("playerId", playerId))

    /** Hold-to-drain takes this much... */
    const val DRAIN_AMOUNT = 5
    /** ...this often, as in the web host's DrainButton. */
    const val DRAIN_INTERVAL_MS = 250L
}
