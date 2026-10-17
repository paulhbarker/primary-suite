package com.primarysuite.app.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import com.primarysuite.app.catalog.Game
import com.primarysuite.app.catalog.GameCatalog
import com.primarysuite.app.games.blockstacker.BlockStackerTags
import com.primarysuite.app.games.reelrush.ReelRushTags
import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.config.serverConfig
import com.primarysuite.app.core.nfc.NfcHub
import com.primarysuite.app.core.nfc.NfcState
import com.primarysuite.app.core.ui.AndroidStringProvider
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.games.luckytap.LuckyTapTags
import com.primarysuite.app.testing.FakeGameConnection
import com.primarysuite.app.testing.FakeServerProbe
import com.primarysuite.app.testing.FakeWifiConnector
import com.primarysuite.app.testing.InMemoryConnectionSettings
import com.primarysuite.app.ui.home.HomeTags
import com.primarysuite.app.ui.settings.SettingsSaved
import com.primarysuite.app.ui.settings.SettingsTags
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Drives the real nav host with fake WiFi, server and settings. */
@RunWith(AndroidJUnit4::class)
// Tall enough that the whole game list is on screen.
@Config(qualifiers = "w411dp-h1400dp")
class NavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val settings = InMemoryConnectionSettings()
    private val wifi = FakeWifiConnector()
    private val probe = FakeServerProbe()
    private val socket = FakeGameConnection()
    private val nfc = NfcHub()
    private val socketSlugs = mutableListOf<String>()

    private var nfcSettingsOpened = 0
    private val saved = mutableListOf<SettingsSaved>()
    private var wifiRetries = 0

    /** The real catalog plus a game without tools (second, so it's on screen), for the coming-soon screen. */
    private val games = GameCatalog.games.take(1) +
        Game("spin-the-wheel", "Spin the Wheel", "No tools yet", hasTools = false) +
        GameCatalog.games.drop(1)

    @Before
    fun setUp() {
        compose.setContent {
            PrimarySuiteTheme {
                PrimarySuiteNavHost(
                    viewModelFactory = suiteViewModelFactory(
                        settings, wifi, probe, nfc, AndroidStringProvider(context),
                        socketFactory = { slug, _, _ -> socketSlugs += slug; socket },
                        games = games,
                        jeopardyBoards = { Result.success(emptyList()) },
                    ),
                    games = games,
                    onEnableNfc = { nfcSettingsOpened++ },
                    onWifiRetry = { wifiRetries++ },
                    onSettingsSaved = { saved += it },
                )
            }
        }
    }

    private fun pressBack() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    private fun assertOnHome() = compose.onNodeWithTag(HomeTags.game("lucky-tap")).assertIsDisplayed()

    @Test
    fun `opens on the game list`() {
        assertOnHome()
        compose.onNodeWithText(s(R.string.app_name)).assertIsDisplayed()
    }

    @Test
    fun `Lucky Tap opens its tools on its own socket, closed on the way out`() {
        compose.onNodeWithTag(HomeTags.game("lucky-tap")).performClick()

        compose.onNodeWithTag(LuckyTapTags.READ).assertIsDisplayed()
        assertEquals(listOf("lucky-tap"), socketSlugs)
        assertEquals(false, socket.shutdownCalled)

        pressBack()
        assertOnHome()
        assertEquals(true, socket.shutdownCalled)
    }

    @Test
    fun `Lucky Tap with NFC off offers to turn it on`() {
        nfc.setState(NfcState.DISABLED)
        compose.onNodeWithTag(HomeTags.game("lucky-tap")).performClick()

        compose.onNodeWithTag(LuckyTapTags.ENABLE_NFC).performClick()

        assertEquals(1, nfcSettingsOpened)
    }

    @Test
    fun `Lucky Tap on a phone without NFC says so`() {
        nfc.setState(NfcState.UNAVAILABLE)
        compose.onNodeWithTag(HomeTags.game("lucky-tap")).performClick()

        compose.onNodeWithText(s(R.string.nfc_no_hardware)).assertIsDisplayed()
    }

    @Test
    fun `a game without tools shows coming soon, and back returns home`() {
        compose.onNodeWithTag(HomeTags.game("spin-the-wheel")).performClick()

        compose.onNodeWithText(s(R.string.coming_soon_body, "Spin the Wheel")).assertIsDisplayed()
        assertEquals(emptyList<String>(), socketSlugs)

        pressBack()
        assertOnHome()
    }

    @Test
    fun `Reel Rush opens its admin on its own socket`() {
        compose.onNodeWithTag(HomeTags.game("reel-rush")).performClick()

        compose.onNodeWithTag(ReelRushTags.CONNECTING).assertExists()
        assertEquals(listOf("reel-rush"), socketSlugs)

        pressBack()
        assertOnHome()
        assertEquals(true, socket.shutdownCalled)
    }

    @Test
    fun `Block Stacker opens its admin on its own socket`() {
        compose.onNodeWithTag(HomeTags.game("block-stacker")).performClick()

        compose.onNodeWithTag(BlockStackerTags.CONNECTING).assertExists()
        assertEquals(listOf("block-stacker"), socketSlugs)

        pressBack()
        assertOnHome()
        assertEquals(true, socket.shutdownCalled)
    }

    @Test
    fun `State Switch opens its admin on its own socket`() {
        compose.onNodeWithTag(HomeTags.game("state-switch")).performClick()

        compose.onNodeWithText(s(R.string.state_switch_connecting)).assertIsDisplayed()
        assertEquals(listOf("state-switch"), socketSlugs)

        pressBack()
        assertOnHome()
        assertEquals(true, socket.shutdownCalled)
    }

    @Test
    fun `Jeopardy opens the host controller on its own socket`() {
        compose.onNodeWithTag(HomeTags.game("jeopardy")).performClick()

        compose.onNodeWithText(s(R.string.jeopardy_connecting)).assertIsDisplayed()
        assertEquals(listOf("jeopardy"), socketSlugs)

        pressBack()
        assertOnHome()
        assertEquals(true, socket.shutdownCalled)
    }

    @Test
    fun `the toolbar back arrow leaves coming soon`() {
        compose.onNodeWithTag(HomeTags.game("state-switch")).performClick()
        compose.onNodeWithContentDescription(s(R.string.cd_back)).performClick()
        assertOnHome()
    }

    @Test
    fun `tapping wifi when it's down asks to retry`() {
        wifi.connectionState.value = WifiConnectionState.ERROR
        compose.waitForIdle()

        compose.onNodeWithTag(HomeTags.WIFI_STATUS).performClick()

        assertEquals(1, wifiRetries)
    }

    @Test
    fun `settings opens from the gear and back returns home`() {
        compose.onNodeWithContentDescription(s(R.string.cd_open_settings)).performClick()
        compose.onNodeWithTag(SettingsTags.SAVE).assertIsDisplayed()

        pressBack()
        assertOnHome()
        assertEquals(emptyList<SettingsSaved>(), saved)
    }

    @Test
    fun `an invalid port is flagged and nothing is saved`() {
        compose.onNodeWithContentDescription(s(R.string.cd_open_settings)).performClick()
        compose.onNodeWithTag(SettingsTags.PORT).performTextInput("70000")
        compose.onNodeWithTag(SettingsTags.SAVE).performClick()

        compose.onNodeWithText(s(R.string.settings_port_invalid)).assertIsDisplayed()
        assertEquals(null, settings.serverPortOverride)
        assertEquals(emptyList<SettingsSaved>(), saved)
    }

    @Test
    fun `saving returns home and shows the new server`() {
        compose.onNodeWithContentDescription(s(R.string.cd_open_settings)).performClick()
        compose.onNodeWithTag(SettingsTags.HOST).performTextInput("192.168.4.1")
        compose.onNodeWithTag(SettingsTags.PORT).performTextInput("9000")
        compose.onNodeWithTag(SettingsTags.SAVE).performClick()

        assertOnHome()
        assertEquals(ServerConfig("192.168.4.1", "9000"), settings.serverConfig)
        assertEquals(listOf(SettingsSaved(wifiChanged = false, serverChanged = true)), saved)
        compose.onNodeWithText("192.168.4.1:9000").assertIsDisplayed()
    }

    @Test
    fun `changing the network name tells the app to rejoin wifi`() {
        compose.onNodeWithContentDescription(s(R.string.cd_open_settings)).performClick()
        compose.onNodeWithTag(SettingsTags.SSID).performTextInput("Backstage")
        compose.onNodeWithTag(SettingsTags.SAVE).performClick()

        assertEquals(listOf(SettingsSaved(wifiChanged = true, serverChanged = false)), saved)
        compose.onNodeWithText("Backstage", substring = true).assertIsDisplayed()
    }

    @Test
    fun `reset clears overrides and stays on settings`() {
        settings.serverHostOverride = "192.168.4.1"
        compose.onNodeWithContentDescription(s(R.string.cd_open_settings)).performClick()
        compose.onNodeWithTag(SettingsTags.HOST).performTextClearance()
        compose.onNodeWithTag(SettingsTags.RESET).performClick()

        assertEquals(null, settings.serverHostOverride)
        assertEquals(listOf(SettingsSaved(wifiChanged = false, serverChanged = true)), saved)
        compose.onNodeWithTag(SettingsTags.SAVE).assertIsDisplayed()
    }
}
