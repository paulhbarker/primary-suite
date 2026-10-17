package com.primarysuite.app.games.reelrush

import java.math.BigDecimal
import java.util.Locale
import kotlin.math.roundToInt

/** The settings fields that can hold a bad value. */
enum class SettingsField { REELS, ROUNDS, NEAR_MISSES, SPIN_SPEED, DISPLAY_LAG, ROUND_MIN, ROUND_MAX, DWELL_MIN, DWELL_MAX }

/** Why a field is not acceptable. */
enum class FieldProblem { NOT_A_NUMBER, OUT_OF_RANGE, MAX_BELOW_MIN }

/**
 * The settings as the host edits them: numbers are text, so half-typed values
 * are fine. The web admin keeps the same draft and sends it on Save.
 */
data class SettingsForm(
    val reels: String,
    val rounds: String,
    val hardMode: Boolean,
    val assist: Boolean,
    val rowDecoys: Boolean,
    val roundMin: String,
    val roundMax: String,
    val nearMisses: String,
    val spinSpeed: String,
    val dwellMin: String,
    val dwellMax: String,
    val displayLag: String,
    val volume: Double,
    val names: List<String>,
) {
    /** The first problem of each bad field. Empty means the form can be saved. */
    fun problems(): Map<SettingsField, FieldProblem> {
        val out = linkedMapOf<SettingsField, FieldProblem>()
        fun int(field: SettingsField, text: String, range: IntRange): Int? {
            val v = text.trim().toIntOrNull()
            when {
                v == null -> out[field] = FieldProblem.NOT_A_NUMBER
                v !in range -> out[field] = FieldProblem.OUT_OF_RANGE
            }
            return v?.takeIf { it in range }
        }
        fun seconds(field: SettingsField, text: String, range: IntRange): Int? {
            val ms = parseSecondsToMs(text)
            when {
                ms == null -> out[field] = FieldProblem.NOT_A_NUMBER
                ms !in range -> out[field] = FieldProblem.OUT_OF_RANGE
            }
            return ms?.takeIf { it in range }
        }
        int(SettingsField.REELS, reels, ReelRushProtocol.REELS)
        int(SettingsField.ROUNDS, rounds, ReelRushProtocol.ROUNDS)
        int(SettingsField.NEAR_MISSES, nearMisses, ReelRushProtocol.NEAR_MISSES)
        int(SettingsField.DISPLAY_LAG, displayLag, ReelRushProtocol.DISPLAY_LAG_MS)
        val speed = spinSpeed.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        when {
            speed == null -> out[SettingsField.SPIN_SPEED] = FieldProblem.NOT_A_NUMBER
            speed < ReelRushProtocol.SPIN_SPEED_MIN || speed > ReelRushProtocol.SPIN_SPEED_MAX ->
                out[SettingsField.SPIN_SPEED] = FieldProblem.OUT_OF_RANGE
        }
        val rMin = seconds(SettingsField.ROUND_MIN, roundMin, ReelRushProtocol.ROUND_MS)
        val rMax = seconds(SettingsField.ROUND_MAX, roundMax, ReelRushProtocol.ROUND_MS)
        if (rMin != null && rMax != null && rMax < rMin) out[SettingsField.ROUND_MAX] = FieldProblem.MAX_BELOW_MIN
        val dMin = seconds(SettingsField.DWELL_MIN, dwellMin, ReelRushProtocol.DWELL_MS)
        val dMax = seconds(SettingsField.DWELL_MAX, dwellMax, ReelRushProtocol.DWELL_MS)
        if (dMin != null && dMax != null && dMax < dMin) out[SettingsField.DWELL_MAX] = FieldProblem.MAX_BELOW_MIN
        return out
    }

    val isValid: Boolean get() = problems().isEmpty()

    /** The settings to send, or null while any field is bad. */
    fun toSettings(): ReelRushSettings? {
        if (!isValid) return null
        return ReelRushSettings(
            reels = reels.trim().toInt(),
            rounds = rounds.trim().toInt(),
            hardMode = hardMode,
            assist = assist,
            roundMinMs = parseSecondsToMs(roundMin)!!,
            roundMaxMs = parseSecondsToMs(roundMax)!!,
            nearMisses = nearMisses.trim().toInt(),
            rowDecoys = rowDecoys,
            spinSpeed = spinSpeed.trim().toDouble(),
            dwellMinMs = parseSecondsToMs(dwellMin)!!,
            dwellMaxMs = parseSecondsToMs(dwellMax)!!,
            displayLagMs = displayLag.trim().toInt(),
            names = names,
            volume = volume,
        )
    }

    companion object {
        fun from(s: ReelRushSettings) = SettingsForm(
            reels = s.reels.toString(),
            rounds = s.rounds.toString(),
            hardMode = s.hardMode,
            assist = s.assist,
            rowDecoys = s.rowDecoys,
            roundMin = formatSeconds(s.roundMinMs),
            roundMax = formatSeconds(s.roundMaxMs),
            nearMisses = s.nearMisses.toString(),
            spinSpeed = BigDecimal.valueOf(s.spinSpeed).stripTrailingZeros().toPlainString(),
            dwellMin = formatSeconds(s.dwellMinMs),
            dwellMax = formatSeconds(s.dwellMaxMs),
            displayLag = s.displayLagMs.toString(),
            volume = s.volume,
            names = s.names,
        )

        /** Milliseconds as seconds without trailing zeros: 1000 is "1", 250 is "0.25". */
        fun formatSeconds(ms: Int): String =
            BigDecimal(ms).movePointLeft(3).stripTrailingZeros().toPlainString()

        /** "1.5" to 1500, rounded like the web's `Math.round(x * 1000)`. Null if it isn't a number. */
        fun parseSecondsToMs(text: String): Int? {
            val v = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
            if (kotlin.math.abs(v) > 1_000_000) return null
            return (v * 1000).roundToInt()
        }

        /** Whether [text] is something a number field should accept while typing. */
        fun isNumberDraft(text: String): Boolean = text.all { it.isDigit() || it == '.' } && text.count { it == '.' } <= 1

        fun volumeLabel(v: Double): String = String.format(Locale.US, "%d%%", (v * 100).roundToInt())
    }
}
