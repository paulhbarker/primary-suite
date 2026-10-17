package com.primarysuite.app.games.stateswitch

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

/** What the State Switch screen shows. [settings] is null until the server sends them. */
data class StateSwitchUiState(
    val connected: Boolean = false,
    val settings: StateSwitchSettings? = null,
    val state: StateSwitchState = StateSwitchState(),
    /** The server's button cooldown is running. */
    val cooldown: Boolean = false,
) {
    /** The limit is on and every press has been used. */
    val limitReached: Boolean
        get() = settings?.limitEnabled == true && state.actuations >= settings.limit

    val canSwitch: Boolean get() = connected && settings != null && !cooldown && !limitReached
}

/**
 * Runs the State Switch admin over `/ws/state-switch`, like the web admin. The
 * server sends the state and settings on connect and broadcasts every change,
 * so the screen always follows the server, including changes from another admin.
 */
class StateSwitchViewModel(
    wifi: WifiConnector,
    socketFactory: GameSocketFactory,
) : ViewModel() {

    companion object {
        private const val TAG = "StateSwitch"
        const val GAME_SLUG = "state-switch"
    }

    private val wifiConnected: StateFlow<Boolean> = wifi.connectionState
        .map { it == WifiConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, wifi.connectionState.value == WifiConnectionState.CONNECTED)

    private val socket: GameConnection = socketFactory(viewModelScope) { wifiConnected.value }

    private val _ui = MutableStateFlow(StateSwitchUiState())
    val ui: StateFlow<StateSwitchUiState> = _ui.asStateFlow()

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
                    SocketEvent.Connected -> Unit // the server sends state and settings unasked
                    is SocketEvent.Received -> onMessage(event.message)
                    is SocketEvent.GaveUp -> _errors.tryEmit("Lost the connection to the game server.")
                }
            }
        }
    }

    private fun onMessage(message: GameMessage) {
        val p = message.payload
        when (message.type) {
            StateSwitchProtocol.SETTINGS -> _ui.update { it.copy(settings = StateSwitchSettings.parse(p)) }
            StateSwitchProtocol.STATE -> _ui.update { it.copy(state = StateSwitchState.parse(p)) }
            StateSwitchProtocol.BUTTON_STATE -> _ui.update { it.copy(cooldown = p.optBoolean("disabled", false)) }
            else -> Log.d(TAG, "Ignoring ${message.type}")
        }
    }

    /** Sends new settings and shows them at once; the server's broadcast confirms them. */
    fun updateSettings(change: (StateSwitchSettings) -> StateSwitchSettings) {
        val current = _ui.value.settings ?: return
        val next = change(current)
        if (next == current) return
        if (send(StateSwitchCommands.settings(next))) _ui.update { it.copy(settings = next) }
    }

    /** Switches the display, like the physical button. */
    fun switch() {
        if (_ui.value.canSwitch) send(StateSwitchCommands.press())
    }

    fun resetActuations() {
        send(StateSwitchCommands.resetActuations())
    }

    /** Like the web's Reset All: the server resets, then the web's defaults are sent. */
    fun resetAll() {
        if (send(StateSwitchCommands.reset())) {
            send(StateSwitchCommands.settings(StateSwitchSettings.WEB_DEFAULTS))
            _ui.update { it.copy(settings = StateSwitchSettings.WEB_DEFAULTS) }
        }
    }

    private fun send(message: GameMessage): Boolean =
        socket.send(message).also { ok -> if (!ok) _errors.tryEmit("Not connected to the game server.") }

    override fun onCleared() {
        super.onCleared()
        socket.shutdown()
    }
}
