package com.primarysuite.app.ui.settings

import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.config.serverConfig
import com.primarysuite.app.testing.InMemoryConnectionSettings
import com.primarysuite.app.testing.TEST_DEFAULTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    private val settings = InMemoryConnectionSettings()
    private val vm by lazy { SettingsViewModel(settings) }
    private val state get() = vm.uiState.value

    @Test
    fun `starts with the current overrides and blank fields for defaults`() {
        settings.serverHostOverride = "192.168.4.1"

        assertEquals("", state.wifiSsid)
        assertEquals("192.168.4.1", state.serverHost)
        assertEquals("", state.serverPort)
        assertEquals(TEST_DEFAULTS, state.defaults)
    }

    @Test
    fun `saves every field, trimming the server address`() {
        vm.onWifiSsidChange("Backstage")
        vm.onWifiPasswordChange(" pass word ")
        vm.onServerHostChange(" 192.168.4.1 ")
        vm.onServerPortChange(" 9000 ")

        val result = vm.save()

        assertEquals(SettingsSaved(wifiChanged = true, serverChanged = true), result)
        assertEquals("Backstage", settings.wifiSsidOverride)
        assertEquals(" pass word ", settings.wifiPasswordOverride)
        assertEquals(ServerConfig("192.168.4.1", "9000"), settings.serverConfig)
    }

    @Test
    fun `blank fields clear the override`() {
        settings.wifiSsidOverride = "Backstage"
        settings.serverPortOverride = "9000"
        vm.onWifiSsidChange("  ")
        vm.onServerPortChange("")

        vm.save()

        assertNull(settings.wifiSsidOverride)
        assertNull(settings.serverPortOverride)
    }

    @Test
    fun `reports only what actually changed`() {
        vm.onServerPortChange("9000")
        assertEquals(SettingsSaved(wifiChanged = false, serverChanged = true), vm.save())

        vm.onWifiSsidChange("EventNet") // same as the default
        assertEquals(SettingsSaved(wifiChanged = false, serverChanged = false), vm.save())
    }

    @Test
    fun `invalid host or port blocks saving and flags the field`() {
        vm.onServerHostChange("http://10.0.0.1")
        vm.onServerPortChange("80000")

        assertNull(vm.save())
        assertTrue(state.hostError)
        assertTrue(state.portError)
        assertNull(settings.serverHostOverride)
        assertNull(settings.serverPortOverride)
    }

    @Test
    fun `editing a flagged field clears its error`() {
        vm.onServerPortChange("0")
        vm.save()

        vm.onServerPortChange("8081")

        assertFalse(state.portError)
    }

    @Test
    fun `reset clears overrides and the form`() {
        settings.wifiSsidOverride = "Backstage"
        settings.serverHostOverride = "192.168.4.1"

        val result = vm.resetToDefaults()

        assertEquals(SettingsSaved(wifiChanged = true, serverChanged = true), result)
        assertNull(settings.wifiSsidOverride)
        assertNull(settings.serverHostOverride)
        assertEquals("", state.wifiSsid)
        assertEquals("", state.serverHost)
    }
}
