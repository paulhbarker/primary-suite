package com.primarysuite.app.games.blockstacker

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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
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
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class BlockStackerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val server = BlockStackerSettings(15, 1.7, 0.8, true)
    private val state = BlockStackerState("waiting", 3, 9, Instant.parse("2026-10-17T08:15:30Z"))
    private var ui by mutableStateOf(BlockStackerUiState(connected = true, draft = server, server = server, state = state))

    private var saves = 0
    private var resets = 0
    private var presses = 0
    private var backs = 0

    private fun show() {
        compose.setContent {
            PrimarySuiteTheme {
                BlockStackerScreen(
                    ui = ui,
                    snackbar = SnackbarHostState(),
                    onEdit = { change -> ui = ui.copy(draft = change(ui.draft)) },
                    onSave = { saves++ },
                    onResetDefaults = { resets++; ui = ui.copy(draft = BlockStackerSettings.DEFAULTS) },
                    onTestPress = { presses++ },
                    onBack = { backs++ },
                    zone = ZoneId.of("UTC"),
                )
            }
        }
    }

    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun slide(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    @Test
    fun `waits for the server's settings`() {
        ui = BlockStackerUiState()
        show()
        compose.onNodeWithText(s(R.string.blockstacker_connecting)).assertIsDisplayed()
        compose.onNodeWithTag(BlockStackerTags.SAVE).assertDoesNotExist()
    }

    @Test
    fun `shows the web admin's controls with the server's values`() {
        show()
        compose.onNodeWithText(s(R.string.blockstacker_max_levels)).assertIsDisplayed()
        compose.onNodeWithTag(BlockStackerTags.MAX_LEVELS_VALUE).assertTextEquals("15")
        compose.onNodeWithTag(BlockStackerTags.INITIAL_SPEED_VALUE).assertTextEquals("1.7")
        compose.onNodeWithTag(BlockStackerTags.SPEED_INCREMENT_VALUE).assertTextEquals("0.8")
        compose.onNodeWithTag(BlockStackerTags.GAME_ENABLED).assertIsOn()
        compose.onNodeWithTag(BlockStackerTags.SAVE).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(BlockStackerTags.RESET).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(BlockStackerTags.TEST_PRESS).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(BlockStackerTags.UNSAVED).assertDoesNotExist()
    }

    @Test
    fun `shows the game status, how to play and the empty log`() {
        show()
        compose.onNodeWithTag(BlockStackerTags.STATUS_VALUE, useUnmergedTree = true).performScrollTo().assertTextEquals("waiting")
        compose.onNodeWithTag(BlockStackerTags.TOTAL_GAMES + ".value", useUnmergedTree = true).assertTextEquals("3")
        compose.onNodeWithTag(BlockStackerTags.HIGH_SCORE + ".value", useUnmergedTree = true).assertTextEquals("9 levels")
        compose.onNodeWithTag(BlockStackerTags.LAST_UPDATED + ".value", useUnmergedTree = true).performScrollTo().assertTextContains("2026", substring = true)
        compose.onNodeWithText("•  " + s(R.string.blockstacker_how_5)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(BlockStackerTags.LOG_EMPTY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `shows the log, newest first`() {
        ui = ui.copy(log = listOf(LogEntry(1_000L, "button-press", """{"id":1}"""), LogEntry(500L, "block-stacker-game-state", "{}")))
        show()
        compose.onNodeWithText("button-press: {\"id\":1}", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("block-stacker-game-state: {}", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(BlockStackerTags.LOG_EMPTY).assertDoesNotExist()
    }

    @Test
    fun `sliders edit the draft and send nothing`() {
        show()
        slide(BlockStackerTags.MAX_LEVELS, 30f)
        slide(BlockStackerTags.INITIAL_SPEED, 4.2f)
        slide(BlockStackerTags.SPEED_INCREMENT, 1.5f)
        click(BlockStackerTags.GAME_ENABLED)
        assertEquals(BlockStackerSettings(30, 4.2, 1.5, false), ui.draft)
        compose.onNodeWithTag(BlockStackerTags.MAX_LEVELS_VALUE).assertTextEquals("30")
        compose.onNodeWithTag(BlockStackerTags.INITIAL_SPEED_VALUE).assertTextEquals("4.2")
        compose.onNodeWithTag(BlockStackerTags.SPEED_INCREMENT_VALUE).assertTextEquals("1.5")
        compose.onNodeWithTag(BlockStackerTags.GAME_ENABLED).assertIsOff()
        compose.onNodeWithTag(BlockStackerTags.UNSAVED).performScrollTo().assertIsDisplayed()
        assertEquals(0, saves)
    }

    @Test
    fun `save sends once`() {
        show()
        click(BlockStackerTags.SAVE)
        assertEquals(1, saves)
    }

    @Test
    fun `reset to defaults changes the draft only`() {
        ui = ui.copy(draft = server.copy(maxLevels = 40, gameEnabled = false))
        show()
        click(BlockStackerTags.RESET)
        assertEquals(1, resets)
        assertEquals(0, saves)
        compose.onNodeWithTag(BlockStackerTags.MAX_LEVELS_VALUE).assertTextEquals("15")
        compose.onNodeWithTag(BlockStackerTags.GAME_ENABLED).assertIsOn()
    }

    @Test
    fun `test button press sends a press on touch, not on release`() {
        show()
        compose.onNodeWithTag(BlockStackerTags.TEST_PRESS).performScrollTo().performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(1, presses)
        compose.onNodeWithTag(BlockStackerTags.TEST_PRESS).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, presses)
    }

    @Test
    fun `offline disables save and test press and shows the badge`() {
        ui = ui.copy(connected = false)
        show()
        compose.onNodeWithText(s(R.string.game_offline)).assertIsDisplayed()
        compose.onNodeWithTag(BlockStackerTags.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(BlockStackerTags.TEST_PRESS).performScrollTo().assertIsNotEnabled()
        // Editing the draft is still possible, as on the web.
        slide(BlockStackerTags.MAX_LEVELS, 8f)
        assertEquals(8, ui.draft.maxLevels)
    }

    @Test
    fun `back goes back`() {
        show()
        compose.onNodeWithContentDescription(s(R.string.cd_back)).performClick()
        assertTrue(backs == 1)
    }
}
