package com.primarysuite.app.games.luckytap

import com.primarysuite.app.core.protocol.GameMessage
import org.json.JSONArray
import org.json.JSONObject

/** How the last draw ended. */
sealed interface DrawResult {
    data class Winners(val numbers: List<Int>) : DrawResult
    data class Failed(val message: String) : DrawResult
}

/**
 * The game as the server last described it, built from `/ws/lucky-tap` messages.
 *
 * @property synced true once a full state has arrived on this connection.
 * @property weights each checked-in card's weight. Cards start at [INITIAL_WEIGHT];
 *   each win lowers it by [WIN_REDUCTION].
 * @property roundScans how many times each card was scanned this round. A card
 *   scanned twice has twice the chance in the next draw.
 * @property drawing true while the display animates a draw.
 */
data class LuckyTapGameState(
    val synced: Boolean = false,
    val checkedIn: List<Int> = emptyList(),
    val weights: Map<Int, Int> = emptyMap(),
    val roundScans: Map<Int, Int> = emptyMap(),
    val numWinners: Int = 1,
    val drawing: Boolean = false,
    val lastDraw: DrawResult? = null,
) {
    val scansThisRound: Int get() = roundScans.values.sum()

    /** A card's odds relative to a fresh card, in percent (100 = full odds). */
    fun oddsPercent(card: Int): Int = (weights[card] ?: INITIAL_WEIGHT) * 100 / INITIAL_WEIGHT

    companion object {
        // Mirrors InitialWeightLuckyTap and DefaultReductionAmountLuckyTap in simple-games
        // backend/cmd/lucky_tap.go. The server doesn't resend weights after a draw.
        const val INITIAL_WEIGHT = 100
        const val WIN_REDUCTION = 25
    }
}

/**
 * Applies one server message. Unknown types and malformed payloads leave the
 * state unchanged rather than failing: the server is the source of truth and
 * the next full state corrects any drift.
 */
fun LuckyTapGameState.reduce(message: GameMessage): LuckyTapGameState {
    val p = message.payload
    return when (message.type) {
        "lucky-tap-full-state" -> copy(
            synced = true,
            checkedIn = p.ints("checkedInNumbers").sorted(),
            weights = p.optJSONObject("likelihoods")?.weights() ?: emptyMap(),
            roundScans = p.ints("currentCollection").groupingBy { it }.eachCount(),
            numWinners = p.optInt("numWinners", numWinners).takeIf { it > 0 } ?: numWinners,
        )

        "checked-in-cards-update" -> {
            val cards = p.ints("checkedInNumbers").sorted()
            val set = cards.toSet()
            copy(
                checkedIn = cards,
                weights = cards.associateWith { weights[it] ?: LuckyTapGameState.INITIAL_WEIGHT },
                roundScans = roundScans.filterKeys { it in set },
            )
        }

        // The server auto-checks-in an unknown card when it's scanned.
        "card-activated" -> p.optInt("number", 0).takeIf { it > 0 }?.let { activate(listOf(it)) } ?: this
        "cards-activated-batch" -> activate(p.ints("numbers"))
        "current-collection-cleared" -> copy(roundScans = emptyMap())
        "start-tumbler-animation" -> copy(drawing = true)

        "selected-number-final" -> {
            val winners = p.ints("numbers")
            copy(
                drawing = false,
                lastDraw = DrawResult.Winners(winners),
                roundScans = emptyMap(),
                weights = weights.mapValues { (card, w) ->
                    if (card in winners) (w - LuckyTapGameState.WIN_REDUCTION).coerceAtLeast(0) else w
                },
            )
        }

        "draw-error" -> copy(drawing = false, lastDraw = DrawResult.Failed(p.optString("message", "Draw failed")))
        else -> this
    }
}

private fun LuckyTapGameState.activate(cards: List<Int>): LuckyTapGameState {
    if (cards.isEmpty()) return this
    val scans = roundScans.toMutableMap()
    cards.forEach { scans[it] = (scans[it] ?: 0) + 1 }
    val newCards = cards.filter { it !in checkedIn }.distinct()
    return copy(
        roundScans = scans,
        checkedIn = if (newCards.isEmpty()) checkedIn else (checkedIn + newCards).sorted(),
        weights = weights + newCards.associateWith { LuckyTapGameState.INITIAL_WEIGHT },
    )
}

private fun JSONObject.ints(key: String): List<Int> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { i -> (array.opt(i) as? Number)?.toInt() }
}

private fun JSONObject.weights(): Map<Int, Int> =
    keys().asSequence().mapNotNull { key ->
        val card = key.toIntOrNull() ?: return@mapNotNull null
        val weight = (opt(key) as? Number)?.toInt() ?: return@mapNotNull null
        card to weight
    }.toMap()
