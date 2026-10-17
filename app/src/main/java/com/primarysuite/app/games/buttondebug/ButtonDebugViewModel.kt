package com.primarysuite.app.games.buttondebug

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.primarysuite.app.core.net.GameConnection
import com.primarysuite.app.core.net.GameSocketFactory
import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.core.wifi.WifiConnector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Button Debugger screen shows. */
data class ButtonDebugUiState(
    val connected: Boolean = false,
    /** Round trip to the server in ms, from the last pong. */
    val rttMs: Double? = null,
    /** Colour by button id, defaults plus the user's picks. */
    val colors: Map<Int, ButtonColor> = ButtonColor.DEFAULTS,
    val session: DebugSession = DebugSession(),
) {
    /** Shown only while connected, as on the web. */
    val showRtt: Boolean get() = rttMs != null && connected
}

/**
 * Runs the Button Debugger over `/ws/button-debug`, like the web page. The server
 * sends a snapshot on connect and streams edges, presses and status after that.
 *
 * Socket messages are handled on [workDispatcher], not the main thread, so a burst
 * of edges from a noisy line isn't held up (the socket's event buffer is small).
 * [clockMs] is the clock the round trip is measured with: ms, monotonic.
 */
class ButtonDebugViewModel(
    wifi: WifiConnector,
    socketFactory: GameSocketFactory,
    private val colorStore: ButtonColorStore,
    private val clockMs: () -> Double = { System.nanoTime() / 1_000_000.0 },
    workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    companion object {
        private const val TAG = "ButtonDebug"
        const val GAME_SLUG = "button-debug"
    }

    private val wifiConnected: StateFlow<Boolean> = wifi.connectionState
        .map { it == WifiConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, wifi.connectionState.value == WifiConnectionState.CONNECTED)

    private val socket: GameConnection = socketFactory(viewModelScope) { wifiConnected.value }

    private val _ui = MutableStateFlow(
        ButtonDebugUiState(colors = ButtonColor.DEFAULTS + colorStore.load()),
    )
    val ui: StateFlow<ButtonDebugUiState> = _ui.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    init {
        viewModelScope.launch {
            wifiConnected.collect { up -> if (up) socket.connect() }
        }
        viewModelScope.launch {
            socket.connectionState.collect { ws ->
                _ui.update { it.copy(connected = ws == WebSocketConnectionState.CONNECTED) }
            }
        }
        viewModelScope.launch {
            // Ping on connect and every two seconds while connected, to measure the round trip.
            socket.connectionState.map { it == WebSocketConnectionState.CONNECTED }.collectLatest { up ->
                while (up) {
                    socket.send(ButtonDebugCommands.ping(clockMs()))
                    delay(ButtonDebugProtocol.PING_EVERY_MS)
                }
            }
        }
        viewModelScope.launch(workDispatcher) {
            socket.events.collect { event ->
                when (event) {
                    SocketEvent.Connected -> Unit // the server sends the snapshot unasked
                    is SocketEvent.Received -> onMessage(event.message)
                    is SocketEvent.GaveUp -> _errors.tryEmit("Lost the connection to the game server.")
                }
            }
        }
    }

    private fun onMessage(message: GameMessage) {
        val p = message.payload
        when (message.type) {
            ButtonDebugProtocol.SNAPSHOT -> {
                val snapshot = DebugSnapshot.parse(p)
                _ui.update { it.copy(session = it.session.onSnapshot(snapshot)) }
            }
            ButtonDebugProtocol.STATUS -> {
                val status = DebugStatus.parse(p)
                _ui.update { it.copy(session = it.session.onStatus(status)) }
            }
            ButtonDebugProtocol.EDGE -> {
                val ev = EdgeEvent.parse(p) ?: return
                _ui.update { it.copy(session = it.session.onEdge(ev, live = true)) }
            }
            ButtonDebugProtocol.PRESS -> {
                val press = DebugPress.parse(p)
                _ui.update { it.copy(session = it.session.onPress(press)) }
            }
            ButtonDebugProtocol.PONG -> {
                // Timed on arrival.
                val sent = p.optDouble("t", Double.NaN)
                if (!sent.isNaN()) {
                    val rtt = clockMs() - sent
                    _ui.update { it.copy(rttMs = rtt) }
                }
            }
            else -> Log.d(TAG, "Ignoring ${message.type}")
        }
    }

    /** An LED test mode: Games control, Follow press, All on, All off. */
    fun setLedMode(mode: LedMode) {
        send(ButtonDebugCommands.ledMode(mode))
    }

    /** Lights each LED in turn. */
    fun chase() {
        send(ButtonDebugCommands.chase())
    }

    /** Re-enables every button a game disabled. */
    fun enableAll() {
        send(ButtonDebugCommands.enableAll())
    }

    /** Sets the debounce window. Out-of-range values are ignored, as the server would. */
    fun setDebounce(ms: Double) {
        if (ms.isNaN() || ms < 0 || ms > ButtonDebugProtocol.DEBOUNCE_MAX_MS) return
        send(ButtonDebugCommands.debounce(ms))
    }

    /** Clears the server's history for every open debugger; the new snapshot comes back. */
    fun resetStats() {
        send(ButtonDebugCommands.reset())
    }

    /** Remembers which colour button [id] is. */
    fun setColor(id: Int, color: ButtonColor) {
        val next = _ui.value.colors + (id to color)
        _ui.update { it.copy(colors = next) }
        colorStore.save(next)
    }

    private fun send(message: GameMessage): Boolean =
        socket.send(message).also { ok -> if (!ok) _errors.tryEmit("Not connected to the game server.") }

    override fun onCleared() {
        super.onCleared()
        socket.shutdown()
    }
}
