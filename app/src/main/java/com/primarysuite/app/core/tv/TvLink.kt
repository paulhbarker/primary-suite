package com.primarysuite.app.core.tv

import com.primarysuite.app.core.net.GameConnection
import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.wifi.WifiConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * What this phone knows about the TV. [leader] is true when this phone is the
 * one that moves it: the first phone to connect, until another takes over.
 */
data class TvState(
    val connected: Boolean = false,
    val leader: Boolean = false,
    /** The game on the TV, "" for its welcome screen, null until known. */
    val game: String? = null,
)

/** The TV, as the home screen sees it. Tests pass a fake. */
interface TvControl {
    val state: StateFlow<TvState>

    /**
     * Shows [gameSlug] on the TV, or its welcome screen for "". Only does
     * anything while this phone leads; returns whether it was sent.
     */
    fun show(gameSlug: String): Boolean

    /** Makes this phone the one that moves the TV. Returns whether it was sent. */
    fun takeOver(): Boolean

    companion object {
        /** No TV: never connected, never leads. */
        val NONE: TvControl = object : TvControl {
            override val state: StateFlow<TvState> = MutableStateFlow(TvState())
            override fun show(gameSlug: String) = false
            override fun takeOver() = false
        }
    }
}

/**
 * Holds the `/ws/stage` socket while the app is on screen ([start]/[stop]),
 * and says hello with [phoneId] on every connect. The server remembers the
 * leader by that id, so a leader that sleeps or closes the app keeps the TV
 * until another phone takes over.
 */
class TvLink(
    private val scope: CoroutineScope,
    private val socket: GameConnection,
    private val phoneId: String,
    private val wifiState: StateFlow<WifiConnectionState>,
) : TvControl {

    companion object {
        const val SLUG = "stage"
    }

    private val _state = MutableStateFlow(TvState())
    override val state: StateFlow<TvState> = _state.asStateFlow()

    private var job: Job? = null

    /** Connects whenever WiFi is up, until [stop]. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            launch {
                socket.events.collect { event ->
                    when (event) {
                        SocketEvent.Connected ->
                            socket.send(GameMessage("stage-hello", JSONObject().put("id", phoneId)))
                        is SocketEvent.Received -> if (event.message.type == "stage") onStage(event.message.payload)
                        is SocketEvent.GaveUp -> Unit
                    }
                }
            }
            launch {
                socket.connectionState.collect { s ->
                    val connected = s == WebSocketConnectionState.CONNECTED
                    // Lead is only known from the server; drop it until told again.
                    _state.update { if (connected) it.copy(connected = true) else it.copy(connected = false, leader = false) }
                }
            }
            wifiState.collect { if (it == WifiConnectionState.CONNECTED) socket.connect() }
        }
    }

    /** Closes the socket. A leader stays the leader. */
    fun stop() {
        job?.cancel()
        job = null
        socket.disconnect()
        _state.update { it.copy(connected = false, leader = false) }
    }

    override fun show(gameSlug: String): Boolean {
        if (!_state.value.leader) return false
        return socket.send(GameMessage("stage-set", JSONObject().put("game", gameSlug)))
    }

    override fun takeOver(): Boolean {
        if (!_state.value.connected || _state.value.leader) return false
        return socket.send(GameMessage("stage-take-over"))
    }

    private fun onStage(payload: JSONObject) {
        _state.update {
            it.copy(
                game = payload.optString("game", ""),
                leader = payload.optBoolean("leader", false),
            )
        }
    }
}
