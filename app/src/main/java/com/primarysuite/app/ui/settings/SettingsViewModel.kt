package com.primarysuite.app.ui.settings

import androidx.lifecycle.ViewModel
import com.primarysuite.app.core.config.ConnectionDefaults
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.config.effectiveWifiPassword
import com.primarysuite.app.core.config.effectiveWifiSsid
import com.primarysuite.app.core.config.resetToDefaults
import com.primarysuite.app.core.config.serverConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The form. A blank field means "use the default", which the screen shows as
 * a hint. Errors appear only after a save attempt.
 */
data class SettingsUiState(
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val serverHost: String = "",
    val serverPort: String = "",
    val defaults: ConnectionDefaults,
    val hostError: Boolean = false,
    val portError: Boolean = false,
)

/** What changed on save, so the app knows whether to rejoin WiFi or recheck the server. */
data class SettingsSaved(val wifiChanged: Boolean, val serverChanged: Boolean)

class SettingsViewModel(private val settings: ConnectionSettings) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState() = SettingsUiState(
        wifiSsid = settings.wifiSsidOverride.orEmpty(),
        wifiPassword = settings.wifiPasswordOverride.orEmpty(),
        serverHost = settings.serverHostOverride.orEmpty(),
        serverPort = settings.serverPortOverride.orEmpty(),
        defaults = settings.defaults,
    )

    fun onWifiSsidChange(value: String) = _uiState.update { it.copy(wifiSsid = value) }
    fun onWifiPasswordChange(value: String) = _uiState.update { it.copy(wifiPassword = value) }
    fun onServerHostChange(value: String) = _uiState.update { it.copy(serverHost = value, hostError = false) }
    fun onServerPortChange(value: String) = _uiState.update { it.copy(serverPort = value, portError = false) }

    /** Validates and stores the form. Returns null, and flags the bad fields, if it's invalid. */
    fun save(): SettingsSaved? {
        val form = _uiState.value
        val host = form.serverHost.trim()
        val port = form.serverPort.trim()
        val hostError = host.isNotEmpty() && !ServerConfig.isValidHost(host)
        val portError = port.isNotEmpty() && !ServerConfig.isValidPort(port)
        if (hostError || portError) {
            _uiState.update { it.copy(hostError = hostError, portError = portError) }
            return null
        }

        val before = Snapshot.of(settings)
        // The SSID may legitimately have spaces at the ends; the password must be kept exactly.
        settings.wifiSsidOverride = form.wifiSsid.ifBlank { null }
        settings.wifiPasswordOverride = form.wifiPassword.ifEmpty { null }
        settings.serverHostOverride = host.ifEmpty { null }
        settings.serverPortOverride = port.ifEmpty { null }
        return before.diff(Snapshot.of(settings))
    }

    /** Clears every override and empties the form. */
    fun resetToDefaults(): SettingsSaved {
        val before = Snapshot.of(settings)
        settings.resetToDefaults()
        _uiState.value = loadState()
        return before.diff(Snapshot.of(settings))
    }

    private data class Snapshot(val ssid: String, val password: String, val server: ServerConfig) {
        fun diff(after: Snapshot) = SettingsSaved(
            wifiChanged = ssid != after.ssid || password != after.password,
            serverChanged = server != after.server,
        )

        companion object {
            fun of(s: ConnectionSettings) = Snapshot(s.effectiveWifiSsid, s.effectiveWifiPassword, s.serverConfig)
        }
    }
}
