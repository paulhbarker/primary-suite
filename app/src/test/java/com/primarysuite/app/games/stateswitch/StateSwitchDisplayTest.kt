package com.primarysuite.app.games.stateswitch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class StateSwitchDisplayTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings = StateSwitchSettings("fast-slow", 2.5f, limitEnabled = false, limit = 20)
    private var ui by mutableStateOf(StateSwitchUiState(connected = true, settings = settings))

    private fun show() {
        compose.setContent { PrimarySuiteTheme { StateSwitchDisplay(ui) } }
    }

    @Test
    fun `sides follow the web display's order`() {
        assertEquals("Slow", StateSlides.of("fast-slow", 0)?.word)
        assertEquals("Fast", StateSlides.of("fast-slow", 1)?.word)
        assertEquals("Go", StateSlides.of("go-stop", 0)?.word)
        assertEquals("Legato", StateSlides.of("staccato-legato", 0)?.word)
        assertEquals("Marching", StateSlides.of("clapping-marching", 0)?.word)
        assertEquals("Standing", StateSlides.of("standing-sitting", 0)?.word)
    }

    @Test
    fun `every pair has two different sides`() {
        GameType.entries.filter { it != GameType.RANDOM }.forEach { type ->
            val a = StateSlides.of(type.slug, 0)!!
            val b = StateSlides.of(type.slug, 1)!!
            assertNotEquals(type.slug, a.word, b.word)
        }
    }

    @Test
    fun `random and unknown games show nothing, like the web`() {
        assertNull(StateSlides.of("random", 0))
        assertNull(StateSlides.of("mystery", 0))
        assertNull(StateSlides.of("fast-slow", 2))
    }

    @Test
    fun `shows the current side and follows the server`() {
        show()
        compose.onNodeWithTag(StateSwitchDisplayTags.WORD).assertTextEquals("Slow")
        ui = ui.copy(state = StateSwitchState(actuations = 1, current = 1))
        compose.onNodeWithTag(StateSwitchDisplayTags.WORD).assertTextEquals("Fast")
        ui = ui.copy(settings = settings.copy(game = "left-right"))
        compose.onNodeWithTag(StateSwitchDisplayTags.WORD).assertTextEquals("Right")
    }

    @Test
    fun `random shows a blank screen`() {
        ui = ui.copy(settings = settings.copy(game = "random"))
        show()
        compose.onNodeWithTag(StateSwitchDisplayTags.ROOT).assertExists()
        compose.onNodeWithTag(StateSwitchDisplayTags.WORD).assertDoesNotExist()
    }

    @Test
    fun `waits for the server's settings`() {
        ui = StateSwitchUiState()
        show()
        compose.onNodeWithText("Connecting…").assertExists()
    }
}
