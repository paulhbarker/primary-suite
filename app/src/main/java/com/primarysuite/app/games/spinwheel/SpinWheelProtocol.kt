package com.primarysuite.app.games.spinwheel

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * The Spin the Wheel protocol, from `backend/cmd/spinwheel/{messages,engine}.go`
 * and the web admin (`frontend/src/games/spin-the-wheel/admin/Admin.tsx`).
 */
object SpinWheelProtocol {
    const val HELLO = "hello"
    const val ROLE_ADMIN = "admin"
    const val STATE = "state"
    const val ERROR = "error"
    const val SPIN = "spin"
    const val SET_LOCKED = "set-locked"
    const val FORCE_NEXT = "force-next"
    const val SET_WEIGHTS = "set-weights"
    const val SET_SETTINGS = "set-settings"
    const val CLEAR_HISTORY = "clear-history"

    /** Allowed ranges, from `Settings.Validate` on the server. */
    val SPIN_MS = 3000..15000
    val CELEBRATE_MS = 1000..30000

    /** A value's weight: 0 to 10 (`maxWeight` on the server). */
    val WEIGHT = 0..10

    /** The web's volume slider: 0 to 1 in steps of 0.05. */
    const val VOLUME_STEP = 0.05f
}

enum class WheelPhase(val slug: String) {
    ARMED("armed"),
    SPINNING("spinning"),
    LANDED("landed"),
    ;

    companion object {
        /** An unknown phase from a newer server reads as armed. */
        fun of(slug: String): WheelPhase = entries.firstOrNull { it.slug == slug } ?: ARMED
    }
}

/** The wedge and digit colours of each value on the TV (`VALUE_COLORS` in the web's `wheel.ts`). */
data class ValueColor(val fill: Long, val text: Long)

object ValueColors {
    private val BY_VALUE = mapOf(
        100 to ValueColor(0xFFF5ECD0, 0xFF111111),
        200 to ValueColor(0xFFB3121F, 0xFFFFF3D6),
        300 to ValueColor(0xFF2FBF71, 0xFF111111),
        400 to ValueColor(0xFFF2B705, 0xFF111111),
        500 to ValueColor(0xFF1B1B1B, 0xFFF6C544),
        600 to ValueColor(0xFFFF6A3D, 0xFF111111),
        700 to ValueColor(0xFF0F6B4A, 0xFFFFF3D6),
        800 to ValueColor(0xFF7A1747, 0xFFFFF3D6),
        900 to ValueColor(0xFFFFE7A0, 0xFF111111),
        1000 to ValueColor(0xFF2B59C3, 0xFFFFF3D6),
    )

    fun of(value: Int): ValueColor = BY_VALUE[value] ?: ValueColor(0xFF888888, 0xFFFFFFFF)
}

/** The box buttons by id, as the web names them. 0 means the admin. */
enum class BoxButton(val id: Int, val rgb: Long) {
    GREEN(1, 0xFF3FD46A),
    YELLOW(2, 0xFFFFD23A),
    WHITE(3, 0xFFF4F4F4),
    BLUE(4, 0xFF3D8BFF),
    ;

    companion object {
        fun of(id: Int): BoxButton? = entries.firstOrNull { it.id == id }
    }
}

private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it) }

private inline fun <T> JSONArray?.objects(parse: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }.map(parse)

private fun JSONObject.intOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

/** The spin settings. They live in the server's memory only. */
data class WheelSettings(
    val spinMinMs: Int,
    val spinMaxMs: Int,
    val celebrateMs: Int,
    val volume: Double,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("spinMinMs", spinMinMs)
        .put("spinMaxMs", spinMaxMs)
        .put("celebrateMs", celebrateMs)
        .put("volume", volume)

    companion object {
        /** `DefaultSettings` on the server. */
        val DEFAULTS = WheelSettings(spinMinMs = 8000, spinMaxMs = 10000, celebrateMs = 10000, volume = 0.8)

        fun parse(p: JSONObject?): WheelSettings {
            val d = DEFAULTS
            if (p == null) return d
            return WheelSettings(
                spinMinMs = p.optInt("spinMinMs", d.spinMinMs),
                spinMaxMs = p.optInt("spinMaxMs", d.spinMaxMs),
                celebrateMs = p.optInt("celebrateMs", d.celebrateMs),
                volume = p.optDouble("volume", d.volume).takeIf { it.isFinite() } ?: d.volume,
            )
        }

        /** The slider's value on the web's 0.05 steps. */
        fun roundVolume(v: Float): Double = Math.round(v.coerceIn(0f, 1f) * 20) / 20.0
    }
}

/** A finished spin. [at] is the server's unix ms. [button] is the box button, 0 for the admin. */
data class WheelResult(
    val id: Int,
    val value: Int,
    val at: Long,
    val button: Int,
    val rigged: Boolean,
) {
    companion object {
        fun parse(p: JSONObject) = WheelResult(
            id = p.optInt("id"),
            value = p.optInt("value"),
            at = p.optDouble("at", 0.0).takeIf { it.isFinite() }?.roundToLong() ?: 0L,
            button = p.optInt("button"),
            rigged = p.optBoolean("rigged"),
        )
    }
}

/** The whole wheel, as the server sends it. The spin's animation fields are the TV's business. */
data class WheelState(
    val phase: WheelPhase,
    val locked: Boolean,
    val values: List<Int>,
    /** In [values] order. */
    val weights: List<Int>,
    val forcedNext: Int?,
    val settings: WheelSettings,
    /** The value being spun to or celebrated; null when armed. */
    val spinValue: Int?,
    val lastResult: WheelResult?,
    /** Newest first. */
    val history: List<WheelResult>,
    val serverTime: Long,
) {
    /** The values smallest first, each with its weight. */
    val odds: List<Pair<Int, Int>> get() = values.zip(weights).sortedBy { it.first }

    val totalWeight: Int get() = weights.sum()

    /** How many times each value has come up in [history]. */
    fun count(value: Int): Int = history.count { it.value == value }

    /** The ten weights with [value]'s set to [weight], or null if that's out of range or would leave none above 0. */
    fun weightsWith(value: Int, weight: Int): List<Int>? {
        val i = values.indexOf(value)
        if (i < 0 || weight !in SpinWheelProtocol.WEIGHT) return null
        val next = weights.toMutableList().also { it[i] = weight }
        return next.takeIf { w -> w.any { it > 0 } }
    }

    companion object {
        fun parse(p: JSONObject): WheelState = WheelState(
            phase = WheelPhase.of(p.optString("phase")),
            locked = p.optBoolean("locked"),
            values = p.optJSONArray("values").ints(),
            weights = p.optJSONArray("weights").ints(),
            forcedNext = p.intOrNull("forcedNext"),
            settings = WheelSettings.parse(p.optJSONObject("settings")),
            spinValue = p.optJSONObject("spin")?.intOrNull("value"),
            lastResult = p.optJSONObject("lastResult")?.let(WheelResult::parse),
            history = p.optJSONArray("history").objects(WheelResult::parse),
            serverTime = p.optDouble("serverTime", 0.0).takeIf { it.isFinite() }?.roundToLong() ?: 0L,
        )
    }
}

object SpinWheelCommands {
    fun hello() = GameMessage(SpinWheelProtocol.HELLO, JSONObject().put("role", SpinWheelProtocol.ROLE_ADMIN))
    fun spin() = GameMessage(SpinWheelProtocol.SPIN)
    fun setLocked(locked: Boolean) = GameMessage(SpinWheelProtocol.SET_LOCKED, JSONObject().put("locked", locked))

    /** Rigs the next spin to land on [value]; null clears it. */
    fun forceNext(value: Int?) = GameMessage(SpinWheelProtocol.FORCE_NEXT, JSONObject().put("value", value ?: JSONObject.NULL))
    fun setWeights(weights: List<Int>) = GameMessage(SpinWheelProtocol.SET_WEIGHTS, JSONObject().put("weights", JSONArray(weights)))
    fun setSettings(s: WheelSettings) = GameMessage(SpinWheelProtocol.SET_SETTINGS, s.toJson())
    fun clearHistory() = GameMessage(SpinWheelProtocol.CLEAR_HISTORY)
}
