package com.primarysuite.app.games.spinwheel

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SpinWheelScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private var ui by mutableStateOf(SpinWheelUiState(connected = true, state = Fixtures.state()))
    private val calls = mutableListOf<String>()
    private val saved = mutableListOf<WheelSettings>()

    private fun show() {
        compose.setContent {
            PrimarySuiteTheme {
                SpinWheelScreen(
                    ui = ui,
                    snackbar = SnackbarHostState(),
                    actions = SpinWheelActions(
                        onSpin = { calls += "spin" },
                        onSetLocked = { calls += "locked-$it" },
                        onForceNext = { calls += "force-$it" },
                        onSetWeight = { v, w -> calls += "weight-$v-$w" },
                        onEvenOdds = { calls += "even" },
                        onClearHistory = { calls += "clear" },
                        onSaveSettings = { saved += it },
                        onBack = { calls += "back" },
                    ),
                )
            }
        }
    }

    private fun wheel(state: WheelState) {
        ui = ui.copy(state = state)
        compose.waitForIdle()
    }

    @Test
    fun `says connecting until the wheel arrives`() {
        ui = SpinWheelUiState()
        show()
        compose.onNodeWithTag(SpinWheelTags.CONNECTING).assertIsDisplayed()
        ui = SpinWheelUiState(connected = true, state = Fixtures.state())
        compose.onNodeWithTag(SpinWheelTags.SPIN).assertIsDisplayed()
    }

    @Test
    fun `the status line follows the phase like the web`() {
        show()
        compose.onNodeWithTag(SpinWheelTags.PHASE).assertTextEquals(s(R.string.spinwheel_phase_armed))
        wheel(Fixtures.state(locked = true))
        compose.onNodeWithTag(SpinWheelTags.PHASE).assertTextEquals(s(R.string.spinwheel_phase_locked))
        wheel(Fixtures.state(phase = "spinning", spinValue = 400))
        compose.onNodeWithTag(SpinWheelTags.PHASE).assertTextEquals(s(R.string.spinwheel_phase_spinning))
        wheel(Fixtures.state(phase = "landed", spinValue = 400))
        compose.onNodeWithTag(SpinWheelTags.PHASE).assertTextEquals(s(R.string.spinwheel_phase_landed))
    }

    @Test
    fun `spin is off while spinning and on during the celebration`() {
        show()
        compose.onNodeWithTag(SpinWheelTags.SPIN).performClick()
        wheel(Fixtures.state(phase = "spinning", spinValue = 400))
        compose.onNodeWithTag(SpinWheelTags.SPIN).assertIsNotEnabled()
        wheel(Fixtures.state(phase = "landed", spinValue = 400))
        compose.onNodeWithTag(SpinWheelTags.SPIN).assertIsEnabled().performClick()
        assertEquals(listOf("spin", "spin"), calls)
    }

    @Test
    fun `controls are off while offline`() {
        ui = ui.copy(connected = false)
        show()
        compose.onNodeWithTag(SpinWheelTags.SPIN).assertIsNotEnabled()
        compose.onNodeWithTag(SpinWheelTags.LOCK).assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.game_offline)).assertIsDisplayed()
    }

    @Test
    fun `the lock switch shows and sets the lock`() {
        show()
        compose.onNodeWithTag(SpinWheelTags.LOCK).assertIsOff().performClick()
        wheel(Fixtures.state(locked = true))
        compose.onNodeWithTag(SpinWheelTags.LOCK).assertIsOn().performClick()
        assertEquals(listOf("locked-true", "locked-false"), calls)
    }

    @Test
    fun `forcing picks a value, and tapping it again or Clear unrigs`() {
        show()
        compose.onNodeWithTag(SpinWheelTags.force(700)).performScrollTo().performClick()
        wheel(Fixtures.state(forcedNext = 700))
        compose.onNodeWithTag(SpinWheelTags.FORCE_TITLE).performScrollTo().assertTextContains(s(R.string.spinwheel_rigged))
        compose.onNodeWithTag(SpinWheelTags.force(700)).assertIsSelected().performClick()
        compose.onNodeWithTag(SpinWheelTags.FORCE_CLEAR).performScrollTo().performClick()
        assertEquals(listOf("force-700", "force-null", "force-null"), calls)
    }

    @Test
    fun `the last result and history say who spun it`() {
        val history = listOf(Fixtures.result(2, 500, button = 4, rigged = true), Fixtures.result(1, 500))
        show()
        wheel(Fixtures.state(lastResult = history[0], history = history))
        compose.onNodeWithTag(SpinWheelTags.LAST_RESULT).assertTextContains("blue button, rigged", substring = true)
        compose.onNodeWithTag(SpinWheelTags.count(500)).performScrollTo().assertTextEquals("2")
        compose.onNodeWithTag(SpinWheelTags.count(100)).assertTextEquals("0")
        compose.onNodeWithTag(SpinWheelTags.historyRow(1)).performScrollTo().assertTextContains("admin", substring = true)
        compose.onNodeWithTag(SpinWheelTags.HISTORY_TITLE).assertTextContains(context.resources.getQuantityString(R.plurals.spinwheel_history_count, 2, 2))
    }

    @Test
    fun `clear history asks first`() {
        show()
        wheel(Fixtures.state(history = listOf(Fixtures.result(1, 300))))
        compose.onNodeWithTag(SpinWheelTags.CLEAR_HISTORY).performScrollTo().performClick()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithTag(SpinWheelTags.CONFIRM).performClick()
        assertEquals(listOf("clear"), calls)
    }

    @Test
    fun `odds step each weight and keep one above zero`() {
        show()
        compose.onNodeWithTag(SpinWheelTags.TAB_ODDS).performClick()
        compose.onNodeWithTag(SpinWheelTags.EVEN).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SpinWheelTags.more(300)).performClick()
        compose.onNodeWithTag(SpinWheelTags.less(300)).performClick()
        assertEquals(listOf("weight-300-2", "weight-300-0"), calls)

        wheel(Fixtures.state(weights = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 10)))
        compose.onNodeWithTag(SpinWheelTags.weight(1000)).performScrollTo().assertTextEquals("10")
        compose.onNodeWithTag(SpinWheelTags.less(1000)).assertIsNotEnabled()
        compose.onNodeWithTag(SpinWheelTags.more(1000)).assertIsNotEnabled()
        compose.onNodeWithTag(SpinWheelTags.less(100)).assertIsNotEnabled()
        compose.onNodeWithTag(SpinWheelTags.EVEN).performScrollTo().assertIsEnabled().performClick()
        assertEquals("even", calls.last())
    }

    @Test
    fun `settings save the edit and discard throws it away`() {
        show()
        compose.onNodeWithTag(SpinWheelTags.TAB_SETTINGS).performClick()
        compose.onNodeWithTag(SpinWheelTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(SpinWheelTags.SPIN_MIN).performTextReplacement("5")
        compose.onNodeWithTag(SpinWheelTags.CELEBRATE).performTextReplacement("2.5")
        compose.onNodeWithTag(SpinWheelTags.SAVE).assertIsEnabled().performClick()
        assertEquals(listOf(WheelSettings(5000, 10000, 2500, 0.8)), saved)

        compose.onNodeWithTag(SpinWheelTags.SPIN_MAX).performTextReplacement("6")
        compose.onNodeWithTag(SpinWheelTags.DISCARD).performClick()
        compose.onNodeWithTag(SpinWheelTags.SPIN_MAX).assertTextContains("10")
    }

    @Test
    fun `a bad setting says why and blocks save`() {
        show()
        compose.onNodeWithTag(SpinWheelTags.TAB_SETTINGS).performClick()
        compose.onNodeWithTag(SpinWheelTags.SPIN_MAX).performTextReplacement("7")
        compose.onNodeWithText(s(R.string.spinwheel_problem_max_below_min)).assertIsDisplayed()
        compose.onNodeWithTag(SpinWheelTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(SpinWheelTags.SPIN_MAX).performTextReplacement("20")
        compose.onNodeWithText(s(R.string.spinwheel_problem_out_of_range, "3", "15")).assertIsDisplayed()
    }
}
