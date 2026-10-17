package com.primarysuite.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import com.primarysuite.app.catalog.GameCatalog
import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.ui.home.HomeScreen
import com.primarysuite.app.ui.home.HomeTags
import com.primarysuite.app.ui.home.HomeUiState
import com.primarysuite.app.ui.home.ServerStatus
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private var wifiClicks = 0

    private fun show(wifi: WifiConnectionState, server: ServerStatus) {
        val state = HomeUiState(
            wifiSsid = "EventNet",
            wifiState = wifi,
            server = ServerConfig("10.0.0.1", "8080"),
            serverStatus = server,
            games = GameCatalog.games,
        )
        compose.setContent {
            PrimarySuiteTheme {
                HomeScreen(state, onGameClick = {}, onSettingsClick = {}, onWifiClick = { wifiClicks++ })
            }
        }
    }

    private fun assertStatus(tag: String, label: Int, value: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithContentDescription("${s(label)}: $value").assertIsDisplayed()
    }

    @Test
    fun `lists every game`() {
        show(WifiConnectionState.DISCONNECTED, ServerStatus.WAITING_FOR_WIFI)
        GameCatalog.games.forEach {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(HomeTags.game(it.slug)))
            compose.onNodeWithTag(HomeTags.game(it.slug)).assertIsDisplayed()
        }
    }

    @Test
    fun `shows a healthy connection`() {
        show(WifiConnectionState.CONNECTED, ServerStatus.REACHABLE)
        assertStatus(HomeTags.WIFI_STATUS, R.string.home_label_wifi, s(R.string.home_wifi_connected))
        assertStatus(HomeTags.SERVER_STATUS, R.string.home_label_server, s(R.string.home_server_reachable))
    }

    @Test
    fun `shows a server that stopped answering`() {
        show(WifiConnectionState.CONNECTED, ServerStatus.UNREACHABLE)
        assertStatus(HomeTags.SERVER_STATUS, R.string.home_label_server, s(R.string.home_server_unreachable))
    }

    @Test
    fun `while connecting, the server waits and wifi can't be retried`() {
        show(WifiConnectionState.CONNECTING, ServerStatus.WAITING_FOR_WIFI)
        assertStatus(HomeTags.WIFI_STATUS, R.string.home_label_wifi, s(R.string.home_wifi_connecting))
        assertStatus(HomeTags.SERVER_STATUS, R.string.home_label_server, s(R.string.home_server_waiting))

        compose.onNodeWithTag(HomeTags.WIFI_STATUS).performClick()
        assertEquals(0, wifiClicks)
    }

    @Test
    fun `tapping wifi after an error retries`() {
        show(WifiConnectionState.ERROR, ServerStatus.WAITING_FOR_WIFI)
        assertStatus(HomeTags.WIFI_STATUS, R.string.home_label_wifi, s(R.string.home_wifi_error))

        compose.onNodeWithTag(HomeTags.WIFI_STATUS).performClick()
        assertEquals(1, wifiClicks)
    }
}
