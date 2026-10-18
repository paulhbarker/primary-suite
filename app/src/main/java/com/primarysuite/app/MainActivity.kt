package com.primarysuite.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.primarysuite.app.core.nfc.AndroidNfcTag
import com.primarysuite.app.core.config.serverConfig
import com.primarysuite.app.core.nfc.NfcState
import com.primarysuite.app.core.ui.vibrateBuzz
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.core.wifi.WifiSetupCoordinator
import com.primarysuite.app.ui.PrimarySuiteNavHost
import com.primarysuite.app.games.buttondebug.PrefsButtonColorStore
import com.primarysuite.app.ui.suiteViewModelFactory
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import kotlinx.coroutines.launch

/**
 * The only activity. Hosts the Compose screens and owns the app-wide hardware:
 * WiFi setup, and the NFC adapter (foreground dispatch while resumed, tags
 * forwarded to [com.primarysuite.app.core.nfc.NfcHub]).
 */
class MainActivity : ComponentActivity() {

    private val wifiSetup = WifiSetupCoordinator(this, { appContainer.wifi }, { appContainer.settings })

    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null

    // Same as the original scanner: text records, plus any NDEF tag so blank cards can be written.
    private val nfcFilters = arrayOf(IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply { addDataType("text/plain") })
    private val nfcTechLists = arrayOf(arrayOf(Ndef::class.java.name))

    /** Set when we send the user to the NFC settings, so we can come back once it's on. */
    private var awaitingNfcEnable = false

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return
            updateNfcState()
            val on = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF) == NfcAdapter.STATE_ON
            if (on && awaitingNfcEnable) {
                awaitingNfcEnable = false
                // Pop the settings screen and bring the app back.
                startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer
        val factory = suiteViewModelFactory(
            settings = container.settings,
            wifi = container.wifi,
            probe = container.serverProbe,
            nfc = container.nfc,
            strings = container.strings,
            onBuzz = { vibrateBuzz(applicationContext) },
            buttonColors = PrefsButtonColorStore(getSharedPreferences("button-debug", MODE_PRIVATE)),
            tv = container.tv,
        )

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null) {
            setupNfcDispatch()
            ContextCompat.registerReceiver(
                this,
                nfcStateReceiver,
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
        updateNfcState()

        setContent {
            PrimarySuiteTheme {
                PrimarySuiteNavHost(
                    viewModelFactory = factory,
                    onEnableNfc = ::openNfcSettings,
                    onWifiRetry = wifiSetup::retry,
                    onSettingsSaved = { result -> if (result.wifiChanged) wifiSetup.reconnect() },
                    serverBase = { container.settings.serverConfig.httpUrl },
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

    override fun onStart() {
        super.onStart()
        // The TV follows the first phone connected, so stay connected only
        // while the app is on screen.
        appContainer.tv.start()
    }

    override fun onStop() {
        super.onStop()
        appContainer.tv.stop()
    }

    override fun onResume() {
        super.onResume()
        updateNfcState()
        // Deliver tags to this activity instead of letting Android pick an app for them.
        nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, nfcFilters, nfcTechLists)
        wifiSetup.ensureConnected()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = AndroidNfcTag.from(intent)
        if (tag == null) {
            Log.d(TAG, "Intent without a tag: ${intent.action}")
            return
        }
        appContainer.nfc.dispatch(tag)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (nfcAdapter != null) unregisterReceiver(nfcStateReceiver)
        // The task is going away (not just rotating): release the event network.
        if (isFinishing) appContainer.wifi.cleanup()
    }

    @SuppressLint("WrongConstant")
    private fun setupNfcDispatch() {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags,
        )
    }

    private fun updateNfcState() {
        val adapter = nfcAdapter
        appContainer.nfc.setState(
            when {
                adapter == null -> NfcState.UNAVAILABLE
                adapter.isEnabled -> NfcState.AVAILABLE
                else -> NfcState.DISABLED
            },
        )
    }

    private fun openNfcSettings() {
        awaitingNfcEnable = true
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
