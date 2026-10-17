package com.primarysuite.app.core.net

import com.primarysuite.app.core.protocol.GameMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Runs WebSocketManager against a real local server. Each test takes well under a second. */
class WebSocketManagerTest {

    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var path = "/ws/lucky-tap"
    private var wifiUp = true
    private lateinit var manager: WebSocketManager

    /** Server side of the most recent connection. */
    private val serverSockets = LinkedBlockingQueue<WebSocket>()
    private val received = LinkedBlockingQueue<String>()

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSockets.put(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received.put(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }
    }

    private fun upgrade() = MockResponse().withWebSocketUpgrade(serverListener)

    @Before
    fun setUp() {
        server.start()
        manager = WebSocketManager(
            scope = scope,
            urlProvider = { server.url(path).toString().replaceFirst("http", "ws") },
            canConnect = { wifiUp },
            retryPolicy = RetryPolicy(baseDelayMs = 10, maxDelayMs = 20, maxRetries = 2),
        )
    }

    @After
    fun tearDown() {
        manager.shutdown()
        scope.cancel()
        server.shutdown()
    }

    private fun awaitState(state: WebSocketConnectionState) = runBlocking {
        withTimeout(5_000) { manager.connectionState.first { it == state } }
    }

    @Test
    fun `connects and sends a game message`() {
        server.enqueue(upgrade())

        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)
        val sent = manager.send(GameMessage("nfc-check-in", JSONObject().put("number", 42)))

        assertTrue(sent)
        val frame = JSONObject(received.poll(5, TimeUnit.SECONDS)!!)
        assertEquals("nfc-check-in", frame.getString("type"))
        assertEquals(42, frame.getJSONObject("payload").getInt("number"))
        assertEquals("/ws/lucky-tap", server.takeRequest().path)
    }

    @Test
    fun `emits messages from the server and skips malformed ones`() = runBlocking {
        server.enqueue(upgrade())
        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { manager.events.filterIsInstance<SocketEvent.Received>().first() }
        }

        val socket = serverSockets.poll(5, TimeUnit.SECONDS)!!
        socket.send("not json")
        socket.send("""{"type":"checked-in-cards-update","payload":{"checkedInNumbers":[3,9]}}""")

        val message = first.await().message
        assertEquals("checked-in-cards-update", message.type)
        assertEquals(2, message.payload.getJSONArray("checkedInNumbers").length())
    }

    @Test
    fun `does nothing while wifi is down`() {
        wifiUp = false

        manager.connect()
        val sent = manager.send(GameMessage("card-scanned"))

        assertFalse(sent)
        assertEquals(WebSocketConnectionState.DISCONNECTED, manager.connectionState.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `connecting again to the same url is a no-op`() {
        server.enqueue(upgrade())

        manager.connect()
        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)
        manager.connect()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `switching url replaces the socket and ignores the old one closing`() {
        server.enqueue(upgrade())
        server.enqueue(upgrade())
        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)

        path = "/ws/jeopardy"
        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)
        runBlocking { delay(200) } // let the old socket's close callbacks arrive

        assertEquals(WebSocketConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals("/ws/lucky-tap", server.takeRequest().path)
        assertEquals("/ws/jeopardy", server.takeRequest().path)
    }

    @Test
    fun `reconnects when the server closes the socket`() {
        server.enqueue(upgrade())
        server.enqueue(upgrade())
        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)

        serverSockets.poll(5, TimeUnit.SECONDS)!!.close(1001, "server restarting")

        // A new server-side socket opens once the client has reconnected.
        serverSockets.poll(5, TimeUnit.SECONDS)!!
        awaitState(WebSocketConnectionState.CONNECTED)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `disconnect is immediate and does not retry`() {
        server.enqueue(upgrade())
        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)

        manager.disconnect()

        assertEquals(WebSocketConnectionState.DISCONNECTED, manager.connectionState.value)
        runBlocking { delay(100) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retries after a failure and recovers`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(upgrade())

        manager.connect()

        awaitState(WebSocketConnectionState.CONNECTED)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `gives up after the retry limit`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
        val gaveUp = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { manager.events.filterIsInstance<SocketEvent.GaveUp>().first() }
        }

        manager.connect()

        assertEquals(2, gaveUp.await().attempts)
        assertEquals(3, server.requestCount) // first try plus two retries
        assertEquals(WebSocketConnectionState.DISCONNECTED, manager.connectionState.value)
    }

    @Test
    fun `announces every connect, including reconnects`() = runBlocking {
        server.enqueue(upgrade())
        server.enqueue(upgrade())
        val connects = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { manager.events.filterIsInstance<SocketEvent.Connected>().take(2).toList() }
        }

        manager.connect()
        awaitState(WebSocketConnectionState.CONNECTED)
        serverSockets.poll(5, TimeUnit.SECONDS)!!.close(1001, "server restarting")

        assertEquals(2, connects.await().size)
    }
}
