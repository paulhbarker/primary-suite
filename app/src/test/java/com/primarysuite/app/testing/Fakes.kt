package com.primarysuite.app.testing

import com.primarysuite.app.core.config.ConnectionDefaults
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.net.ServerProbe
import com.primarysuite.app.core.net.GameConnection
import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.ui.StringProvider
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.core.wifi.WifiConnector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

val TEST_DEFAULTS = ConnectionDefaults(
    wifiSsid = "EventNet",
    wifiPassword = "secret",
    serverHost = "10.0.0.1",
    serverPort = "8080",
)

class InMemoryConnectionSettings(
    override val defaults: ConnectionDefaults = TEST_DEFAULTS,
) : ConnectionSettings {
    override var wifiSsidOverride: String? = null
    override var wifiPasswordOverride: String? = null
    override var serverHostOverride: String? = null
    override var serverPortOverride: String? = null
}

/** Renders a resource as "#<id>" or "#<id>[arg, arg]" so tests can compare without Android resources. */
class FakeStrings : StringProvider {
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) "#$id" else "#$id${args.toList()}"
}

/** A [GameConnection] that records what was sent and lets tests drive its state. */
class FakeGameConnection : GameConnection {
    override val connectionState = MutableStateFlow(WebSocketConnectionState.DISCONNECTED)
    override val events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 16)

    val sent = mutableListOf<GameMessage>()
    var connectCalls = 0
        private set
    var shutdownCalled = false
        private set

    /** What [send] returns. */
    var acceptSends = true

    override fun connect() {
        connectCalls++
    }

    override fun send(message: GameMessage): Boolean {
        if (!acceptSends) return false
        sent += message
        return true
    }

    override fun disconnect() {
        connectionState.value = WebSocketConnectionState.DISCONNECTED
    }

    override fun shutdown() {
        shutdownCalled = true
        disconnect()
    }
}

class FakeWifiConnector : WifiConnector {
    override val connectionState = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    var connectedSsid: String? = null
    val connectRequests = mutableListOf<Pair<String, String>>()

    override fun connect(ssid: String, password: String) {
        connectRequests += ssid to password
        connectionState.value = WifiConnectionState.CONNECTING
    }

    override fun disconnect() {
        connectedSsid = null
        connectionState.value = WifiConnectionState.DISCONNECTED
    }

    override fun isConnectedTo(ssid: String?): Boolean = ssid != null && ssid == connectedSsid

    override fun cleanup() = disconnect()
}

/** A [ServerProbe] whose answer tests control. Records which servers were checked. */
class FakeServerProbe(var reachable: Boolean = true, var latencyMs: Long = 0) : ServerProbe {
    val checked = mutableListOf<ServerConfig>()

    override suspend fun isReachable(server: ServerConfig): Boolean {
        checked += server
        if (latencyMs > 0) kotlinx.coroutines.delay(latencyMs)
        return reachable
    }
}
