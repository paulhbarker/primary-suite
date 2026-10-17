package com.primarysuite.app.core.wifi

import kotlinx.coroutines.flow.StateFlow

enum class WifiConnectionState { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

/** Joins the event WiFi network. [WifiController] is the Android implementation. */
interface WifiConnector {
    val connectionState: StateFlow<WifiConnectionState>

    /** Starts joining [ssid], retrying on failure. The caller must hold location permission. */
    fun connect(ssid: String, password: String)

    fun disconnect()

    /** True if the phone is on [ssid] right now. */
    fun isConnectedTo(ssid: String?): Boolean

    /** Stops retries and releases the network. Call when the owner is destroyed. */
    fun cleanup()
}
