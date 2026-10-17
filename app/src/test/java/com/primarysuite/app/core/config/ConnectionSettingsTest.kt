package com.primarysuite.app.core.config

import com.primarysuite.app.testing.InMemoryConnectionSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionSettingsTest {

    private val settings = InMemoryConnectionSettings()

    @Test
    fun `uses build defaults when nothing is overridden`() {
        assertEquals("EventNet", settings.effectiveWifiSsid)
        assertEquals("secret", settings.effectiveWifiPassword)
        assertEquals(ServerConfig("10.0.0.1", "8080"), settings.serverConfig)
    }

    @Test
    fun `overrides win over defaults`() {
        settings.wifiSsidOverride = "Backstage"
        settings.wifiPasswordOverride = "hunter2"
        settings.serverHostOverride = "192.168.4.1"
        settings.serverPortOverride = "9000"

        assertEquals("Backstage", settings.effectiveWifiSsid)
        assertEquals("hunter2", settings.effectiveWifiPassword)
        assertEquals("ws://192.168.4.1:9000/ws/lucky-tap", settings.serverConfig.webSocketUrl("lucky-tap"))
    }

    @Test
    fun `host and port can be overridden separately`() {
        settings.serverPortOverride = "9000"
        assertEquals(ServerConfig("10.0.0.1", "9000"), settings.serverConfig)
    }

    @Test
    fun `reset restores every default`() {
        settings.wifiSsidOverride = "Backstage"
        settings.wifiPasswordOverride = "hunter2"
        settings.serverHostOverride = "192.168.4.1"
        settings.serverPortOverride = "9000"

        settings.resetToDefaults()

        assertEquals("EventNet", settings.effectiveWifiSsid)
        assertEquals("secret", settings.effectiveWifiPassword)
        assertEquals(ServerConfig("10.0.0.1", "8080"), settings.serverConfig)
    }
}
