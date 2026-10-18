package com.primarysuite.app.games.stateswitch

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
class StateSwitchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val base = StateSwitchSettings("fast-slow", 2.5f, limitEnabled = false, limit = 20)
    private var ui by mutableStateOf(StateSwitchUiState(connected = true, settings = base))

    private val changes = mutableListOf<StateSwitchSettings>()
    private var switches = 0
    private var resetActuations = 0
    private var resetAlls = 0
    private var backs = 0
    private var displays = 0

    private fun show() {
        compose.setContent {
            PrimarySuiteTheme {
                StateSwitchScreen(
                    ui = ui,
                    snackbar = SnackbarHostState(),
                    onSettings = { changes += it; ui = ui.copy(settings = it) },
                    onSwitch = { switches++ },
                    onResetActuations = { resetActuations++ },
                    onResetAll = { resetAlls++ },
                    onBack = { backs++ },
                    onDisplay = { displays++ },
                )
            }
        }
    }

    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** Drags a slider to [value] and lets go, as one gesture. */
    private fun slide(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    @Test
    fun `waits for the server's settings`() {
        ui = StateSwitchUiState()
        show()
        compose.onNodeWithText(s(R.string.state_switch_connecting)).assertIsDisplayed()
        compose.onNodeWithTag(StateSwitchTags.SWITCH).assertDoesNotExist()
    }

    @Test
    fun `shows the web admin's controls with the server's values`() {
        show()
        compose.onNodeWithTag(StateSwitchTags.SWITCH).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Fast / Slow").assertIsDisplayed()
        compose.onNodeWithTag(StateSwitchTags.TIMEOUT_VALUE).assertTextEquals("2.5 sec")
        compose.onNodeWithTag(StateSwitchTags.LIMIT_ENABLED).assertIsOff()
        compose.onNodeWithTag(StateSwitchTags.LIMIT).assertDoesNotExist()
        compose.onNodeWithTag(StateSwitchTags.RESET_ACTUATIONS).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(StateSwitchTags.RESET_ALL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.game_offline)).assertDoesNotExist()
    }

    @Test
    fun `picking a game sends it`() {
        show()
        click(StateSwitchTags.GAME)
        compose.onNodeWithTag("${StateSwitchTags.GAME}.go-stop").performClick()
        assertEquals(listOf(base.copy(game = "go-stop")), changes)
        compose.onNodeWithText("Stop / Go").assertIsDisplayed()
    }

    @Test
    fun `every game type is offered`() {
        show()
        click(StateSwitchTags.GAME)
        GameType.entries.forEach { compose.onNodeWithTag("${StateSwitchTags.GAME}.${it.slug}").assertExists() }
    }

    @Test
    fun `the timeout is sent once on release, snapped to a tenth`() {
        show()
        slide(StateSwitchTags.TIMEOUT, 4.2f)
        assertEquals(listOf(base.copy(timeout = 4.2f)), changes)
        compose.onNodeWithTag(StateSwitchTags.TIMEOUT_VALUE).assertTextEquals("4.2 sec")
    }

    @Test
    fun `enforcing the limit sends it and shows the limit slider`() {
        show()
        click(StateSwitchTags.LIMIT_ENABLED)
        assertEquals(listOf(base.copy(limitEnabled = true)), changes)
        compose.onNodeWithTag(StateSwitchTags.LIMIT_ENABLED).assertIsOn()
        compose.onNodeWithTag(StateSwitchTags.LIMIT_VALUE).performScrollTo().assertTextEquals("20")

        slide(StateSwitchTags.LIMIT, 8f)
        assertEquals(base.copy(limitEnabled = true, limit = 8), changes.last())
        compose.onNodeWithTag(StateSwitchTags.LIMIT_VALUE).assertTextEquals("8")
    }

    @Test
    fun `switch presses the button`() {
        show()
        click(StateSwitchTags.SWITCH)
        assertEquals(1, switches)
    }

    @Test
    fun `switch fires on touch, not on release`() {
        show()
        compose.onNodeWithTag(StateSwitchTags.SWITCH).performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(1, switches)
        compose.onNodeWithTag(StateSwitchTags.SWITCH).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, switches)
    }

    @Test
    fun `switch says why it is held back`() {
        ui = ui.copy(cooldown = true)
        show()
        compose.onNodeWithTag(StateSwitchTags.SWITCH).assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.state_switch_cooling_down)).assertIsDisplayed()

        ui = ui.copy(cooldown = false, settings = base.copy(limitEnabled = true, limit = 4), state = StateSwitchState(actuations = 4))
        compose.onNodeWithText(s(R.string.state_switch_limit_reached)).assertIsDisplayed()
        compose.onNodeWithTag(StateSwitchTags.SWITCH).assertIsNotEnabled()
    }

    @Test
    fun `reset actuations acts at once`() {
        show()
        click(StateSwitchTags.RESET_ACTUATIONS)
        assertEquals(1, resetActuations)
    }

    @Test
    fun `reset all asks first`() {
        show()
        click(StateSwitchTags.RESET_ALL)
        assertEquals(0, resetAlls)
        compose.onNodeWithText(s(R.string.state_switch_reset_all_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        assertEquals(0, resetAlls)

        click(StateSwitchTags.RESET_ALL)
        compose.onNodeWithTag(StateSwitchTags.CONFIRM).performClick()
        assertEquals(1, resetAlls)
    }

    @Test
    fun `offline keeps the last settings but locks the controls`() {
        ui = ui.copy(connected = false)
        show()
        compose.onNodeWithText(s(R.string.game_offline)).assertIsDisplayed()
        compose.onNodeWithTag(StateSwitchTags.SWITCH).assertIsNotEnabled()
        compose.onNodeWithTag(StateSwitchTags.TIMEOUT).assertIsNotEnabled()
        compose.onNodeWithTag(StateSwitchTags.GAME).assertIsNotEnabled()
        compose.onNodeWithTag(StateSwitchTags.LIMIT_ENABLED).assertIsNotEnabled()
        compose.onNodeWithTag(StateSwitchTags.RESET_ALL).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(StateSwitchTags.RESET_ACTUATIONS).assertIsNotEnabled()
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `display mode opens once the settings are in`() {
        ui = StateSwitchUiState()
        show()
        compose.onNodeWithTag(StateSwitchTags.DISPLAY).assertIsNotEnabled()
        ui = StateSwitchUiState(connected = true, settings = base)
        compose.onNodeWithTag(StateSwitchTags.DISPLAY).assertIsEnabled().performClick()
        assertEquals(1, displays)
    }
}
