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
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.nfc.NfcHub
import com.primarysuite.app.core.nfc.NfcState
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

    private fun assertStatus(text: String) =
        compose.onNodeWithTag(LuckyTapTags.STATUS).performScrollTo().assertTextEquals(text)

    @Test
    fun `scanning and admin need every connection, writing only needs nfc`() {
        listOf(LuckyTapTags.READ, LuckyTapTags.CHECK_IN, LuckyTapTags.CHECK_OUT, LuckyTapTags.CLEAR, LuckyTapTags.RESET)
            .forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }

        nfc.setState(NfcState.AVAILABLE)
        compose.onNodeWithTag(LuckyTapTags.WRITE_INPUT).performTextInput("42")
        settle()
        compose.onNodeWithTag(LuckyTapTags.WRITE).assertIsEnabled()
        compose.onNodeWithTag(LuckyTapTags.READ).assertIsNotEnabled()
        assertStatus(s(R.string.status_waiting_for_connections))

        goOnline()
        listOf(LuckyTapTags.READ, LuckyTapTags.CHECK_IN, LuckyTapTags.CHECK_OUT, LuckyTapTags.CLEAR, LuckyTapTags.RESET)
            .forEach { compose.onNodeWithTag(it).assertIsEnabled() }
        assertStatus(s(R.string.status_idle))
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
}
