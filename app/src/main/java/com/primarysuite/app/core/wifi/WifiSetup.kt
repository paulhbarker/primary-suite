package com.primarysuite.app.core.wifi

/** What to do next to get onto the event network. */
sealed interface WifiSetupAction {
    /** Already connected or connecting. */
    data object None : WifiSetupAction
    data object RequestPermissions : WifiSetupAction
    data object EnableWifi : WifiSetupAction
    data object EnableLocation : WifiSetupAction
    data object MissingSsid : WifiSetupAction
    data class Connect(val ssid: String, val password: String) : WifiSetupAction {
        // Logged by the coordinator: never put the password in the system log.
        override fun toString(): String = "Connect(ssid=$ssid, password=${if (password.isEmpty()) "<none>" else "<hidden>"})"
    }
}

/** The device conditions that decide the next [WifiSetupAction]. */
data class WifiSetupConditions(
    val state: WifiConnectionState,
    val permissionsGranted: Boolean,
    val wifiEnabled: Boolean,
    val locationEnabled: Boolean,
    val ssid: String,
    val password: String,
)

/**
 * Steps run in order: permissions, WiFi on, location on (Android requires it
 * to join a network by SSID), an SSID configured, then connect.
 */
fun nextWifiSetupAction(c: WifiSetupConditions): WifiSetupAction = when {
    c.state == WifiConnectionState.CONNECTED || c.state == WifiConnectionState.CONNECTING -> WifiSetupAction.None
    !c.permissionsGranted -> WifiSetupAction.RequestPermissions
    !c.wifiEnabled -> WifiSetupAction.EnableWifi
    !c.locationEnabled -> WifiSetupAction.EnableLocation
    c.ssid.isBlank() -> WifiSetupAction.MissingSsid
    else -> WifiSetupAction.Connect(c.ssid, c.password)
}
