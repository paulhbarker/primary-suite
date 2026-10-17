package com.primarysuite.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.primarysuite.app.catalog.GameCatalog
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.core.wifi.WifiSetupCoordinator
import com.primarysuite.app.games.luckytap.LuckyTapActivity
import com.primarysuite.app.ui.PrimarySuiteNavHost
import com.primarysuite.app.ui.suiteViewModelFactory
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import kotlinx.coroutines.launch

/** Entry point: the game list, settings, and the app-wide WiFi setup. */
class MainActivity : ComponentActivity() {

    private val wifiSetup = WifiSetupCoordinator(this, { appContainer.wifi }, { appContainer.settings })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer
        val factory = suiteViewModelFactory(container.settings, container.wifi, container.serverProbe)

        setContent {
            PrimarySuiteTheme {
                PrimarySuiteNavHost(
                    viewModelFactory = factory,
                    onOpenTools = { game ->
                        if (game.slug == GameCatalog.LUCKY_TAP) {
                            startActivity(Intent(this, LuckyTapActivity::class.java))
                        }
                    },
                    onWifiRetry = wifiSetup::retry,
                    onSettingsSaved = { result -> if (result.wifiChanged) wifiSetup.reconnect() },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.wifi.connectionState.collect { state ->
                    if (state == WifiConnectionState.ERROR) wifiSetup.onConnectionError()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wifiSetup.ensureConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        // The task is going away (not just rotating): release the event network.
        if (isFinishing) appContainer.wifi.cleanup()
    }
}
