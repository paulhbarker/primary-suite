package com.primarysuite.app.core.tv

import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.testing.FakeGameConnection
import com.primarysuite.app.testing.FakeWifiConnector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvLinkTest {

    private val wifi = FakeWifiConnector()
    private val socket = FakeGameConnection()

    private fun TestScope.link() = TvLink(backgroundScope, socket, "phone-1", wifi.connectionState)

    private suspend fun TestScope.open() {
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        socket.events.emit(SocketEvent.Connected)
        runCurrent()
    }

    private suspend fun TestScope.receive(game: String, leader: Boolean) {
        socket.events.emit(SocketEvent.Received(GameMessage("stage", JSONObject().put("game", game).put("leader", leader))))
        runCurrent()
    }

    @Test
    fun `connects once wifi is up and says hello with the phone id`() = runTest {
        val tv = link()
        tv.start()
        runCurrent()
        assertEquals(0, socket.connectCalls)

        open()
        assertEquals(1, socket.connectCalls)
        assertEquals(listOf(GameMessage("stage-hello", JSONObject().put("id", "phone-1"))), socket.sent)
        assertTrue(tv.state.value.connected)
    }

    @Test
    fun `only the leader moves the TV`() = runTest {
        val tv = link()
        tv.start()
        open()
        socket.sent.clear()

        receive("", leader = false)
        assertFalse(tv.show("jeopardy"))
        assertEquals(emptyList<GameMessage>(), socket.sent)

        receive("", leader = true)
        assertTrue(tv.state.value.leader)
        assertTrue(tv.show("jeopardy"))
        assertEquals(listOf(GameMessage("stage-set", JSONObject().put("game", "jeopardy"))), socket.sent)
    }

    @Test
    fun `losing the connection or stopping drops the lead until the server says again`() = runTest {
        val tv = link()
        tv.start()
        open()
        receive("reel-rush", leader = true)
        assertEquals("reel-rush", tv.state.value.game)

        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(tv.state.value.leader)
        assertFalse(tv.show("jeopardy"))

        open()
        receive("reel-rush", leader = true)
        tv.stop()
        assertFalse(tv.state.value.connected)
        assertFalse(tv.state.value.leader)
    }

    @Test
    fun `a phone that isn't leading can take over`() = runTest {
        val tv = link()
        tv.start()
        open()
        receive("", leader = true)
        socket.sent.clear()
        assertFalse("the leader has nothing to take over", tv.takeOver())

        receive("", leader = false)
        assertTrue(tv.takeOver())
        assertEquals(listOf(GameMessage("stage-take-over")), socket.sent)
    }

    @Test
    fun `can't take over while offline`() = runTest {
        val tv = link()
        tv.start()
        runCurrent()
        assertFalse(tv.takeOver())
        assertEquals(emptyList<GameMessage>(), socket.sent)
    }
}
