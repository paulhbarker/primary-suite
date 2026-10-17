package com.primarysuite.app.games.blockstacker

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * The Block Stacker protocol, from `backend/cmd/block_stacker.go` and the web
 * admin (`frontend/src/games/block-stacker/BlockStackerAdmin.tsx`).
 */
object BlockStackerProtocol {
    const val GAME_STATE = "block-stacker-game-state"
    const val UPDATE_SETTINGS = "block-stacker-update-settings"
    const val BUTTON_PRESS = "button-press"

    /** Slider limits, the same as the web's number inputs. */
    const val LEVELS_MIN = 1
    const val LEVELS_MAX = 50
    const val INITIAL_SPEED_MIN = 0.1
    const val INITIAL_SPEED_MAX = 10.0
    const val SPEED_INCREMENT_MIN = 0.0
    const val SPEED_INCREMENT_MAX = 5.0
}

/** The game's settings. Speeds are doubles so a tenth like 1.7 is sent as exactly 1.7. */
data class BlockStackerSettings(
    val maxLevels: Int,
    val initialSpeed: Double,
    val speedIncrement: Double,
    val gameEnabled: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("maxLevels", maxLevels)
        .put("initialSpeed", initialSpeed)
        .put("speedIncrement", speedIncrement)
        .put("gameEnabled", gameEnabled)

    companion object {
        /** The web admin's "Reset to Defaults", and the server's own defaults. */
        val DEFAULTS = BlockStackerSettings(maxLevels = 15, initialSpeed = 1.7, speedIncrement = 0.8, gameEnabled = true)

        fun parse(p: JSONObject?) = BlockStackerSettings(
            maxLevels = p?.optInt("maxLevels", DEFAULTS.maxLevels) ?: DEFAULTS.maxLevels,
            initialSpeed = p?.optDouble("initialSpeed", DEFAULTS.initialSpeed) ?: DEFAULTS.initialSpeed,
            speedIncrement = p?.optDouble("speedIncrement", DEFAULTS.speedIncrement) ?: DEFAULTS.speedIncrement,
            gameEnabled = p?.optBoolean("gameEnabled", DEFAULTS.gameEnabled) ?: DEFAULTS.gameEnabled,
        )

        /** Snaps a slider value to a tenth, so 1.7000001 becomes 1.7. */
        fun roundToTenth(value: Double): Double = (value * 10).roundToInt() / 10.0
    }
}

/** The runtime part of the server's state. [lastUpdated] is null if missing or unreadable. */
data class BlockStackerState(
    val status: String = "",
    val totalGamesPlayed: Int = 0,
    val highScore: Int = 0,
    val lastUpdated: Instant? = null,
) {
    companion object {
        fun parse(p: JSONObject?) = BlockStackerState(
            status = p?.optString("status", "") ?: "",
            totalGamesPlayed = p?.optInt("totalGamesPlayed", 0) ?: 0,
            highScore = p?.optInt("highScore", 0) ?: 0,
            lastUpdated = p?.optString("lastUpdated", "")?.let(::parseTime),
        )

        /** Go sends RFC 3339 with nanoseconds and an offset. */
        private fun parseTime(text: String): Instant? =
            runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
    }
}

/** The payload of `block-stacker-game-state`. */
data class BlockStackerFullState(val settings: BlockStackerSettings, val state: BlockStackerState) {
    companion object {
        fun parse(p: JSONObject) = BlockStackerFullState(
            BlockStackerSettings.parse(p.optJSONObject("settings")),
            BlockStackerState.parse(p.optJSONObject("state")),
        )
    }
}

/** Messages the admin sends. */
object BlockStackerCommands {
    fun updateSettings(s: BlockStackerSettings) = GameMessage(BlockStackerProtocol.UPDATE_SETTINGS, s.toJson())

    /** A press, as the web's Test Button Press sends it: `{id: 1}`. */
    fun press() = GameMessage(BlockStackerProtocol.BUTTON_PRESS, JSONObject().put("id", 1))
}
