package com.primarysuite.app.games.jeopardy

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The lobby's board list. */
data class BoardsState(
    val loading: Boolean = false,
    val boards: List<BoardSummary>? = null,
    val error: String? = null,
)

/**
 * Hosts a Jeopardy! game over `/ws/jeopardy`, like the web host's useJeopardy:
 * says hello as host on every connect, keeps the latest host state, and sends
 * the host's commands. The server is authoritative; nothing is predicted here.
 */
class JeopardyHostViewModel(
    wifi: WifiConnector,
    socketFactory: GameSocketFactory,
    private val boardsApi: JeopardyBoardsApi,
    /** Called when a player buzzes in, so the host feels it. */
    private val onBuzz: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    companion object {
        private const val TAG = "JeopardyHost"
        const val GAME_SLUG = "jeopardy"
    }

    private val wifiConnected: StateFlow<Boolean> = wifi.connectionState
        .map { it == WifiConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, wifi.connectionState.value == WifiConnectionState.CONNECTED)

    private val socket: GameConnection = socketFactory(viewModelScope) { wifiConnected.value }

    /** The server has welcomed this connection as the host. States before that are the public view. */
    private val welcomed = MutableStateFlow(false)

    private val _state = MutableStateFlow<HostState?>(null)
    val state: StateFlow<HostState?> = _state.asStateFlow()

    /** `serverTime - localTime` from the latest state, for countdowns. */
    @Volatile var clockOffsetMs: Long = 0
        private set

    /** Socket open and welcomed as host. */
    val connected: StateFlow<Boolean> = combine(socket.connectionState, welcomed) { ws, ok ->
        ws == WebSocketConnectionState.CONNECTED && ok
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Server rejections (`jeopardy-error`), shown as snackbars. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _boards = MutableStateFlow(BoardsState())
    val boards: StateFlow<BoardsState> = _boards.asStateFlow()

    init {
        viewModelScope.launch {
            wifiConnected.collect { up -> if (up) socket.connect() }
        }
        viewModelScope.launch {
            socket.connectionState.collect { ws ->
                if (ws != WebSocketConnectionState.CONNECTED) welcomed.value = false
            }
        }
        viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    // The server treats every connection as a display until it says hello.
                    SocketEvent.Connected -> {
                        welcomed.value = false
                        socket.send(HostCommands.hello())
                        refreshBoards()
                    }
                    is SocketEvent.Received -> onMessage(event.message)
                    is SocketEvent.GaveUp -> _errors.tryEmit("Lost the connection to the game server.")
                }
            }
        }
    }

    private fun onMessage(message: GameMessage) {
        val p = message.payload
        when (message.type) {
            "jeopardy-welcome" -> welcomed.value = p.optString("role") == "host"
            "jeopardy-state" -> if (welcomed.value) {
                val s = JeopardyParser.state(p)
                clockOffsetMs = s.serverTime - clock()
                _state.value = s
            }
            "jeopardy-event" -> if (JeopardyParser.event(p).kind == "buzz") onBuzz()
            "jeopardy-error" -> _errors.tryEmit(p.optString("message", "Something went wrong"))
            else -> Log.d(TAG, "Ignoring ${message.type}")
        }
    }

    /** Sends a host command. Failures surface as an error; the next state shows the truth. */
    fun send(command: GameMessage) {
        if (!socket.send(command)) _errors.tryEmit("Not connected to the game server.")
    }

    fun refreshBoards() {
        if (_boards.value.loading) return
        _boards.value = _boards.value.copy(loading = true)
        viewModelScope.launch {
            val result = boardsApi.listBoards()
            _boards.value = BoardsState(
                loading = false,
                boards = result.getOrNull() ?: _boards.value.boards,
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun countdown(nowMs: Long = clock()): Countdown = Countdown.of(_state.value, clockOffsetMs, nowMs)

    override fun onCleared() {
        super.onCleared()
        socket.shutdown()
    }
}
