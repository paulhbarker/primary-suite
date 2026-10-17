package com.primarysuite.app.catalog

import com.primarysuite.app.core.config.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameCatalogTest {

    @Test
    fun `lists every game the server hosts, Lucky Tap first`() {
        assertEquals(
            listOf("lucky-tap", "jeopardy", "state-switch", "reel-rush", "block-stacker", "button-debug"),
            GameCatalog.games.map { it.slug },
        )
    }

    @Test
    fun `slugs are unique and valid channel names`() {
        val slugs = GameCatalog.games.map { it.slug }
        assertEquals(slugs.size, slugs.toSet().size)
        val server = ServerConfig("10.0.0.1", "8080")
        slugs.forEach { assertEquals("ws://10.0.0.1:8080/ws/$it", server.webSocketUrl(it)) }
    }

    @Test
    fun `games with tools so far`() {
        assertEquals(listOf(GameCatalog.LUCKY_TAP, GameCatalog.JEOPARDY, GameCatalog.STATE_SWITCH, GameCatalog.REEL_RUSH, GameCatalog.BLOCK_STACKER), GameCatalog.games.filter { it.hasTools }.map { it.slug })
    }

    @Test
    fun `finds games by slug`() {
        assertEquals("Jeopardy!", GameCatalog.find("jeopardy")?.name)
        assertNull(GameCatalog.find("spin-the-wheel"))
    }
}
