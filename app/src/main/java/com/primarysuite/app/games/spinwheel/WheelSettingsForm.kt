package com.primarysuite.app.games.spinwheel

import java.math.BigDecimal
import java.util.Locale
import kotlin.math.roundToInt

/** The settings fields that can hold a bad value. */
enum class WheelField { SPIN_MIN, SPIN_MAX, CELEBRATE }

/** Why a field is not acceptable. */
enum class WheelFieldProblem { NOT_A_NUMBER, OUT_OF_RANGE, MAX_BELOW_MIN }

/**
 * The settings as the host edits them: the times are text in seconds, so
 * half-typed values are fine. The web admin keeps the same draft and sends it on Save.
 */
data class WheelSettingsForm(
    val spinMin: String,
    val spinMax: String,
    val celebrate: String,
    val volume: Double,
) {
    /** The first problem of each bad field. Empty means the form can be saved. */
    fun problems(): Map<WheelField, WheelFieldProblem> {
        val out = linkedMapOf<WheelField, WheelFieldProblem>()
        fun seconds(field: WheelField, text: String, range: IntRange): Int? {
            val ms = parseSecondsToMs(text)
            when {
                ms == null -> out[field] = WheelFieldProblem.NOT_A_NUMBER
                ms !in range -> out[field] = WheelFieldProblem.OUT_OF_RANGE
            }
            return ms?.takeIf { it in range }
        }
        val min = seconds(WheelField.SPIN_MIN, spinMin, SpinWheelProtocol.SPIN_MS)
        val max = seconds(WheelField.SPIN_MAX, spinMax, SpinWheelProtocol.SPIN_MS)
        if (min != null && max != null && max < min) out[WheelField.SPIN_MAX] = WheelFieldProblem.MAX_BELOW_MIN
        seconds(WheelField.CELEBRATE, celebrate, SpinWheelProtocol.CELEBRATE_MS)
        return out
    }

    val isValid: Boolean get() = problems().isEmpty()

    /** The settings to send, or null while any field is bad. */
    fun toSettings(): WheelSettings? {
        if (!isValid) return null
        return WheelSettings(
            spinMinMs = parseSecondsToMs(spinMin)!!,
            spinMaxMs = parseSecondsToMs(spinMax)!!,
            celebrateMs = parseSecondsToMs(celebrate)!!,
            volume = volume,
        )
    }

    companion object {
        fun from(s: WheelSettings) = WheelSettingsForm(
            spinMin = formatSeconds(s.spinMinMs),
            spinMax = formatSeconds(s.spinMaxMs),
            celebrate = formatSeconds(s.celebrateMs),
            volume = s.volume,
        )

        /** Milliseconds as seconds without trailing zeros: 8000 is "8", 7500 is "7.5". */
        fun formatSeconds(ms: Int): String = BigDecimal(ms).movePointLeft(3).stripTrailingZeros().toPlainString()

        /** "1.5" to 1500, rounded like the web's `Math.round(x * 1000)`. Null if it isn't a number. */
        fun parseSecondsToMs(text: String): Int? {
            val v = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
            if (kotlin.math.abs(v) > 1_000_000) return null
            return (v * 1000).roundToInt()
        }

        /** Whether [text] is something a seconds field should accept while typing. */
        fun isNumberDraft(text: String): Boolean = text.all { it.isDigit() || it == '.' } && text.count { it == '.' } <= 1

        fun volumeLabel(v: Double): String = String.format(Locale.US, "%d%%", (v * 100).roundToInt())
    }
}
