package com.primarysuite.app.games.buttondebug

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Button Debugger protocol, from `backend/cmd/button_debug.go`, `buttonbox.go`
 * and the web page (`frontend/src/games/button-debug/ButtonDebug.tsx`, `analysis.ts`).
 */
object ButtonDebugProtocol {
    const val SNAPSHOT = "button-debug-snapshot"
    const val STATUS = "button-debug-status"
    const val EDGE = "button-debug-edge"
    const val PRESS = "button-debug-press"
    const val PONG = "button-debug-pong"

    const val PING = "button-debug-ping"
    const val LED_MODE = "button-debug-led-mode"
    const val CHASE = "button-debug-chase"
    const val ENABLE_ALL = "button-debug-enable-all"
    const val DEBOUNCE = "button-debug-debounce"
    const val RESET = "button-debug-reset"

    /** The debounce window the server accepts, in ms. */
    const val DEBOUNCE_MAX_MS = 500.0

    /** The web pings every 2 s. */
    const val PING_EVERY_MS = 2_000L

    /** Debounce presets on the web page, in ms. */
    val DEBOUNCE_PRESETS = listOf(5, 10, 20, 30, 50)
}

/** Edge kinds and verdicts, as in `analysis.ts`. Kept as raw strings so an unknown one still shows. */
object EdgeKind {
    const val EDGE = "edge"
    const val SETTLE = "settle"
    const val POLL = "poll"
}

object Verdict {
    const val PRESS = "press"
    const val PENDING = "pending"
    const val BOUNCE = "bounce"
    const val DISABLED = "disabled"
    const val LATE_PRESS = "late-press"
    const val RELEASE = "release"
    const val HOLDING = "holding"
    const val BLIP = "blip"
    const val LOST_PRESS = "lost-press"
    const val LOST_RELEASE = "lost-release"
}

/** The LED test modes, with the web's labels and hints. */
enum class LedMode(val mode: String) {
    GAME("game"),
    FOLLOW("follow"),
    ON("on"),
    OFF("off"),
}

/** One thing the debouncer saw (`EdgeEvent` in `analysis.ts`). */
data class EdgeEvent(
    /** Index in the box. */
    val button: Int,
    val id: Int,
    val pin: Int,
    val kind: String,
    /** Edge: the direction the kernel claimed. Settle and poll: the line level read (true = low). */
    val pressed: Boolean,
    val tsUs: Long,
    /** Since the previous edge on this line; -1 for none. */
    val gapUs: Long,
    /** Edge: kernel timestamp to handler; -1 if unknown. */
    val lagUs: Long,
    val kernelGap: Long,
    /** Settle and poll: since the burst's first edge. */
    val burstUs: Long?,
    val verdict: String,
    /** The button-press sequence number, when a press was sent; 0 for none. */
    val seq: Long,
) {
    companion object {
        /** Null when the payload has no `button`, since the edge can't be attributed. */
        fun parse(p: JSONObject): EdgeEvent? {
            if (!p.has("button")) return null
            return EdgeEvent(
                button = p.optInt("button"),
                id = p.optInt("id"),
                pin = p.optInt("pin"),
                kind = p.optString("kind", ""),
                pressed = p.optBoolean("pressed", false),
                tsUs = p.optLong("tsUs"),
                gapUs = p.optLong("gapUs", -1),
                lagUs = p.optLong("lagUs", -1),
                kernelGap = p.optLong("kernelGap", 0),
                burstUs = if (p.has("burstUs") && !p.isNull("burstUs")) p.optLong("burstUs") else null,
                verdict = p.optString("verdict", ""),
                seq = p.optLong("seq", 0),
            )
        }
    }
}

/** A button-press as the games received it (`DebugPress`). */
data class DebugPress(
    val id: Int,
    val seq: Long,
    /** "box" or "wifi". */
    val source: String,
    val remote: String?,
    /** Box only: kernel edge to the hub handing the press to the games; -1 if unknown. */
    val hubUs: Long,
    val rssi: Int,
    val sentMs: Long,
    val freeHeap: Long,
    /** Server wall clock, ms. */
    val atMs: Long,
) {
    val fromBox: Boolean get() = source == SOURCE_BOX

    companion object {
        const val SOURCE_BOX = "box"
        const val SOURCE_WIFI = "wifi"

        fun parse(p: JSONObject) = DebugPress(
            id = p.optInt("id"),
            seq = p.optLong("seq", 0),
            source = p.optString("source", ""),
            remote = p.optString("remote", "").ifEmpty { null },
            hubUs = p.optLong("hubUs", -1),
            rssi = p.optInt("rssi", 0),
            sentMs = p.optLong("sentMs", 0),
            freeHeap = p.optLong("freeHeap", 0),
            atMs = p.optLong("atMs", 0),
        )
    }
}

/** One button on the box (`DebugButton`). [index] is its position, [id] what the games see. */
data class DebugButton(val index: Int, val id: Int, val switchPin: Int, val ledPin: Int) {
    companion object {
        fun parse(p: JSONObject) = DebugButton(p.optInt("index"), p.optInt("id"), p.optInt("switchPin"), p.optInt("ledPin"))
    }
}

/**
 * The box's state (`DebugStatus`). The arrays are by box index; a missing array
 * or a missing entry reads as false, except [enabled], which reads as true.
 */
data class DebugStatus(
    /** Debounced state. */
    val down: List<Boolean>,
    /** The line read just now: true = low (pressed). */
    val levels: List<Boolean>,
    val levelErr: String?,
    val enabled: List<Boolean>,
    val leds: List<Boolean>,
    val ledMode: String,
    val quietMs: Double,
    val stuck: List<Boolean>,
    val activeGames: List<String>,
    val droppedEdges: Long,
) {
    fun isDown(i: Int) = down.getOrElse(i) { false }
    fun isLow(i: Int) = levels.getOrElse(i) { false }
    fun isEnabled(i: Int) = enabled.getOrElse(i) { true }
    fun isLed(i: Int) = leds.getOrElse(i) { false }
    fun isStuck(i: Int) = stuck.getOrElse(i) { false }

    companion object {
        fun parse(p: JSONObject) = DebugStatus(
            down = bools(p.optJSONArray("down")),
            levels = bools(p.optJSONArray("levels")),
            levelErr = p.optString("levelErr", "").ifEmpty { null },
            enabled = bools(p.optJSONArray("enabled")),
            leds = bools(p.optJSONArray("leds")),
            ledMode = p.optString("ledMode", ""),
            quietMs = p.optDouble("quietMs", 0.0).let { if (it.isNaN()) 0.0 else it },
            stuck = bools(p.optJSONArray("stuck")),
            activeGames = strings(p.optJSONArray("activeGames")),
            droppedEdges = p.optLong("droppedEdges", 0),
        )

        private fun bools(a: JSONArray?): List<Boolean> = if (a == null) emptyList() else List(a.length()) { a.optBoolean(it, false) }
        private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.optString(it, "") }
    }
}

/** What the server sends on connect and after a reset (`DebugSnapshot`). */
data class DebugSnapshot(
    val box: Boolean,
    val boxError: String?,
    val chip: String?,
    val buttons: List<DebugButton>,
    val status: DebugStatus?,
    val events: List<EdgeEvent>,
    val presses: List<DebugPress>,
) {
    companion object {
        fun parse(p: JSONObject): DebugSnapshot {
            val buttons = p.optJSONArray("buttons")?.objects()?.map(DebugButton::parse) ?: emptyList()
            return DebugSnapshot(
                box = p.optBoolean("box", false),
                boxError = p.optString("boxError", "").ifEmpty { null },
                chip = p.optString("chip", "").ifEmpty { null },
                buttons = buttons,
                status = p.optJSONObject("status")?.let(DebugStatus::parse),
                events = p.optJSONArray("events")?.objects()?.mapNotNull(EdgeEvent::parse) ?: emptyList(),
                presses = p.optJSONArray("presses")?.objects()?.map(DebugPress::parse) ?: emptyList(),
            )
        }

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

/** Messages the debugger sends. */
object ButtonDebugCommands {
    /** [t] is the sender's clock in ms; the server echoes it back. */
    fun ping(t: Double) = GameMessage(ButtonDebugProtocol.PING, JSONObject().put("t", t))

    fun ledMode(mode: LedMode) = GameMessage(ButtonDebugProtocol.LED_MODE, JSONObject().put("mode", mode.mode))
    fun chase() = GameMessage(ButtonDebugProtocol.CHASE)
    fun enableAll() = GameMessage(ButtonDebugProtocol.ENABLE_ALL)
    fun debounce(ms: Double) = GameMessage(ButtonDebugProtocol.DEBOUNCE, JSONObject().put("ms", ms))
    fun reset() = GameMessage(ButtonDebugProtocol.RESET)
}

/**
 * Reads the debounce field. Like the web's submit handler, anything that isn't a
 * number from 0 to 500 is rejected (null).
 */
fun parseDebounce(text: String): Double? {
    val ms = text.trim().toDoubleOrNull() ?: return null
    return ms.takeIf { it.isFinite() && it >= 0 && it <= ButtonDebugProtocol.DEBOUNCE_MAX_MS }
}
