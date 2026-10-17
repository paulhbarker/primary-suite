package com.primarysuite.app.core.config

/** Build-time defaults for the event network and game server. */
data class ConnectionDefaults(
    val wifiSsid: String,
    val wifiPassword: String,
    val serverHost: String,
    val serverPort: String,
)

/**
 * User overrides for the connection settings. A null override means "use the
 * build-time default". [com.primarysuite.app.AppPreferences] is the real store.
 */
interface ConnectionSettings {
    val defaults: ConnectionDefaults

    var wifiSsidOverride: String?
    var wifiPasswordOverride: String?
    var serverHostOverride: String?
    var serverPortOverride: String?
}

val ConnectionSettings.effectiveWifiSsid: String get() = wifiSsidOverride ?: defaults.wifiSsid
val ConnectionSettings.effectiveWifiPassword: String get() = wifiPasswordOverride ?: defaults.wifiPassword

/** The game server address after applying overrides. */
val ConnectionSettings.serverConfig: ServerConfig
    get() = ServerConfig(
        host = serverHostOverride ?: defaults.serverHost,
        port = serverPortOverride ?: defaults.serverPort,
    )

/** Clears every override so the build-time defaults apply again. */
fun ConnectionSettings.resetToDefaults() {
    wifiSsidOverride = null
    wifiPasswordOverride = null
    serverHostOverride = null
    serverPortOverride = null
}
