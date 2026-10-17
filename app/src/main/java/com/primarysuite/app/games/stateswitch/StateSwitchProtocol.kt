package com.primarysuite.app.games.stateswitch

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * The State Switch protocol, from `backend/cmd/state_switch.go` and the web
 * admin (`frontend/src/games/state-switch/StateSwitchAdmin.tsx`).
 */
object StateSwitchProtocol {
    const val SETTINGS = "state-switch-game-settings"
    const val STATE = "state-switch-game-state"
    const val BUTTON_STATE = "button-state"
    const val RESET = "state-switch-reset"
    const val RESET_ACTUATIONS = "state-switch-reset-actuations"
    const val BUTTON_PRESS = "button-press"

    /** Button timeout slider, in seconds: 0 to 9 in steps of 0.1, as on the web. */
    const val TIMEOUT_MAX = 9f
    const val TIMEOUT_STEP = 0.1f

    /** Button limit slider: 0 to 20 in steps of 2, as on the web. */
    const val LIMIT_MAX = 20
    const val LIMIT_STEP = 2
}

/** The pairs the display can show, in the web admin's order. Labels match the web. */
enum class GameType(val slug: String, val label: String) {
    FAST_SLOW("fast-slow", "Fast / Slow"),
    GO_STOP("go-stop", "Stop / Go"),
    LOUD_SOFT("loud-soft", "Loud / Soft"),
    LEFT_RIGHT("left-right", "Left / Right"),
    STACCATO_LEGATO("staccato-legato", "Staccato / Legato"),
    CLAPPING_MARCHING("clapping-marching", "Clapping / Marching"),
    STANDING_SITTING("standing-sitting", "Standing / Sitting"),
    SINGING_HUMMING("singing-humming", "Singing / Humming"),
    RANDOM("random", "Random"),
    ;

    companion object {
        fun of(slug: String): GameType? = entries.firstOrNull { it.slug == slug }
    }
}

/**
 * The game's settings. [game] stays the server's raw slug, so an unknown type
 * survives a round trip instead of being replaced.
 */
data class StateSwitchSettings(
    val game: String,
    val timeout: Float,
    val limitEnabled: Boolean,
    val limit: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("game", game)
        // Via the float's own decimal text, so 2.3f goes out as 2.3, not 2.299999952316284.
        .put("timeout", timeout.toString().toDouble())
        .put("limitEnabled", limitEnabled)
        .put("limit", limit)

    companion object {
        /**
         * What the web admin's Reset All leaves behind. The server resets to a 0 s
         * timeout, but the web then sends its own defaults, so the timeout ends at 2.5 s.
         */
        val WEB_DEFAULTS = StateSwitchSettings(GameType.FAST_SLOW.slug, 2.5f, limitEnabled = false, limit = 20)

        fun parse(p: JSONObject) = StateSwitchSettings(
            game = p.optString("game", GameType.FAST_SLOW.slug),
            timeout = roundTimeout(p.optDouble("timeout", 0.0).toFloat()),
            limitEnabled = p.optBoolean("limitEnabled", false),
            limit = p.optInt("limit", 20),
        )

        /** Snaps to the slider's 0.1 s steps, so 2.3000001 is sent as 2.3. */
        fun roundTimeout(seconds: Float): Float =
            ((seconds.coerceIn(0f, StateSwitchProtocol.TIMEOUT_MAX)) * 10).roundToInt() / 10f

        /** Snaps to the slider's steps of 2. */
        fun roundLimit(value: Float): Int =
            ((value / StateSwitchProtocol.LIMIT_STEP).roundToInt() * StateSwitchProtocol.LIMIT_STEP)
                .coerceIn(0, StateSwitchProtocol.LIMIT_MAX)
    }
}

/** Presses so far, and which side of the pair is showing (0 or 1). */
data class StateSwitchState(val actuations: Int = 0, val current: Int = 0) {
    companion object {
        fun parse(p: JSONObject) = StateSwitchState(p.optInt("actuations", 0), p.optInt("current", 0))
    }
}

/** Messages the admin sends. */
object StateSwitchCommands {
    fun settings(s: StateSwitchSettings) = GameMessage(StateSwitchProtocol.SETTINGS, s.toJson())
    fun reset() = GameMessage(StateSwitchProtocol.RESET)
    fun resetActuations() = GameMessage(StateSwitchProtocol.RESET_ACTUATIONS)

    /** Switches the display as the physical button would, within the same timeout and limit. */
    fun press() = GameMessage(StateSwitchProtocol.BUTTON_PRESS)
}
