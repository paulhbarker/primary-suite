package com.primarysuite.app.core.net

import android.util.Log
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.config.serverConfig
import com.primarysuite.app.core.protocol.GameMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * OkHttp WebSocket with retry and backoff. [urlProvider] is read on every
 * (re)connect, so settings changes take effect on the next connect.
 *
 * Callbacks from a socket that has since been replaced or closed are ignored,
 * so an old connection can't overwrite the state of the current one. If the
 * server drops or closes the connection, it reconnects with backoff.
 */
class WebSocketManager(
    private val scope: CoroutineScope,
    private val urlProvider: () -> String,
    private val canConnect: () -> Boolean = { true },
    private val client: OkHttpClient = defaultClient(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) : GameConnection {

    companion object {
        private const val TAG = "WebSocketManager"
        private const val NORMAL_CLOSURE = 1000
        private const val GOING_AWAY = 1001

        /**
         * How often OkHttp pings the server. A missing pong fails the socket, so a
         * connection that died silently (phone asleep, out of range) is noticed
         * within about two intervals and the retry starts. The web notices within 15 s.
         */
        const val PING_INTERVAL_SECONDS = 10L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    private val _connectionState = MutableStateFlow(WebSocketConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    // Large enough that a burst of messages (a noisy button sends many edges) isn't dropped by tryEmit.
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 1024)
    override val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    @Volatile private var webSocket: WebSocket? = null
    // The URL as requested. OkHttp reports ws:// URLs as http://, so compare against this.
    @Volatile private var webSocketUrl: String? = null
    private var retryJob: Job? = null
    @Volatile private var retryCount = 0

    override fun connect() {
        retryJob?.cancel()

        if (!canConnect()) {
            Log.d(TAG, "connect() blocked: prerequisite not met (e.g. WiFi not connected).")
            return
        }

        val url = urlProvider()
        val current = webSocket
        val state = _connectionState.value
        if (current != null && webSocketUrl == url &&
            (state == WebSocketConnectionState.CONNECTING || state == WebSocketConnectionState.CONNECTED)
        ) {
            Log.d(TAG, "Already connected or connecting to $url.")
            return
        }

        if (current != null) {
            Log.d(TAG, "Replacing socket for ${current.request().url} with $url.")
            webSocket = null
            current.close(GOING_AWAY, "Reconnecting")
        }

        _connectionState.value = WebSocketConnectionState.CONNECTING
        Log.d(TAG, "Connecting to: $url")
        webSocketUrl = url
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    override fun send(message: GameMessage): Boolean {
        if (_connectionState.value == WebSocketConnectionState.DISCONNECTED) {
            if (!canConnect()) {
                Log.d(TAG, "send() skipped: prerequisite not met.")
                return false
            }
            connect()
        }

        val text = message.encode()
        val sent = webSocket?.send(text) ?: false
        if (sent) {
            Log.d(TAG, "Sent: $text")
        } else {
            Log.w(TAG, "Failed to send: $text (state=${_connectionState.value})")
            if (canConnect()) connect()
        }
        return sent
    }

    override fun disconnect() {
        retryJob?.cancel()
        retryCount = 0
        val current = webSocket
        webSocket = null
        current?.close(NORMAL_CLOSURE, "Disconnect requested")
        _connectionState.value = WebSocketConnectionState.DISCONNECTED
    }

    override fun shutdown() {
        disconnect()
        client.dispatcher.executorService.shutdown()
        Log.d(TAG, "WebSocketManager shut down.")
    }

    private fun scheduleRetry() {
        retryJob?.cancel()

        if (retryPolicy.maxRetries != RetryPolicy.UNLIMITED && retryCount >= retryPolicy.maxRetries) {
            Log.w(TAG, "Max WebSocket retries (${retryPolicy.maxRetries}) reached. Giving up.")
            _events.tryEmit(SocketEvent.GaveUp(retryCount))
            retryCount = 0
            return
        }

        val delayMs = retryPolicy.delayFor(retryCount)
        if (retryCount < Int.MAX_VALUE) retryCount++
        Log.d(TAG, "Scheduling WebSocket retry #$retryCount in ${delayMs}ms")
        retryJob = scope.launch {
            delay(delayMs)
            if (!canConnect()) {
                Log.d(TAG, "Retry skipped: prerequisite not met. Resetting retry count.")
                retryCount = 0
            } else if (_connectionState.value == WebSocketConnectionState.DISCONNECTED) {
                connect()
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        private fun WebSocket.isCurrent() = this === this@WebSocketManager.webSocket

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!webSocket.isCurrent()) return
            Log.i(TAG, "Connected to ${webSocket.request().url}")
            retryJob?.cancel()
            retryCount = 0
            _connectionState.value = WebSocketConnectionState.CONNECTED
            _events.tryEmit(SocketEvent.Connected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!webSocket.isCurrent()) return
            val message = GameMessage.decode(text)
            if (message == null) {
                Log.w(TAG, "Ignoring malformed message: $text")
                return
            }
            _events.tryEmit(SocketEvent.Received(message))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            // Set CLOSING before answering: answering can run onClosed on another
            // thread right away, and its DISCONNECTED must not be overwritten.
            if (webSocket.isCurrent()) {
                Log.d(TAG, "Closing: $code / $reason")
                _connectionState.value = WebSocketConnectionState.CLOSING
            }
            // Complete the close handshake so onClosed follows.
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // disconnect() and connect() clear webSocket before closing, so a close
            // that reaches here came from the server (e.g. a restart): reconnect.
            if (!webSocket.isCurrent()) return
            Log.d(TAG, "Closed by server: $code / $reason")
            this@WebSocketManager.webSocket = null
            _connectionState.value = WebSocketConnectionState.DISCONNECTED
            scheduleRetry()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!webSocket.isCurrent()) return
            Log.e(TAG, "Error for ${webSocket.request().url}: ${t.message}")
            this@WebSocketManager.webSocket = null
            _connectionState.value = WebSocketConnectionState.DISCONNECTED
            scheduleRetry()
        }
    }
}

/** A connection to the `/ws/[slug]` channel at the server address in [settings]. */
fun gameSocket(
    slug: String,
    settings: ConnectionSettings,
    scope: CoroutineScope,
    canConnect: () -> Boolean,
): GameConnection = WebSocketManager(
    scope = scope,
    urlProvider = { settings.serverConfig.webSocketUrl(slug) },
    canConnect = canConnect,
)
