package com.primarysuite.app.games.luckytap

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.nfc.NfcHub
import com.primarysuite.app.core.nfc.NfcState
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.ui.AndroidStringProvider
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.testing.FakeGameConnection
import com.primarysuite.app.testing.FakeNfcTag
import com.primarysuite.app.testing.FakeWifiConnector
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The Lucky Tap screen with its real view model and fake hardware. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class LuckyTapScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val wifi = FakeWifiConnector()
    private val nfc = NfcHub()
    private val socket = FakeGameConnection()
    private lateinit var vm: LuckyTapViewModel
    private var backs = 0

    @Before
    fun setUp() {
        vm = LuckyTapViewModel(AndroidStringProvider(context), wifi, nfc, { _, _ -> socket })
        compose.setContent {
            PrimarySuiteTheme { LuckyTapRoute(vm, onBack = { backs++ }, onEnableNfc = {}) }
        }
    }

    private fun goOnline() {
        nfc.setState(NfcState.AVAILABLE)
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        settle()
    }

    /** Runs pending view model work (it uses the main looper) and recomposes. */
    private fun settle(after: Duration = Duration.ZERO) {
        shadowOf(Looper.getMainLooper()).idleFor(after)
        compose.waitForIdle()
    }

    private fun tap(tag: FakeNfcTag) {
        nfc.dispatch(tag)
        settle()
    }

    private fun openGameTab() {
        compose.onNodeWithTag(LuckyTapTags.TAB_GAME).performClick()
        settle()
    }

    private fun receive(json: String) {
        socket.events.tryEmit(SocketEvent.Received(GameMessage.decode(json)!!))
        settle()
    }

    private val fullState = """{"type":"lucky-tap-full-state","payload":{"checkedInNumbers":[3,7],"likelihoods":{"3":100,"7":75},"currentCollection":[7,7],"numWinners":2}}"""

    private fun assertStatus(text: String) =
        compose.onNodeWithTag(LuckyTapTags.STATUS).performScrollTo().assertTextEquals(text)

    @Test
    fun `scanning needs every connection, writing only needs nfc`() {
        listOf(LuckyTapTags.READ, LuckyTapTags.CHECK_IN, LuckyTapTags.CHECK_OUT)
            .forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }

        nfc.setState(NfcState.AVAILABLE)
        compose.onNodeWithTag(LuckyTapTags.WRITE_INPUT).performTextInput("42")
        settle()
        compose.onNodeWithTag(LuckyTapTags.WRITE).assertIsEnabled()
        compose.onNodeWithTag(LuckyTapTags.READ).assertIsNotEnabled()
        assertStatus(s(R.string.status_waiting_for_connections))

        goOnline()
        listOf(LuckyTapTags.READ, LuckyTapTags.CHECK_IN, LuckyTapTags.CHECK_OUT)
            .forEach { compose.onNodeWithTag(it).assertIsEnabled() }
        assertStatus(s(R.string.status_idle))
    }

    @Test
    fun `game admin needs the server but not nfc`() {
        openGameTab()
        compose.onNodeWithTag(LuckyTapTags.CHECK_IN_ALL).assertIsNotEnabled()

        wifi.connectionState.value = WifiConnectionState.CONNECTED
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        settle()

        listOf(LuckyTapTags.CHECK_IN_ALL, LuckyTapTags.SCAN_ALL, LuckyTapTags.CLEAR, LuckyTapTags.RESET)
            .forEach { compose.onNodeWithTag(it).performScrollTo().assertIsEnabled() }
    }

    @Test
    fun `check-in scan shows the number, then waits for the next card`() {
        goOnline()
        compose.onNodeWithTag(LuckyTapTags.CHECK_IN).performClick()
        compose.onNodeWithTag(LuckyTapTags.SCANNING).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.mode_check_in).uppercase()).assertIsDisplayed()

        tap(FakeNfcTag.card(42))

        compose.onNodeWithContentDescription(s(R.string.cd_scanned_number, "42")).assertIsDisplayed()
        assertEquals(listOf("nfc-check-in"), socket.sent.map { it.type })

        settle(after = Duration.ofMillis(LuckyTapViewModel.SUCCESS_DISPLAY_DURATION_MS + 100))
        compose.onNodeWithTag(LuckyTapTags.SCANNING).assertIsDisplayed()
    }

    @Test
    fun `read and check-out send their messages`() {
        goOnline()
        compose.onNodeWithTag(LuckyTapTags.READ).performClick()
        tap(FakeNfcTag.card(1))
        settle(after = Duration.ofMillis(LuckyTapViewModel.SUCCESS_DISPLAY_DURATION_MS + 100))
        compose.onNodeWithTag(LuckyTapTags.STOP_SCANNING).performClick()
        settle()

        compose.onNodeWithTag(LuckyTapTags.CHECK_OUT).performClick()
        tap(FakeNfcTag.card(2))

        assertEquals(listOf("card-scanned", "nfc-check-out"), socket.sent.map { it.type })
    }

    @Test
    fun `stop scanning returns to the controls`() {
        goOnline()
        compose.onNodeWithTag(LuckyTapTags.READ).performClick()

        compose.onNodeWithTag(LuckyTapTags.STOP_SCANNING).performClick()
        settle()

        compose.onNodeWithTag(LuckyTapTags.READ).assertIsDisplayed()
        assertStatus(s(R.string.status_scan_cancelled))
    }

    @Test
    fun `system back cancels a scan instead of leaving`() {
        goOnline()
        compose.onNodeWithTag(LuckyTapTags.READ).performClick()
        settle()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        settle()

        compose.onNodeWithTag(LuckyTapTags.READ).assertIsDisplayed()
        assertEquals(0, backs)
    }

    @Test
    fun `losing wifi mid-scan returns to the controls and says why`() {
        goOnline()
        compose.onNodeWithTag(LuckyTapTags.READ).performClick()

        wifi.connectionState.value = WifiConnectionState.DISCONNECTED
        settle()

        compose.onNodeWithTag(LuckyTapTags.READ).assertIsDisplayed().assertIsNotEnabled()
        assertStatus(s(R.string.status_waiting_for_connections))
        assertEquals(s(R.string.status_connection_lost), vm.nfcStatusMessage.value)
    }

    @Test
    fun `writes the entered number to a blank card`() {
        nfc.setState(NfcState.AVAILABLE)
        compose.onNodeWithTag(LuckyTapTags.WRITE_INPUT).performTextInput("42")
        compose.onNodeWithTag(LuckyTapTags.WRITE).performClick()
        settle()
        val blank = FakeNfcTag()

        tap(blank)
        settle()

        assertEquals(listOf("42"), blank.writes)
    }

    @Test
    fun `reset game asks first, and only sends when confirmed`() {
        goOnline()
        openGameTab()
        compose.onNodeWithTag(LuckyTapTags.RESET).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.confirm_reset_title)).assertIsDisplayed()
        assertTrue(socket.sent.isEmpty())

        compose.onNodeWithTag(LuckyTapTags.CONFIRM).performClick()

        assertEquals(listOf("admin-reset-game"), socket.sent.map { it.type })
        compose.onNodeWithText(s(R.string.confirm_reset_title)).assertDoesNotExist()
    }

    @Test
    fun `clear scans can be cancelled`() {
        goOnline()
        openGameTab()
        compose.onNodeWithTag(LuckyTapTags.CLEAR).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.confirm_clear_body)).assertIsDisplayed()

        compose.onNodeWithText(s(R.string.cancel)).performClick()

        assertTrue(socket.sent.isEmpty())
        compose.onNodeWithText(s(R.string.confirm_clear_body)).assertDoesNotExist()
    }

    @Test
    fun `the toolbar back arrow leaves the screen`() {
        compose.onNodeWithContentDescription(s(R.string.cd_back)).performClick()
        assertEquals(1, backs)
    }

    // --- Game tab ---

    @Test
    fun `shows checked-in cards with odds and this round's scans`() {
        goOnline()
        openGameTab()
        compose.onNodeWithText(s(R.string.game_waiting_for_state)).assertIsDisplayed()

        receive(fullState)

        compose.onNodeWithText(s(R.string.game_waiting_for_state)).assertDoesNotExist()
        compose.onNodeWithContentDescription(s(R.string.cd_card_odds, 3, 100, 0)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.cd_card_odds, 7, 75, 2)).assertIsDisplayed()
        compose.onNodeWithTag(LuckyTapTags.NUM_WINNERS).assertTextEquals("2")
    }

    @Test
    fun `the stepper changes winners per draw`() {
        goOnline()
        openGameTab()
        receive(fullState)

        compose.onNodeWithTag(LuckyTapTags.MORE_WINNERS).performClick()
        compose.onNodeWithTag(LuckyTapTags.FEWER_WINNERS).performClick()

        assertEquals(listOf(3, 1), socket.sent.map { it.payload.getInt("number") })
        assertEquals(setOf("admin-set-num-winners"), socket.sent.map { it.type }.toSet())
    }

    @Test
    fun `check in all asks first`() {
        goOnline()
        openGameTab()

        compose.onNodeWithTag(LuckyTapTags.CHECK_IN_ALL).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.confirm_check_in_all_title)).assertIsDisplayed()
        compose.onNodeWithTag(LuckyTapTags.CONFIRM).performClick()

        assertEquals(listOf("admin-check-in-all"), socket.sent.map { it.type })
    }

    @Test
    fun `scan all can be cancelled`() {
        goOnline()
        openGameTab()

        compose.onNodeWithTag(LuckyTapTags.SCAN_ALL).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.confirm_scan_all_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.cancel)).performClick()

        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `refresh asks for the full state`() {
        goOnline()
        openGameTab()

        compose.onNodeWithTag(LuckyTapTags.REFRESH).performClick()

        assertEquals(listOf("admin-get-full-state"), socket.sent.map { it.type })
    }

    @Test
    fun `during a draw the admin controls wait, then the winners show`() {
        goOnline()
        openGameTab()
        receive(fullState)

        receive("""{"type":"start-tumbler-animation","payload":{"options":[3,7]}}""")
        compose.onNodeWithTag(LuckyTapTags.DRAW_STATUS).assertTextEquals(s(R.string.game_drawing))
        listOf(LuckyTapTags.REFRESH, LuckyTapTags.MORE_WINNERS, LuckyTapTags.CHECK_IN_ALL, LuckyTapTags.RESET)
            .forEach { compose.onNodeWithTag(it).performScrollTo().assertIsNotEnabled() }

        receive("""{"type":"selected-number-final","payload":{"numbers":[7]}}""")
        compose.onNodeWithTag(LuckyTapTags.DRAW_STATUS).performScrollTo().assertTextEquals(s(R.string.game_last_winners, "7"))
        compose.onNodeWithContentDescription(s(R.string.cd_card_odds, 7, 50, 0)).assertExists()
        compose.onNodeWithTag(LuckyTapTags.RESET).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `a failed draw is shown`() {
        goOnline()
        openGameTab()
        receive(fullState)

        receive("""{"type":"draw-error","payload":{"message":"No cards activated for this round!"}}""")

        compose.onNodeWithTag(LuckyTapTags.DRAW_STATUS)
            .assertTextEquals(s(R.string.game_last_draw_failed, "No cards activated for this round!"))
    }
}
