package com.primarysuite.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PressButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private var presses = 0
    private var enabled by mutableStateOf(true)

    private fun show() = compose.setContent {
        PrimarySuiteTheme { PressButton("Switch", onPress = { presses++ }, enabled = enabled, modifier = Modifier.testTag("b")) }
    }

    @Test
    fun `fires as soon as the finger lands, before release`() {
        show()
        compose.onNodeWithTag("b").performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(1, presses)

        // Holding doesn't repeat, and lifting doesn't fire again.
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("b").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, presses)
    }

    @Test
    fun `each tap is one press`() {
        show()
        repeat(3) {
            compose.onNodeWithTag("b").performTouchInput { down(center); up() }
        }
        compose.waitForIdle()
        assertEquals(3, presses)
    }

    @Test
    fun `disabled does nothing`() {
        enabled = false
        show()
        compose.onNodeWithTag("b").assertIsNotEnabled().performTouchInput { down(center); up() }
        compose.waitForIdle()
        assertEquals(0, presses)

        enabled = true
        compose.onNodeWithTag("b").assertIsEnabled().performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(1, presses)
    }

    @Test
    fun `accessibility services can still press it`() {
        show()
        compose.onNodeWithTag("b").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, presses)
    }
}
