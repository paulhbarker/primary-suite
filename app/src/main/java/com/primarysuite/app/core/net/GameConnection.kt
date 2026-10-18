package com.primarysuite.app.core.net

import com.primarysuite.app.core.protocol.GameMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class WebSocketConnectionState { DISCONNECTED, CONNECTING, CONNECTED, CLOSING }

sealed interface SocketEvent {
    /** The socket opened (first connect or a reconnect). Games that need a handshake send it now. */
    data object Connected : SocketEvent

    /** A message arrived from the server. */
    data class Received(val message: GameMessage) : SocketEvent

    /** Reconnecting failed [attempts] times in a row; the socket stopped retrying. */
    data class GaveUp(val attempts: Int) : SocketEvent
}

/**
 * A connection to one game's channel on the server. View models depend on this
 * interface so tests can swap in a fake.
 */
interface GameConnection {
    val connectionState: StateFlow<WebSocketConnectionState>
    val events: SharedFlow<SocketEvent>

    /** Opens the socket, or reconnects if the target URL changed. No-op if already open. */
    fun connect()

    /** Sends [message]. Returns false if it couldn't be queued. */
    fun send(message: GameMessage): Boolean

    /** Closes the socket and stops any pending retry. */
    fun disconnect()

    /** Disconnects and releases resources. The connection can't be reused afterwards. */
    fun shutdown()
}

/**
 * Reconnect backoff, matching the web's sockets (`frontend/src/websocketOptions.ts`):
 * 0.5, 1, 2 and 4 s, then every 5 s, and by default it never gives up, so a
 * phone that slept or walked out of range gets back in on its own. A finite
 * [maxRetries] makes the socket stop and emit [SocketEvent.GaveUp].
 */
data class RetryPolicy(
    val baseDelayMs: Long = 500L,
    val maxDelayMs: Long = 5_000L,
    val maxRetries: Int = UNLIMITED,
) {
    /** Delay before retry number [attempt], counting from 0. */
    fun delayFor(attempt: Int): Long =
        (baseDelayMs * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(maxDelayMs)

    companion object {
        const val UNLIMITED = Int.MAX_VALUE
    }
}

/** Opens a game's socket in [scope]. `canConnect` reports whether WiFi is up. */
typealias GameSocketFactory = (scope: CoroutineScope, canConnect: () -> Boolean) -> GameConnection
