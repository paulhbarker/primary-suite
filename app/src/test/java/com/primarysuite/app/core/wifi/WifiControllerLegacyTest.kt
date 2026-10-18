package com.primarysuite.app.core.wifi

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowWifiInfo

/**
 * Android 6-9 joins with WifiConfiguration. The Pi's network has no internet, so
 * the app must bind its traffic to it, or Android may route `10.0.0.1` over mobile data.
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class WifiControllerLegacyTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val cellNetwork = ShadowNetwork.newInstance(7)
    private val wifiNetwork = ShadowNetwork.newInstance(42)
    private lateinit var controller: WifiController

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            // Below API 33, AndroidX guards not-exported receivers with this app permission;
            // the test sends the broadcast that the system would.
            "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        wifiManager.isWifiEnabled = true

        // Mobile data (the default route) plus the event Wi-Fi, which has no internet.
        shadowOf(cm).clearAllNetworks()
        val cellCaps = ShadowNetworkCapabilities.newInstance()
        shadowOf(cellCaps).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        val cellInfo = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_MOBILE, 0, true, NetworkInfo.State.CONNECTED)
        shadowOf(cm).addNetwork(cellNetwork, cellInfo)
        shadowOf(cm).setNetworkCapabilities(cellNetwork, cellCaps)

        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        val info = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED)
        shadowOf(cm).addNetwork(wifiNetwork, info)
        shadowOf(cm).setNetworkCapabilities(wifiNetwork, caps)

        controller = WifiController(app)
    }

    private fun onNetwork(ssid: String, connected: Boolean) {
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiInfo).setSSID(ssid)
        shadowOf(wifiManager).setConnectionInfo(wifiInfo)
        val state = if (connected) NetworkInfo.State.CONNECTED else NetworkInfo.State.DISCONNECTED
        val detailed = if (connected) NetworkInfo.DetailedState.CONNECTED else NetworkInfo.DetailedState.DISCONNECTED
        val info = ShadowNetworkInfo.newInstance(detailed, ConnectivityManager.TYPE_WIFI, 0, true, state)
        app.sendBroadcast(Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION).putExtra(WifiManager.EXTRA_NETWORK_INFO, info))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Bound to a Wi-Fi network, not mobile data. (The shadow may add its own Wi-Fi network on join.) */
    private fun assertBoundToWifi() {
        val bound = cm.boundNetworkForProcess
        assertNotNull("app should be bound to the event Wi-Fi", bound)
        assertNotEquals(cellNetwork, bound)
        assertTrue(cm.getNetworkCapabilities(bound)!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    }

    @Test
    fun `binds the app to the event wifi once joined, and unbinds when it drops`() {
        controller.connect("GroveParkPrimary", "GrovePark10")
        assertNull(cm.boundNetworkForProcess)

        onNetwork("GroveParkPrimary", connected = true)
        assertEquals(WifiConnectionState.CONNECTED, controller.connectionState.value)
        assertBoundToWifi()

        onNetwork("GroveParkPrimary", connected = false)
        assertEquals(WifiConnectionState.DISCONNECTED, controller.connectionState.value)
        assertNull(cm.boundNetworkForProcess)
    }

    @Test
    fun `another network is not bound`() {
        controller.connect("GroveParkPrimary", "GrovePark10")
        onNetwork("HomeWifi", connected = true)
        assertNull(cm.boundNetworkForProcess)
    }

    @Test
    fun `disconnecting unbinds`() {
        controller.connect("GroveParkPrimary", "GrovePark10")
        onNetwork("GroveParkPrimary", connected = true)
        controller.disconnect()
        assertNull(cm.boundNetworkForProcess)
    }
}
