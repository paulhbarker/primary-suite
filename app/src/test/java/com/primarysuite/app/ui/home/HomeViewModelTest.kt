package com.primarysuite.app.ui.home

import com.primarysuite.app.catalog.GameCatalog
import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.testing.FakeServerProbe
import com.primarysuite.app.testing.FakeTvControl
import com.primarysuite.app.testing.FakeWifiConnector
import com.primarysuite.app.testing.InMemoryConnectionSettings
import com.primarysuite.app.testing.MainDispatcherRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val settings = InMemoryConnectionSettings()
    private val wifi = FakeWifiConnector()
    private val probe = FakeServerProbe()
    private val tv = FakeTvControl()
    private val vm: HomeViewModel by lazy { HomeViewModel(settings, wifi, probe, probeIntervalMs = 5_000, tv = tv) }

    private val state: HomeUiState get() = vm.uiState.value

    /**
     * runTest finishes by running everything left on the scheduler, which the
     * view model shares. Its probe loop never ends, so stop it first.
     */
    private fun homeTest(body: suspend TestScope.() -> Unit) = runTest(main.dispatcher) {
        vm // created here, after MainDispatcherRule swapped Dispatchers.Main
        try {
            body()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `shows every game and the configured network`(): Unit = homeTest {
        runCurrent()
        assertEquals(GameCatalog.games, state.games)
        assertEquals("EventNet", state.wifiSsid)
        assertEquals(ServerConfig("10.0.0.1", "8080"), state.server)
    }

    @Test
    fun `picking a game puts it on the TV, and coming back home clears it`(): Unit = homeTest {
        vm.onGameSelected(GameCatalog.find(GameCatalog.REEL_RUSH)!!)
        vm.onReturnedHome()
        assertEquals(listOf("reel-rush", ""), tv.shown)
    }

    @Test
    fun `shows whether this phone leads the TV`(): Unit = homeTest {
        runCurrent()
        assertEquals(true, state.tv.leader)
        tv.state.value = tv.state.value.copy(leader = false)
        runCurrent()
        assertEquals(false, state.tv.leader)
    }

    @Test
    fun `waits for wifi before checking the server`(): Unit = homeTest {
        runCurrent()
        assertEquals(WifiConnectionState.DISCONNECTED, state.wifiState)
        assertEquals(ServerStatus.WAITING_FOR_WIFI, state.serverStatus)

        wifi.connectionState.value = WifiConnectionState.CONNECTING
        runCurrent()

        assertEquals(WifiConnectionState.CONNECTING, state.wifiState)
        assertEquals(ServerStatus.WAITING_FOR_WIFI, state.serverStatus)
        assertEquals(0, probe.checked.size)
    }

    @Test
    fun `checks the server once wifi connects`(): Unit = homeTest {
        probe.latencyMs = 100
        runCurrent()

        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()
        assertEquals(ServerStatus.CHECKING, state.serverStatus)

        advanceTimeBy(101)
        assertEquals(ServerStatus.REACHABLE, state.serverStatus)
        assertEquals(listOf(ServerConfig("10.0.0.1", "8080")), probe.checked)
    }

    @Test
    fun `reports an unreachable server`(): Unit = homeTest {
        probe.reachable = false
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()

        assertEquals(ServerStatus.UNREACHABLE, state.serverStatus)
    }

    @Test
    fun `keeps checking on an interval and notices changes`(): Unit = homeTest {
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()
        assertEquals(1, probe.checked.size)

        probe.reachable = false
        advanceTimeBy(5_001)
        assertEquals(2, probe.checked.size)
        assertEquals(ServerStatus.UNREACHABLE, state.serverStatus)

        probe.reachable = true
        advanceTimeBy(5_000)
        assertEquals(3, probe.checked.size)
        assertEquals(ServerStatus.REACHABLE, state.serverStatus)
    }

    @Test
    fun `stops checking when wifi drops`(): Unit = homeTest {
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()

        wifi.connectionState.value = WifiConnectionState.DISCONNECTED
        runCurrent()
        advanceTimeBy(20_000)

        assertEquals(1, probe.checked.size)
        assertEquals(ServerStatus.WAITING_FOR_WIFI, state.serverStatus)
        assertEquals(WifiConnectionState.DISCONNECTED, state.wifiState)
    }

    @Test
    fun `refresh picks up new settings and checks right away`(): Unit = homeTest {
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()

        settings.wifiSsidOverride = "Backstage"
        settings.serverHostOverride = "192.168.4.1"
        vm.refresh()
        runCurrent()

        assertEquals("Backstage", state.wifiSsid)
        assertEquals(ServerConfig("192.168.4.1", "8080"), state.server)
        assertEquals(ServerConfig("192.168.4.1", "8080"), probe.checked.last())
        assertEquals(2, probe.checked.size)
    }
}
