package com.primarysuite.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.primarysuite.app.catalog.Game
import com.primarysuite.app.catalog.GameCatalog
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.config.effectiveWifiSsid
import com.primarysuite.app.core.config.serverConfig
import com.primarysuite.app.core.net.ServerProbe
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.core.wifi.WifiConnector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ServerStatus { WAITING_FOR_WIFI, CHECKING, REACHABLE, UNREACHABLE }

data class HomeUiState(
    val wifiSsid: String,
    val wifiState: WifiConnectionState,
    val server: ServerConfig,
    val serverStatus: ServerStatus,
    val games: List<Game>,
)

/**
 * Home screen: event WiFi status, game server status, and the game list.
 * While WiFi is connected it checks the server every [probeIntervalMs].
 */
class HomeViewModel(
    private val settings: ConnectionSettings,
    private val wifi: WifiConnector,
    private val probe: ServerProbe,
    games: List<Game> = GameCatalog.games,
    private val probeIntervalMs: Long = DEFAULT_PROBE_INTERVAL_MS,
) : ViewModel() {

    companion object {
        const val DEFAULT_PROBE_INTERVAL_MS = 5_000L
    }

    private val _uiState = MutableStateFlow(
        HomeUiState(
            wifiSsid = settings.effectiveWifiSsid,
            wifiState = wifi.connectionState.value,
            server = settings.serverConfig,
            serverStatus = ServerStatus.WAITING_FOR_WIFI,
            games = games,
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Bumped to restart the probe loop right away, e.g. after settings change. */
    private val refreshes = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(wifi.connectionState, refreshes) { state, _ -> state }.collectLatest { state ->
                val server = settings.serverConfig
                _uiState.update { it.copy(wifiState = state, wifiSsid = settings.effectiveWifiSsid, server = server) }
                if (state != WifiConnectionState.CONNECTED) {
                    _uiState.update { it.copy(serverStatus = ServerStatus.WAITING_FOR_WIFI) }
                    return@collectLatest
                }
                // Show "checking" only before the first answer, so the status doesn't flicker.
                _uiState.update { it.copy(serverStatus = ServerStatus.CHECKING) }
                while (true) {
                    val reachable = probe.isReachable(server)
                    _uiState.update {
                        it.copy(serverStatus = if (reachable) ServerStatus.REACHABLE else ServerStatus.UNREACHABLE)
                    }
                    delay(probeIntervalMs)
                }
            }
        }
    }

    /** Re-reads settings and checks the server now. */
    fun refresh() {
        refreshes.value++
    }
}
