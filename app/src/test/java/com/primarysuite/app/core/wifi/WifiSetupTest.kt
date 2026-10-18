package com.primarysuite.app.core.wifi

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSetupTest {

    private val ready = WifiSetupConditions(
        state = WifiConnectionState.DISCONNECTED,
        permissionsGranted = true,
        wifiEnabled = true,
        locationEnabled = true,
        ssid = "EventNet",
        password = "secret",
    )

    @Test
    fun `connects when everything is in place`() {
        assertEquals(WifiSetupAction.Connect("EventNet", "secret"), nextWifiSetupAction(ready))
    }

    @Test
    fun `also connects after an earlier error`() {
        assertEquals(WifiSetupAction.Connect("EventNet", "secret"), nextWifiSetupAction(ready.copy(state = WifiConnectionState.ERROR)))
    }

    @Test
    fun `does nothing while connected or connecting`() {
        val blocked = ready.copy(permissionsGranted = false, wifiEnabled = false)
        assertEquals(WifiSetupAction.None, nextWifiSetupAction(blocked.copy(state = WifiConnectionState.CONNECTED)))
        assertEquals(WifiSetupAction.None, nextWifiSetupAction(blocked.copy(state = WifiConnectionState.CONNECTING)))
    }

    @Test
    fun `steps run in order - permissions, wifi, location, ssid`() {
        val nothing = ready.copy(permissionsGranted = false, wifiEnabled = false, locationEnabled = false, ssid = "")
        assertEquals(WifiSetupAction.RequestPermissions, nextWifiSetupAction(nothing))
        assertEquals(WifiSetupAction.EnableWifi, nextWifiSetupAction(nothing.copy(permissionsGranted = true)))
        assertEquals(
            WifiSetupAction.EnableLocation,
            nextWifiSetupAction(nothing.copy(permissionsGranted = true, wifiEnabled = true)),
        )
        assertEquals(
            WifiSetupAction.MissingSsid,
            nextWifiSetupAction(nothing.copy(permissionsGranted = true, wifiEnabled = true, locationEnabled = true)),
        )
    }

    @Test
    fun `a blank ssid counts as missing`() {
        assertEquals(WifiSetupAction.MissingSsid, nextWifiSetupAction(ready.copy(ssid = "  ")))
    }

    @Test
    fun `an open network connects with an empty password`() {
        assertEquals(WifiSetupAction.Connect("EventNet", ""), nextWifiSetupAction(ready.copy(password = "")))
    }

    @Test
    fun `a connect step never shows the password in logs`() {
        val text = WifiSetupAction.Connect("GroveParkPrimary", "GrovePark10").toString()
        assertEquals("Connect(ssid=GroveParkPrimary, password=<hidden>)", text)
        assertEquals("Connect(ssid=Open, password=<none>)", WifiSetupAction.Connect("Open", "").toString())
    }
}
