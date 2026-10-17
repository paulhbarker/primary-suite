package com.primarysuite.app.core.net

import com.primarysuite.app.core.protocol.GameMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class WebSocketConnectionState { DISCONNECTED, CONNECTING, CONNECTED, CLOSING }

sealed interface SocketEvent {
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

/** Exponential backoff: base, 2x, 4x... capped at [maxDelayMs], up to [maxRetries] attempts. */
data class RetryPolicy(
    val baseDelayMs: Long = 1_000L,
    val maxDelayMs: Long = 30_000L,
    val maxRetries: Int = 10,
) {
    /** Delay before retry number [attempt], counting from 0. */
    fun delayFor(attempt: Int): Long =
        (baseDelayMs * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(maxDelayMs)
}
