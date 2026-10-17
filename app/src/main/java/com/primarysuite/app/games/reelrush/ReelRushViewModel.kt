package com.primarysuite.app.games.reelrush

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

/** What the Reel Rush screen shows. [state] is null until the server has sent the game. */
data class ReelRushUiState(
    val connected: Boolean = false,
    val state: ReelRushState? = null,
)

/**
 * Runs the Reel Rush admin over `/ws/reel-rush`, like the web admin. The server
 * treats a new socket as a display, so the admin says hello on every connect;
 * the server answers with the whole game and broadcasts it after every change.
 */
class ReelRushViewModel(
    wifi: WifiConnector,
    socketFactory: GameSocketFactory,
) : ViewModel() {

    companion object {
        private const val TAG = "ReelRush"
        const val GAME_SLUG = "reel-rush"
    }

    private val wifiConnected: StateFlow<Boolean> = wifi.connectionState
        .map { it == WifiConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, wifi.connectionState.value == WifiConnectionState.CONNECTED)

    private val socket: GameConnection = socketFactory(viewModelScope) { wifiConnected.value }

    private val _ui = MutableStateFlow(ReelRushUiState())
    val ui: StateFlow<ReelRushUiState> = _ui.asStateFlow()

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
                    SocketEvent.Connected -> socket.send(ReelRushCommands.hello())
                    is SocketEvent.Received -> onMessage(event.message)
                    is SocketEvent.GaveUp -> _errors.tryEmit("Lost the connection to the game server.")
                }
            }
        }
    }

    private fun onMessage(message: GameMessage) {
        when (message.type) {
            ReelRushProtocol.STATE -> _ui.update { it.copy(state = ReelRushState.parse(message.payload)) }
            ReelRushProtocol.ERROR -> {
                val text = message.payload.optString("message")
                _errors.tryEmit(text.ifBlank { "The game server refused that." })
            }
            else -> Log.d(TAG, "Ignoring ${message.type}")
        }
    }

    fun openLobby() = send(ReelRushCommands.openLobby())

    fun removePlayer(slot: Int) = send(ReelRushCommands.removePlayer(slot))

    fun startGame() = send(ReelRushCommands.startGame())

    fun closeRound() = send(ReelRushCommands.closeRound())

    fun nextRound() = send(ReelRushCommands.nextRound())

    fun replayRound() = send(ReelRushCommands.replayRound())

    fun endGame() = send(ReelRushCommands.endGame())

    fun reset() = send(ReelRushCommands.reset())

    /** Sends the full settings. The screen follows the server's state broadcast, not this call. */
    fun saveSettings(settings: ReelRushSettings) = send(ReelRushCommands.settings(settings))

    private fun send(message: GameMessage): Boolean =
        socket.send(message).also { ok -> if (!ok) _errors.tryEmit("Not connected to the game server.") }

    override fun onCleared() {
        super.onCleared()
        socket.shutdown()
    }
}
