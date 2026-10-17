package com.primarysuite.app.games.buttondebug

import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject

/** The button colours of the web page's palette. */
enum class ButtonColor(val key: String, val label: String, val bg: Long, val fg: Long, val edge: Long) {
    GREEN("green", "Green", 0xFF16A34A, 0xFFFFFFFF, 0xFF15803D),
    YELLOW("yellow", "Yellow", 0xFFFACC15, 0xFF1C1917, 0xFFCA8A04),
    WHITE("white", "White", 0xFFF8FAFC, 0xFF1C1917, 0xFF94A3B8),
    BLUE("blue", "Blue", 0xFF2563EB, 0xFFFFFFFF, 0xFF1D4ED8),
    RED("red", "Red", 0xFFDC2626, 0xFFFFFFFF, 0xFFB91C1C),
    ;

    companion object {
        fun of(key: String): ButtonColor? = entries.firstOrNull { it.key == key }

        /** On the box, left to right: blue (id 4), white (3), yellow (2), green (1). */
        val DEFAULTS: Map<Int, ButtonColor> = mapOf(1 to GREEN, 2 to YELLOW, 3 to WHITE, 4 to BLUE)

        /** Tiles are laid out in the same left-to-right order as the box. */
        val BOX_ORDER = listOf(BLUE, WHITE, YELLOW, GREEN, RED)
    }
}

/**
 * Where the per-button colour picks are remembered (the web uses localStorage).
 * Only picks need saving: [ButtonDebugViewModel] puts them over [ButtonColor.DEFAULTS].
 */
interface ButtonColorStore {
    /** The saved picks by button id; empty when there are none. */
    fun load(): Map<Int, ButtonColor>

    /** Saves the picks, replacing the old ones. */
    fun save(colors: Map<Int, ButtonColor>)
}

/**
 * Backs the store with SharedPreferences, in the web's format: a JSON object of
 * button id to colour key, under the web's key (bumped there whenever the box's
 * id mapping changes, so stale picks don't stick).
 */
class PrefsButtonColorStore(private val prefs: SharedPreferences) : ButtonColorStore {
    override fun load(): Map<Int, ButtonColor> {
        val text = prefs.getString(KEY, null) ?: return emptyMap()
        return try {
            val json = JSONObject(text)
            buildMap {
                for (name in json.keys()) {
                    val id = name.toIntOrNull() ?: continue
                    val color = ButtonColor.of(json.optString(name)) ?: continue
                    put(id, color)
                }
            }
        } catch (_: JSONException) {
            emptyMap()
        }
    }

    override fun save(colors: Map<Int, ButtonColor>) {
        val json = JSONObject()
        colors.forEach { (id, color) -> json.put(id.toString(), color.key) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    companion object {
        const val KEY = "button-debug-colors-v3"
    }
}

/** The colour shown for [id]: its pick, or white when it has none (the web's fallback). */
fun Map<Int, ButtonColor>.colorFor(id: Int): ButtonColor = this[id] ?: ButtonColor.WHITE

/** Tiles in the box's left-to-right order, by colour, then by id. */
fun List<DebugButton>.sortedByBoxOrder(colors: Map<Int, ButtonColor>): List<DebugButton> =
    sortedWith(
        compareBy<DebugButton> { ButtonColor.BOX_ORDER.indexOf(colors[it.id] ?: ButtonColor.RED) }.thenBy { it.id },
    )
