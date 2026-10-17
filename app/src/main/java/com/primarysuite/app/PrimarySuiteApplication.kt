package com.primarysuite.app

import android.app.Application
import android.content.Context
import com.primarysuite.app.core.config.AppPreferences
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.net.HttpServerProbe
import com.primarysuite.app.core.net.ServerProbe
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
    val nfc: NfcHub = NfcHub()
    val strings: StringProvider = AndroidStringProvider(appContext)
}

class PrimarySuiteApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PrimarySuiteApplication).container
