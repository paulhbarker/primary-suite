package com.primarysuite.app.games.buttondebug

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ButtonDebugScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun sessionOf(snap: DebugSnapshot) = DebugSession().onSnapshot(snap)

    private var ui by mutableStateOf(ButtonDebugUiState(connected = true, session = sessionOf(snapshot())))

    private val ledModes = mutableListOf<LedMode>()
    private val debounces = mutableListOf<Double>()
    private val colors = mutableListOf<Pair<Int, ButtonColor>>()
    private var chases = 0
    private var enableAlls = 0
    private var resets = 0
    private var backs = 0

    private fun show() {
        compose.setContent {
            PrimarySuiteTheme {
                ButtonDebugScreen(
                    ui = ui,
                    snackbar = SnackbarHostState(),
                    onLedMode = { ledModes += it },
                    onChase = { chases++ },
                    onEnableAll = { enableAlls++ },
                    onDebounce = { debounces += it },
                    onReset = { resets++ },
                    onColor = { id, c -> colors += id to c },
                    onBack = { backs++ },
                )
            }
        }
    }

    /** Scrolls the list to [tag] and returns it. */
    private fun node(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag(ButtonDebugTags.LIST).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun click(tag: String) = node(tag).performClick()

    private fun withSession(snap: DebugSnapshot, connected: Boolean = true, rtt: Double? = null) {
        ui = ButtonDebugUiState(connected = connected, rttMs = rtt, session = sessionOf(snap))
    }

    // ---- header and banners

    @Test
    fun `shows the title, the chip and the connection`() {
        show()
        compose.onNodeWithText(s(R.string.buttondebug_title)).assertIsDisplayed()
        compose.onNodeWithTag(ButtonDebugTags.CONNECTION).assertTextEquals(s(R.string.buttondebug_connected))
        compose.onNodeWithText(s(R.string.buttondebug_description_box, "gpiochip0")).assertIsDisplayed()
        compose.onNodeWithTag(ButtonDebugTags.RTT).assertDoesNotExist()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_OFFLINE).assertDoesNotExist()
    }

    @Test
    fun `shows the round trip while connected`() {
        ui = ui.copy(rttMs = 42.4)
        show()
        compose.onNodeWithTag(ButtonDebugTags.RTT).assertTextEquals(s(R.string.buttondebug_rtt, 42))
        ui = ui.copy(connected = false)
        compose.waitForIdle()
        compose.onNodeWithTag(ButtonDebugTags.RTT).assertDoesNotExist()
    }

    @Test
    fun `offline shows the badge and banner and locks the controls`() {
        ui = ui.copy(connected = false)
        show()
        compose.onNodeWithText(s(R.string.game_offline)).assertIsDisplayed()
        compose.onNodeWithTag(ButtonDebugTags.CONNECTION).assertTextEquals(s(R.string.buttondebug_disconnected))
        compose.onNodeWithTag(ButtonDebugTags.BANNER_OFFLINE).assertIsDisplayed()
        LedMode.entries.forEach { node(ButtonDebugTags.led(it)).assertIsNotEnabled() }
        node(ButtonDebugTags.CHASE).assertIsNotEnabled()
        node(ButtonDebugTags.RESET_STATS).assertIsNotEnabled()
        node(ButtonDebugTags.ENABLE_ALL).assertIsNotEnabled()
        node(ButtonDebugTags.debouncePreset(20)).assertIsNotEnabled()
        node(ButtonDebugTags.DEBOUNCE_SET).assertIsNotEnabled()
    }

    @Test
    fun `nothing is shown before the first snapshot except the offline banner`() {
        ui = ButtonDebugUiState()
        show()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_OFFLINE).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.game_offline)).assertDoesNotExist()
        compose.onNodeWithTag(ButtonDebugTags.SUMMARY).assertDoesNotExist()
        node(ButtonDebugTags.LOG_EMPTY).assertTextEquals(s(R.string.buttondebug_log_empty))
    }

    @Test
    fun `no box shows the reason and no tiles or controls, but wifi buttons still show`() {
        withSession(snapshot(box = false, boxError = "BUTTON_BOX is not set", buttons = emptyList(), status = null))
        ui = ui.copy(session = ui.session.onPress(press(id = 9, seq = 1, source = "wifi", rssi = -60)))
        show()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_NO_BOX).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_banner_no_box, "BUTTON_BOX is not set")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_description)).assertIsDisplayed()
        compose.onNodeWithTag(ButtonDebugTags.SUMMARY).assertDoesNotExist()
        compose.onNodeWithTag(ButtonDebugTags.CHASE).assertDoesNotExist()
        node(ButtonDebugTags.WIFI_CARD).assertIsDisplayed()
    }

    @Test
    fun `no box banner is not shown while offline`() {
        withSession(snapshot(box = false, boxError = "x", buttons = emptyList(), status = null), connected = false)
        show()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_NO_BOX).assertDoesNotExist()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_OFFLINE).assertIsDisplayed()
    }

    @Test
    fun `warning banners and enable all`() {
        withSession(
            snapshot(status = status(enabled = listOf(false, false), activeGames = listOf("lucky-tap", "state-switch"), droppedEdges = 7, levelErr = "gpio busy")),
        )
        show()
        compose.onNodeWithText(s(R.string.buttondebug_banner_level_err, "gpio busy")).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.buttondebug_banner_disabled, 2, "4, 3")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_banner_active_games, "lucky-tap, state-switch")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_banner_dropped, 7)).assertIsDisplayed()

        click(ButtonDebugTags.BANNER_ENABLE_ALL)
        assertEquals(1, enableAlls)
    }

    @Test
    fun `the disabled banner says button for one`() {
        withSession(snapshot(status = status(enabled = listOf(true, false))))
        show()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.buttondebug_banner_disabled, 1, "3")).assertIsDisplayed()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_ACTIVE_GAMES).assertDoesNotExist()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_DROPPED).assertDoesNotExist()
        compose.onNodeWithTag(ButtonDebugTags.BANNER_LEVEL_ERR).assertDoesNotExist()
    }

    // ---- summary

    @Test
    fun `summary asks for presses, then reports problems or none`() {
        show()
        compose.onNodeWithText(s(R.string.buttondebug_summary_empty)).assertIsDisplayed()

        withSession(snapshot(events = Tap.down(0, seq = 1) + Tap.up(300_000), presses = listOf(press(id = 4, seq = 1))))
        compose.waitForIdle()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.buttondebug_summary_ok, 1, 1)).assertIsDisplayed()

        withSession(snapshot(status = status(stuck = listOf(true, true))))
        compose.waitForIdle()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.buttondebug_summary_problems, 2, 2)).assertIsDisplayed()
    }

    // ---- tiles

    @Test
    fun `tiles are in box order with the right counts`() {
        withSession(snapshot(events = Tap.down(0, seq = 1) + Tap.up(300_000), presses = listOf(press(id = 4, seq = 1))))
        show()
        val blue = compose.onNodeWithTag(ButtonDebugTileTags.tile(4)).getUnclippedBoundsInRoot()
        node(ButtonDebugTileTags.tile(3)).assertExists()
        assertTrue("id 4 is blue, so it is first", blue.top < blue.bottom)
        compose.onNodeWithTag(ButtonDebugTileTags.color(4)).assertTextContains("Blue")
        node(ButtonDebugTileTags.color(3)).assertTextContains("White")
        node(ButtonDebugTileTags.count(3)).assertTextEquals("0")
        node(ButtonDebugTileTags.count(4)).assertTextEquals("1")
    }

    @Test
    fun `a tile shows the line, debounced state and LED from the status`() {
        withSession(snapshot(status = status(down = listOf(true, false), levels = listOf(true, false), leds = listOf(false, true))))
        show()
        node(ButtonDebugTileTags.line(4)).assertTextEquals(s(R.string.buttondebug_line_low))
        node(ButtonDebugTileTags.debounced(4)).assertTextEquals(s(R.string.buttondebug_held))
        node(ButtonDebugTileTags.led(4)).assertTextEquals(s(R.string.buttondebug_off))
        node(ButtonDebugTileTags.line(3)).assertTextEquals(s(R.string.buttondebug_line_high))
        node(ButtonDebugTileTags.debounced(3)).assertTextEquals(s(R.string.buttondebug_up))
        node(ButtonDebugTileTags.led(3)).assertTextEquals(s(R.string.buttondebug_on))
    }

    @Test
    fun `a healthy tile, a disabled tile and a stuck tile`() {
        withSession(
            snapshot(
                status = status(enabled = listOf(true, false), stuck = listOf(true, false)),
                events = Tap.down(0, id = 3, button = 1, seq = 1) + Tap.up(300_000, id = 3, button = 1),
                presses = listOf(press(id = 3, seq = 1)),
            ),
        )
        show()
        node(ButtonDebugTileTags.badge(3, "healthy")).assertIsDisplayed()
        node(ButtonDebugTileTags.badge(3, "disabled")).assertIsDisplayed()
        node(ButtonDebugTileTags.badge(4, "stuck")).assertIsDisplayed()
        compose.onNodeWithTag(ButtonDebugTileTags.badge(4, "healthy")).assertDoesNotExist()
        node(ButtonDebugTileTags.issues(4)).assertIsDisplayed()
        compose.onNodeWithTag(ButtonDebugTileTags.issues(3)).assertDoesNotExist()
    }

    @Test
    fun `problems are listed under the tile with the web's text`() {
        // Two blips of noise and a short press on id 4.
        val noise = listOf(
            edge(tsUs = 0, verdict = Verdict.PENDING),
            edge(kind = EdgeKind.SETTLE, tsUs = 20_000, pressed = false, lagUs = -1, burstUs = 0, verdict = Verdict.BLIP),
        )
        withSession(snapshot(events = noise + noise.map { it.copy(tsUs = it.tsUs + 100_000) }))
        show()
        node(ButtonDebugTileTags.issues(4)).assertIsDisplayed()
        compose.onNodeWithText("•  " + problems(Stats(blips = 2), false).single()).assertIsDisplayed()
    }

    @Test
    fun `stats show the web's values`() {
        val events = Tap.down(0, seq = 1) + Tap.up(500_000)
        withSession(snapshot(events = events, presses = listOf(press(id = 4, seq = 1, hubUs = 450))))
        show()
        node(ButtonDebugTileTags.stat(4, "sent")).assertTextEquals("1 / 1")
        node(ButtonDebugTileTags.stat(4, "late")).assertTextEquals("0 (–)")
        node(ButtonDebugTileTags.stat(4, "lost")).assertTextEquals("0 / 0")
        node(ButtonDebugTileTags.stat(4, "noise")).assertTextEquals("0")
        node(ButtonDebugTileTags.stat(4, "short")).assertTextEquals("0")
        node(ButtonDebugTileTags.stat(4, "dropped")).assertTextEquals("0")
        node(ButtonDebugTileTags.stat(4, "edges")).assertTextEquals("5 / 0")
        node(ButtonDebugTileTags.stat(4, "pressBounce")).assertTextEquals("700µs")
        node(ButtonDebugTileTags.stat(4, "releaseBounce")).assertTextEquals("300µs")
        node(ButtonDebugTileTags.stat(4, "hold")).assertTextEquals("500ms / 500ms")
        node(ButtonDebugTileTags.stat(4, "lag")).assertTextEquals("100µs / 100µs")
        node(ButtonDebugTileTags.stat(4, "hub")).assertTextEquals("450µs / 450µs")
        node(ButtonDebugTileTags.stat(3, "hold")).assertTextEquals("– / –")
        node(ButtonDebugTileTags.stat(3, "lag")).assertTextEquals("– / –")
    }

    @Test
    fun `waves show their burst or no data`() {
        withSession(snapshot(events = Tap.down(0, seq = 1)))
        show()
        node(ButtonDebugTileTags.wave(4, "press") + ".count").assertTextEquals("3× · 700µs")
        node(ButtonDebugTileTags.wave(4, "release")).assertExists()
        compose.onNodeWithText(s(R.string.buttondebug_wave_none)).assertExists()
        compose.onNodeWithTag(ButtonDebugTileTags.wave(4, "noise")).assertDoesNotExist()
    }

    @Test
    fun `the noise wave appears after noise`() {
        withSession(
            snapshot(
                events = listOf(
                    edge(tsUs = 0, verdict = Verdict.PENDING),
                    edge(kind = EdgeKind.SETTLE, tsUs = 20_000, pressed = false, lagUs = -1, burstUs = 0, verdict = Verdict.BLIP),
                ),
            ),
        )
        show()
        node(ButtonDebugTileTags.wave(4, "noise") + ".count").assertTextEquals("1× · 0µs")
    }

    @Test
    fun `picking a colour reports the button and colour`() {
        show()
        click(ButtonDebugTileTags.color(3))
        compose.onNodeWithTag(ButtonDebugTileTags.colorOption(3, ButtonColor.RED)).performClick()
        assertEquals(listOf(3 to ButtonColor.RED), colors)
    }

    @Test
    fun `the colour menu offers every colour`() {
        show()
        click(ButtonDebugTileTags.color(4))
        ButtonColor.entries.forEach { compose.onNodeWithTag(ButtonDebugTileTags.colorOption(4, it)).assertIsDisplayed() }
    }

    @Test
    fun `a changed colour re-sorts the tiles and relabels them`() {
        show()
        ui = ui.copy(colors = ButtonColor.DEFAULTS + (3 to ButtonColor.GREEN))
        compose.waitForIdle()
        node(ButtonDebugTileTags.color(3)).assertTextContains("Green")
    }

    // ---- controls

    @Test
    fun `each LED button sends its mode and the current one is a filled button`() {
        withSession(snapshot(status = status(ledMode = "follow")))
        show()
        LedMode.entries.forEach { click(ButtonDebugTags.led(it)) }
        assertEquals(LedMode.entries, ledModes)
        LedMode.entries.forEach { node(ButtonDebugTags.led(it)).assertIsEnabled() }
        compose.onNodeWithText(s(R.string.buttondebug_led_game)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_led_follow)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_led_on)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_led_off)).assertIsDisplayed()
    }

    @Test
    fun `chase, reset and enable all`() {
        show()
        click(ButtonDebugTags.CHASE)
        click(ButtonDebugTags.RESET_STATS)
        click(ButtonDebugTags.ENABLE_ALL)
        assertEquals(1, chases)
        assertEquals(1, resets)
        assertEquals(1, enableAlls)
        compose.onNodeWithText(s(R.string.buttondebug_chase)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.buttondebug_reset_stats)).assertIsDisplayed()
    }

    @Test
    fun `debounce shows the current value and every preset sends`() {
        withSession(snapshot(status = status(quietMs = 7.5)))
        show()
        node(ButtonDebugTags.DEBOUNCE_NOW).assertTextContains(s(R.string.buttondebug_debounce_now, "7.5ms"), substring = true)
        ButtonDebugProtocol.DEBOUNCE_PRESETS.forEach { click(ButtonDebugTags.debouncePreset(it)) }
        assertEquals(listOf(5.0, 10.0, 20.0, 30.0, 50.0), debounces)
    }

    @Test
    fun `the current debounce reads as a whole number`() {
        show()
        node(ButtonDebugTags.DEBOUNCE_NOW).assertTextContains(s(R.string.buttondebug_debounce_now, "20ms"), substring = true)
    }

    @Test
    fun `the debounce field sends a typed value, only when it is a number from 0 to 500`() {
        show()
        node(ButtonDebugTags.DEBOUNCE_SET).assertIsNotEnabled()

        node(ButtonDebugTags.DEBOUNCE_FIELD).performTextInput("12.5")
        node(ButtonDebugTags.DEBOUNCE_SET).assertIsEnabled().performClick()
        assertEquals(listOf(12.5), debounces)
        // The field clears after sending, as on the web.
        node(ButtonDebugTags.DEBOUNCE_SET).assertIsNotEnabled()

        node(ButtonDebugTags.DEBOUNCE_FIELD).performTextInput("600")
        node(ButtonDebugTags.DEBOUNCE_SET).assertIsNotEnabled()
        assertEquals(1, debounces.size)
    }

    // ---- wifi and log

    @Test
    fun `wifi buttons show presses, lost sequence numbers, rssi and address`() {
        withSession(snapshot())
        var session = ui.session
        session = session.onPress(press(id = 9, seq = 1, source = "wifi", rssi = -60, remote = "192.168.4.20:5555"))
        session = session.onPress(press(id = 9, seq = 4, source = "wifi", rssi = -80))
        ui = ui.copy(session = session)
        show()
        node(ButtonDebugTags.wifi(9)).assertIsDisplayed()
        node(ButtonDebugTags.wifiStat(9, "presses")).assertTextEquals("2")
        node(ButtonDebugTags.wifiStat(9, "lost")).assertTextEquals("2")
        node(ButtonDebugTags.wifiStat(9, "rssi")).assertTextEquals("-80 dBm")
        node(ButtonDebugTags.wifiStat(9, "from")).assertTextEquals("192.168.4.20:5555")
    }

    @Test
    fun `no wifi card until a wifi button presses`() {
        show()
        compose.onNodeWithTag(ButtonDebugTags.LIST).performScrollToNode(hasTestTag(ButtonDebugTags.HOWTO))
        compose.onNodeWithTag(ButtonDebugTags.WIFI_CARD).assertDoesNotExist()
    }

    @Test
    fun `the log lists edges newest first with the web's columns`() {
        withSession(snapshot(events = Tap.down(1_000_000, seq = 12)))
        show()
        // Row ids count from 0 in replay order; the settle is the newest.
        node(ButtonDebugTags.logRow(3)).assertIsDisplayed()
        node(ButtonDebugTags.logPart(3, "event")).assertTextEquals("settle: LOW")
        node(ButtonDebugTags.logPart(3, "verdict")).assertTextEquals("holding")
        node(ButtonDebugTags.logPart(3, "time")).assertTextEquals("20.700")
        node(ButtonDebugTags.logPart(3, "lag")).assertTextEquals(s(R.string.buttondebug_log_burst, "700µs"))

        node(ButtonDebugTags.logPart(0, "event")).assertTextEquals("edge ↓")
        node(ButtonDebugTags.logPart(0, "verdict")).assertTextEquals("press #12")
        node(ButtonDebugTags.logPart(0, "time")).assertTextEquals("0.000")
        node(ButtonDebugTags.logPart(0, "button")).assertTextEquals("#4")
        node(ButtonDebugTags.logPart(0, "gap")).assertTextEquals(s(R.string.buttondebug_log_gap, "–"))
        node(ButtonDebugTags.logPart(0, "lag")).assertTextEquals(s(R.string.buttondebug_log_lag, "100µs"))
        node(ButtonDebugTags.logPart(1, "event")).assertTextEquals("edge ↑")
        compose.onNodeWithTag(ButtonDebugTags.LOG_EMPTY).assertDoesNotExist()
    }

    @Test
    fun `lost kernel edges are flagged on the row`() {
        withSession(snapshot(events = listOf(edge(tsUs = 0, kernelGap = 3))))
        show()
        node(ButtonDebugTags.logPart(0, "lost")).assertTextEquals(s(R.string.buttondebug_log_lost, 3))
    }

    @Test
    fun `a wifi press is logged`() {
        withSession(snapshot(presses = listOf(press(id = 9, seq = 5, source = "wifi", rssi = -61))))
        show()
        node(ButtonDebugTags.logPart(0, "event")).assertTextEquals(s(R.string.buttondebug_log_wifi))
        node(ButtonDebugTags.logPart(0, "verdict")).assertTextEquals("press #5")
        node(ButtonDebugTags.logPart(0, "rssi")).assertTextEquals("-61 dBm")
        node(ButtonDebugTags.logPart(0, "button")).assertTextEquals("#9")
    }

    // ---- how to and navigation

    @Test
    fun `the full check names the LED order from the colours`() {
        show()
        node(ButtonDebugTags.HOWTO_CHASE).assertTextContains("white, blue", substring = true)
        node(ButtonDebugTags.HOWTO).assertIsDisplayed()
    }

    @Test
    fun `the full check falls back to id order when there are no buttons`() {
        withSession(snapshot(box = false, buttons = emptyList(), status = null))
        show()
        node(ButtonDebugTags.HOWTO_CHASE).assertTextContains(s(R.string.buttondebug_howto_order_unknown), substring = true)
    }

    @Test
    fun `back arrow goes back`() {
        show()
        compose.onNodeWithContentDescription(s(R.string.cd_back)).performClick()
        assertEquals(1, backs)
    }
}
