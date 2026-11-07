package com.primarysuite.app.games.spinwheel

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Spin the Wheel screen shows. [state] is null until the server has sent the wheel.
 * [clockOffsetMs] is the phone's clock minus the server's when the state arrived; add it to
 * a server time to get the phone's (the Pi's clock can be days off).
 */
data class SpinWheelUiState(
    val connected: Boolean = false,
    val state: WheelState? = null,
    val clockOffsetMs: Long = 0,
)

/**
 * Runs the Spin the Wheel admin over `/ws/spin-the-wheel`, like the web admin. The server
 * treats a new socket as a display, so the admin says hello on every connect; the server
 * answers with the whole wheel and broadcasts it after every change.
 */
class SpinWheelViewModel(
    wifi: WifiConnector,
    private val now: () -> Long = System::currentTimeMillis,
    socketFactory: GameSocketFactory,
) : ViewModel() {

    companion object {
        private const val TAG = "SpinWheel"
        const val GAME_SLUG = "spin-the-wheel"
    }

    private val wifiConnected: StateFlow<Boolean> = wifi.connectionState
        .map { it == WifiConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, wifi.connectionState.value == WifiConnectionState.CONNECTED)

    private val socket: GameConnection = socketFactory(viewModelScope) { wifiConnected.value }

    private val _ui = MutableStateFlow(SpinWheelUiState())
    val ui: StateFlow<SpinWheelUiState> = _ui.asStateFlow()

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
            socket.events.collect { event ->
                when (event) {
                    // Every connection starts as a display. Say hello as admin, on reconnects too.
                    SocketEvent.Connected -> socket.send(SpinWheelCommands.hello())
                    is SocketEvent.Received -> onMessage(event.message)
                    is SocketEvent.GaveUp -> _errors.tryEmit("Lost the connection to the game server.")
                }
            }
        }
    }

    private fun onMessage(message: GameMessage) {
        when (message.type) {
            SpinWheelProtocol.STATE -> {
                val state = WheelState.parse(message.payload)
                val offset = if (state.serverTime > 0) now() - state.serverTime else 0L
                _ui.update { it.copy(state = state, clockOffsetMs = offset) }
            }
            SpinWheelProtocol.ERROR -> {
                val text = message.payload.optString("message")
                _errors.tryEmit(text.ifBlank { "The game server refused that." })
            }
            else -> Log.d(TAG, "Ignoring ${message.type}")
        }
    }

    fun spin() = send(SpinWheelCommands.spin())

    fun setLocked(locked: Boolean) = send(SpinWheelCommands.setLocked(locked))

    /** Rigs the next spin; null clears it. */
    fun forceNext(value: Int?) = send(SpinWheelCommands.forceNext(value))

    /** Sets one value's weight, sending all ten as the web does. Ignored if it would be out of range or leave none above 0. */
    fun setWeight(value: Int, weight: Int) {
        val next = _ui.value.state?.weightsWith(value, weight) ?: return
        send(SpinWheelCommands.setWeights(next))
    }

    /** Every value weighted 1. */
    fun evenOdds() {
        val n = _ui.value.state?.values?.size ?: return
        send(SpinWheelCommands.setWeights(List(n) { 1 }))
    }

    /** Sends the full settings. The screen follows the server's state broadcast, not this call. */
    fun saveSettings(settings: WheelSettings) = send(SpinWheelCommands.setSettings(settings))

    fun clearHistory() = send(SpinWheelCommands.clearHistory())

    private fun send(message: GameMessage): Boolean =
        socket.send(message).also { ok -> if (!ok) _errors.tryEmit("Not connected to the game server.") }

    override fun onCleared() {
        super.onCleared()
        socket.shutdown()
    }
}
