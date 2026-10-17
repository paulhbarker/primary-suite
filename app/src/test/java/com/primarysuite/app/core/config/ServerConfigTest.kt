package com.primarysuite.app.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    private val config = ServerConfig(host = "10.0.0.1", port = "8080")

    @Test
    fun `builds a websocket url per game`() {
        assertEquals("ws://10.0.0.1:8080/ws/lucky-tap", config.webSocketUrl("lucky-tap"))
        assertEquals("ws://10.0.0.1:8080/ws/jeopardy", config.webSocketUrl("jeopardy"))
    }

    @Test
    fun `builds the http url used for reachability checks`() {
        assertEquals("http://10.0.0.1:8080/", config.httpUrl)
    }

    @Test
    fun `rejects slugs that would change the path`() {
        listOf("", "Lucky", "lucky tap", "../admin", "a/b", "-x", "x-").forEach { slug ->
            val failed = runCatching { config.webSocketUrl(slug) }.isFailure
            assertTrue("expected '$slug' to be rejected", failed)
        }
    }

    @Test
    fun `accepts ports 1 to 65535`() {
        listOf("1", "80", "8080", "65535", " 8080 ").forEach { assertTrue(it, ServerConfig.isValidPort(it)) }
        listOf("", "0", "-1", "65536", "80a", "8.0").forEach { assertFalse(it, ServerConfig.isValidPort(it)) }
    }
}
