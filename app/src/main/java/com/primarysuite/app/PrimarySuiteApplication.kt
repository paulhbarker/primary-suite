package com.primarysuite.app

import android.app.Application
import android.content.Context
import com.primarysuite.app.core.config.AppPreferences
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.net.HttpServerProbe
import com.primarysuite.app.core.net.ServerProbe
import com.primarysuite.app.core.net.gameSocket
import com.primarysuite.app.core.tv.TvLink
import com.primarysuite.app.core.wifi.WifiConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID
import com.primarysuite.app.core.nfc.NfcHub
import com.primarysuite.app.core.ui.AndroidStringProvider
import com.primarysuite.app.core.ui.StringProvider
import com.primarysuite.app.core.wifi.WifiConnector
import com.primarysuite.app.core.wifi.WifiController

/**
 * App-wide objects. The WiFi connection belongs to the app, not to a screen,
 * so moving between screens doesn't drop it. Everything is created on first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: ConnectionSettings by lazy { AppPreferences.getInstance(appContext) }
    val wifiController: WifiController by lazy { WifiController(appContext) }
    val wifi: WifiConnector get() = wifiController
    val serverProbe: ServerProbe by lazy { HttpServerProbe() }

    /** Lives as long as the app; for connections that outlast a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The TV link. MainActivity starts it while the app is on screen. */
    val tv: TvLink by lazy {
        TvLink(
            scope = appScope,
            socket = gameSocket(TvLink.SLUG, settings, appScope) { wifi.connectionState.value == WifiConnectionState.CONNECTED },
            phoneId = phoneId(),
            wifiState = wifi.connectionState,
        )
    }

    /** A random id for this install, so the server knows a phone that reconnects. */
    private fun phoneId(): String {
        val prefs = appContext.getSharedPreferences("tv", Context.MODE_PRIVATE)
        return prefs.getString("phone_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("phone_id", it).apply()
        }
    }
    val nfc: NfcHub = NfcHub()
    val strings: StringProvider = AndroidStringProvider(appContext)
}

class PrimarySuiteApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PrimarySuiteApplication).container
