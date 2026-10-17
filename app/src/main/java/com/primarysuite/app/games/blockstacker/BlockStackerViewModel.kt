package com.primarysuite.app.games.blockstacker

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

/** One line of the received-messages log. */
data class LogEntry(val timeMillis: Long, val type: String, val payload: String)

/**
 * What the Block Stacker screen shows. [draft] is what the sliders show and what
 * Save sends. [server] is the last settings the server sent, null until then.
 */
data class BlockStackerUiState(
    val connected: Boolean = false,
    val draft: BlockStackerSettings = BlockStackerSettings.DEFAULTS,
    val server: BlockStackerSettings? = null,
    val state: BlockStackerState? = null,
    /** Newest first, at most [BlockStackerViewModel.LOG_SIZE]. */
    val log: List<LogEntry> = emptyList(),
) {
    /** The draft has edits the server doesn't have yet. */
    val dirty: Boolean get() = server != null && draft != server
}

/**
 * Runs the Block Stacker admin over `/ws/block-stacker`, like the web admin.
 * The server sends the full state on connect and broadcasts after every save.
 * Edits stay in a local draft until [save].
 */
class BlockStackerViewModel(
    wifi: WifiConnector,
    socketFactory: GameSocketFactory,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    companion object {
        private const val TAG = "BlockStacker"
        const val GAME_SLUG = "block-stacker"
        const val LOG_SIZE = 10
    }

    private val wifiConnected: StateFlow<Boolean> = wifi.connectionState
        .map { it == WifiConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, wifi.connectionState.value == WifiConnectionState.CONNECTED)

    private val socket: GameConnection = socketFactory(viewModelScope) { wifiConnected.value }

    private val _ui = MutableStateFlow(BlockStackerUiState())
    val ui: StateFlow<BlockStackerUiState> = _ui.asStateFlow()

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
                    SocketEvent.Connected -> Unit // the server sends the full state unasked
                    is SocketEvent.Received -> onMessage(event.message)
                    is SocketEvent.GaveUp -> _errors.tryEmit("Lost the connection to the game server.")
                }
            }
        }
    }

    private fun onMessage(message: GameMessage) {
        val entry = LogEntry(clock(), message.type, message.payload.toString())
        _ui.update { it.copy(log = (listOf(entry) + it.log).take(LOG_SIZE)) }
        when (message.type) {
            BlockStackerProtocol.GAME_STATE -> {
                val full = BlockStackerFullState.parse(message.payload)
                _ui.update {
                    // Follow the server, but never throw away edits the user hasn't saved.
                    val follow = it.server == null || it.draft == it.server
                    it.copy(
                        server = full.settings,
                        state = full.state,
                        draft = if (follow) full.settings else it.draft,
                    )
                }
            }
            else -> Log.d(TAG, "Ignoring ${message.type}")
        }
    }

    /** Changes the local draft. Nothing is sent until [save]. */
    fun edit(change: (BlockStackerSettings) -> BlockStackerSettings) {
        _ui.update { it.copy(draft = change(it.draft)) }
    }

    /** Puts the defaults into the draft, as the web's Reset to Defaults does. Nothing is sent. */
    fun resetToDefaults() {
        _ui.update { it.copy(draft = BlockStackerSettings.DEFAULTS) }
    }

    /** Sends the draft. The server's broadcast then confirms it. */
    fun save() {
        send(BlockStackerCommands.updateSettings(_ui.value.draft))
    }

    /** Drops a block on the display, like the web's Test Button Press. */
    fun testPress() {
        send(BlockStackerCommands.press())
    }

    private fun send(message: GameMessage): Boolean =
        socket.send(message).also { ok -> if (!ok) _errors.tryEmit("Not connected to the game server.") }

    override fun onCleared() {
        super.onCleared()
        socket.shutdown()
    }
}
