package com.primarysuite.app.catalog

/** A Simple Games game the suite can administer. [slug] is its `/ws/{slug}` channel. */
data class Game(
    val slug: String,
    val name: String,
    val blurb: String,
    val hasTools: Boolean,
)

/**
 * Every game the Simple Games server hosts. Hardcoded until roles need a
 * server-side list. Set [Game.hasTools] once a game's screen exists.
 */
object GameCatalog {
    const val LUCKY_TAP = "lucky-tap"
    const val JEOPARDY = "jeopardy"

    val games: List<Game> = listOf(
        Game(LUCKY_TAP, "Lucky Tap", "NFC check-in, check-out and draws", hasTools = true),
        Game(JEOPARDY, "Jeopardy!", "Host the game: board, buzzers and scores", hasTools = true),
        Game("state-switch", "State Switch", "Game type, timeout and move limit", hasTools = false),
        Game("reel-rush", "Reel Rush", "Rounds and scoring", hasTools = false),
        Game("block-stacker", "Block Stacker", "Speed and levels", hasTools = false),
        Game("button-debug", "Button Debugger", "Test the GPIO buttons and LEDs", hasTools = false),
    )

    fun find(slug: String): Game? = games.firstOrNull { it.slug == slug }
}
