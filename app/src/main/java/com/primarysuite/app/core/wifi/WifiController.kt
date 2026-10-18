package com.primarysuite.app.core.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Joins the event network with WifiNetworkSpecifier on API 29+ and
 * WifiConfiguration on API 23-28. Pass an application context: receivers are
 * registered and unregistered on it.
 */
class WifiController(context: Context) : WifiConnector {

    private val context: Context = context.applicationContext


    companion object {
        private const val TAG = "WifiController"
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2_000L
    }

    private val wifiManager =
        this.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _connectionStatus = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<WifiConnectionState> = _connectionStatus.asStateFlow()

    private var currentTargetSsid: String? = null
    private var currentNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var addedNetworkId: Int = -1
    private var isReceiverRegistered = false

    // Handler and timeout management to avoid leaks
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    // Retry state
    private var retryCount = 0
    private var lastSsid: String? = null
    private var lastPsk: String? = null
    private var retryRunnable: Runnable? = null

    // Legacy receiver for pre-Q only. API 29+ learns the same from its NetworkCallback.
    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    if (wifiState == WifiManager.WIFI_STATE_DISABLED && currentTargetSsid != null) {
                        Log.w(TAG, "Legacy broadcast: WiFi turned off")
                        _connectionStatus.value = WifiConnectionState.DISCONNECTED
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = IntentCompat.getParcelableExtra(
                        intent, WifiManager.EXTRA_NETWORK_INFO, android.net.NetworkInfo::class.java
                    ) ?: return
                    @Suppress("DEPRECATION")
                    val currentSsid = wifiManager.connectionInfo?.ssid?.replace("\"", "")

                    @Suppress("DEPRECATION")
                    val onTarget = networkInfo.isConnected &&
                        networkInfo.type == ConnectivityManager.TYPE_WIFI &&
                        currentSsid == currentTargetSsid
                    if (onTarget) {
                        Log.d(TAG, "Legacy broadcast: Connected to $currentSsid")
                        bindToWifi()
                        _connectionStatus.value = WifiConnectionState.CONNECTED
                    } else if (_connectionStatus.value == WifiConnectionState.CONNECTED) {
                        // Dropped, or the phone moved to another network.
                        Log.w(TAG, "Legacy broadcast: Lost $currentTargetSsid")
                        connectivityManager.bindProcessToNetwork(null)
                        _connectionStatus.value = WifiConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    override fun connect(ssid: String, password: String) {
        val psk = password
        // Android Q+ throws IllegalArgumentException from WifiNetworkSpecifier.Builder.setSsid()
        // if the SSID is blank — guard here so the error surfaces cleanly.
        if (ssid.isBlank()) {
            Log.e(TAG, "connect() called with blank SSID — aborting. Configure SSID in Settings.")
            _connectionStatus.value = WifiConnectionState.ERROR
            return
        }
        if (!wifiManager.isWifiEnabled) {
            Log.w(TAG, "WiFi is not enabled.")
            _connectionStatus.value = WifiConnectionState.ERROR
            return
        }

        Log.i(TAG, "Attempting to connect to SSID: $ssid")
        lastSsid = ssid
        lastPsk = psk
        disconnectFromWifi()
        // Set after tearing down the old network, which may report DISCONNECTED.
        _connectionStatus.value = WifiConnectionState.CONNECTING
        currentTargetSsid = ssid

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectApi29Plus(ssid, psk)
        } else {
            connectLegacy(ssid, psk)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectApi29Plus(ssid: String, psk: String) {
        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (psk.isNotEmpty()) {
            specifierBuilder.setWpa2Passphrase(psk)
        }

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        cancelTimeout()
        val runnable = Runnable {
            Log.e(TAG, "Timed out waiting for network $ssid")
            currentNetworkCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                } catch (_: Exception) { }
            }
            currentNetworkCallback = null
            scheduleRetry()
        }
        timeoutRunnable = runnable
        handler.postDelayed(runnable, CONNECTION_TIMEOUT_MS)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "NetworkCallback: AVAILABLE for $ssid. Binding process.")
                connectivityManager.bindProcessToNetwork(network)
                _connectionStatus.value = WifiConnectionState.CONNECTED
                cancelTimeout()
                resetRetryCount()
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.e(TAG, "NetworkCallback: UNAVAILABLE for $ssid.")
                cancelTimeout()
                scheduleRetry()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.w(TAG, "NetworkCallback: LOST for $ssid. Will attempt to reconnect.")
                connectivityManager.bindProcessToNetwork(null)
                _connectionStatus.value = WifiConnectionState.DISCONNECTED
                resetRetryCount()
                scheduleRetry()
            }
        }

        currentNetworkCallback = callback
        connectivityManager.requestNetwork(networkRequest, callback)
    }

    @Suppress("DEPRECATION")
    private fun connectLegacy(ssid: String, psk: String) {
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = "\"$ssid\""
        wifiConfig.preSharedKey = "\"$psk\""

        if (!isReceiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                wifiStateReceiver,
                IntentFilter().apply {
                    addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                },
                RECEIVER_NOT_EXPORTED,
            )
            isReceiverRegistered = true
        }

        val existingConfig = wifiManager.configuredNetworks?.find { it.SSID == wifiConfig.SSID }
        addedNetworkId = existingConfig?.networkId ?: wifiManager.addNetwork(wifiConfig)

        if (addedNetworkId != -1) {
            Log.d(TAG, "Legacy: Using networkId $addedNetworkId for $ssid")
            wifiManager.disconnect()
            val enabled = wifiManager.enableNetwork(addedNetworkId, true)
            wifiManager.reconnect()
            if (!enabled) {
                Log.e(TAG, "Legacy: Failed to enable network $ssid")
                _connectionStatus.value = WifiConnectionState.ERROR
            }
        } else {
            Log.e(TAG, "Legacy: Failed to add/find network config for $ssid.")
            _connectionStatus.value = WifiConnectionState.ERROR
        }
    }

    /**
     * API 23-28: send this app's traffic over the event Wi-Fi. The Pi's network
     * has no internet, so Android may keep mobile data as the default route, and
     * then `10.0.0.1` is unreachable. API 29+ binds in its NetworkCallback.
     */
    @Suppress("DEPRECATION") // allNetworks: still the way to list networks on API 23-28
    private fun bindToWifi() {
        val wifi = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifi == null) {
            Log.w(TAG, "Legacy: connected, but no Wi-Fi network to bind to")
            return
        }
        connectivityManager.bindProcessToNetwork(wifi)
        Log.d(TAG, "Legacy: bound the app to $wifi")
    }

    override fun disconnect() = disconnectFromWifi()

    private fun disconnectFromWifi() {
        Log.i(TAG, "Disconnecting (target=$currentTargetSsid)")
        cancelTimeout()
        cancelRetry()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            currentNetworkCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                    Log.d(TAG, "NetworkCallback unregistered for $currentTargetSsid")
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering callback: ${e.message}")
                }
            }
            currentNetworkCallback = null
            connectivityManager.bindProcessToNetwork(null)
        } else {
            connectivityManager.bindProcessToNetwork(null)
            @Suppress("DEPRECATION")
            if (addedNetworkId != -1) {
                wifiManager.disconnect()
            }
            if (isReceiverRegistered) {
                try {
                    context.unregisterReceiver(wifiStateReceiver)
                } catch (_: IllegalArgumentException) { }
                isReceiverRegistered = false
            }
        }

        if (_connectionStatus.value != WifiConnectionState.DISCONNECTED && currentTargetSsid != null) {
            _connectionStatus.value = WifiConnectionState.DISCONNECTED
        }
        currentTargetSsid = null
        addedNetworkId = -1
    }

    override fun isConnectedTo(ssid: String?): Boolean {
        val targetSsidToCheck = ssid
        if (targetSsidToCheck == null || !wifiManager.isWifiEnabled) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: connection is managed via WifiNetworkSpecifier whose NetworkCallback
            // only fires onAvailable() for the exact SSID we requested. Querying
            // ConnectivityManager.activeNetwork does NOT work here because specifier-based
            // networks remove NET_CAPABILITY_INTERNET, so they never appear as the
            // "active" internet-capable network. Trust our internal tracked state instead.
            _connectionStatus.value == WifiConnectionState.CONNECTED &&
                    currentTargetSsid == targetSsidToCheck
        } else {
            // API 23–28: legacy WifiConfiguration path — verify via WifiInfo.
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo
            val currentSsid = connectionInfo?.ssid?.replace("\"", "")
            connectionInfo?.networkId != -1 && currentSsid == targetSsidToCheck
        }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun cancelRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "Max retries ($MAX_RETRIES) reached for $lastSsid. Giving up.")
            retryCount = 0
            _connectionStatus.value = WifiConnectionState.ERROR
            return
        }
        retryCount++
        val delay = RETRY_DELAY_MS * retryCount
        Log.i(TAG, "Scheduling WiFi retry #$retryCount in ${delay}ms for $lastSsid")
        _connectionStatus.value = WifiConnectionState.CONNECTING
        cancelRetry()
        val runnable = Runnable {
            val ssid = lastSsid
            val psk = lastPsk
            if (ssid != null && psk != null) {
                Log.i(TAG, "Retrying WiFi connection to $ssid (attempt #$retryCount)")
                disconnectFromWifi()
                _connectionStatus.value = WifiConnectionState.CONNECTING
                currentTargetSsid = ssid
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connectApi29Plus(ssid, psk)
                } else {
                    connectLegacy(ssid, psk)
                }
            }
        }
        retryRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    /** Reset retry counter — call when a connection succeeds. */
    private fun resetRetryCount() {
        retryCount = 0
        cancelRetry()
    }

    override fun cleanup() {
        Log.d(TAG, "Cleanup called.")
        cancelTimeout()
        cancelRetry()
        disconnectFromWifi()
    }
}
