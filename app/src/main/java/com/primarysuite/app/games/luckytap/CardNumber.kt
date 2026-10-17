package com.primarysuite.app.games.luckytap

/** Lucky Tap cards carry a number from 1 to 100, written as an NDEF text record. */
object CardNumber {
    val RANGE = 1..100

    /** The card number in [text], or null if it isn't a whole number in [RANGE]. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in RANGE }

    fun isValid(text: String): Boolean = parse(text) != null
}
