package com.primarysuite.app.core.net

import com.primarysuite.app.core.config.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpServerProbeTest {

    private val probe = HttpServerProbe()

    private fun MockWebServer.config() = ServerConfig(hostName, port.toString())

    @Test
    fun `any http answer, even 404, means reachable`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(200))

            assertTrue(probe.isReachable(server.config()))
            assertTrue(probe.isReachable(server.config()))
            assertEquals("HEAD", server.takeRequest().method)
        }
    }

    @Test
    fun `nothing listening means unreachable`() = runBlocking {
        val server = MockWebServer().apply { start() }
        val config = server.config()
        server.shutdown()

        assertFalse(probe.isReachable(config))
    }

    @Test
    fun `a malformed address is unreachable rather than a crash`() = runBlocking {
        assertFalse(probe.isReachable(ServerConfig("bad host", "80")))
        assertFalse(probe.isReachable(ServerConfig("10.0.0.1", "99999")))
    }
}
