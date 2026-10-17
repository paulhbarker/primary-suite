package com.primarysuite.app.games.reelrush

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
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
class ReelRushScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private var ui by mutableStateOf(ReelRushUiState(connected = true, state = Fixtures.state()))
    private val calls = mutableListOf<String>()
    private val saved = mutableListOf<ReelRushSettings>()

    private fun show() {
        compose.setContent {
            PrimarySuiteTheme {
                ReelRushScreen(
                    ui = ui,
                    snackbar = SnackbarHostState(),
                    actions = ReelRushActions(
                        onOpenLobby = { calls += "open-lobby" },
                        onRemovePlayer = { calls += "remove-$it" },
                        onStart = { calls += "start" },
                        onCloseRound = { calls += "close" },
                        onNextRound = { calls += "next" },
                        onReplayRound = { calls += "replay" },
                        onEndGame = { calls += "end" },
                        onReset = { calls += "reset" },
                        onSaveSettings = { saved += it },
                        onBack = { calls += "back" },
                    ),
                )
            }
        }
    }

    private fun game(
        phase: String = "idle",
        round: Int = 0,
        suddenDeath: Boolean = false,
        players: List<String> = Fixtures.noPlayers,
        result: String? = null,
        history: List<String> = emptyList(),
        final: String? = null,
    ) {
        ui = ui.copy(state = Fixtures.state(phase, round, suddenDeath = suddenDeath, players = players, result = result, history = history, final = final))
    }

    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun settingsTab() = compose.onNodeWithTag(ReelRushTags.TAB_SETTINGS).performClick()

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
    }

    private val fourJoined = Fixtures.players(true, true, true, true)

    @Test
    fun `waits for the server's game`() {
        ui = ReelRushUiState()
        show()
        compose.onNodeWithTag(ReelRushTags.CONNECTING).assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.TAB_GAME).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.game_offline)).assertDoesNotExist()
    }

    @Test
    fun `back goes back`() {
        show()
        compose.onNodeWithContentDescription(s(R.string.cd_back)).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `idle offers to open the lobby and nothing to reset`() {
        show()
        compose.onNodeWithTag(ReelRushTags.PHASE).assertTextEquals(s(R.string.reelrush_phase_idle))
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_open_lobby))
        compose.onNodeWithTag(ReelRushTags.RESET).assertDoesNotExist()
        compose.onNodeWithTag(ReelRushTags.REPLAY).assertDoesNotExist()
        click(ReelRushTags.PRIMARY)
        assertEquals(listOf("open-lobby"), calls)
    }

    @Test
    fun `the lobby needs two players to start`() {
        game("lobby", players = Fixtures.players(true, false, false, false))
        show()
        compose.onNodeWithTag(ReelRushTags.PHASE).assertTextEquals(s(R.string.reelrush_phase_lobby))
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_start_needs, 2, 1)).assertIsNotEnabled()

        game("lobby", players = Fixtures.players(true, true, true, false))
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_start_with, 3)).assertIsEnabled()
        click(ReelRushTags.PRIMARY)
        assertEquals(listOf("start"), calls)
        compose.onNodeWithTag(ReelRushTags.REPLAY).assertDoesNotExist()
        compose.onNodeWithTag(ReelRushTags.RESET).assertIsDisplayed()
    }

    @Test
    fun `remove shows for joined players in the lobby only`() {
        game("lobby", players = Fixtures.players(true, false, true, false))
        show()
        compose.onNodeWithTag(ReelRushTags.remove(1)).assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.remove(2)).assertDoesNotExist()
        compose.onNodeWithTag(ReelRushTags.remove(3)).assertIsDisplayed()
        click(ReelRushTags.remove(3))
        assertEquals(listOf("remove-3"), calls)

        game("intro", round = 1, players = fourJoined)
        compose.onNodeWithTag(ReelRushTags.remove(1)).assertDoesNotExist()
    }

    @Test
    fun `players show names, score, fouls and not joined`() {
        val players = listOf(
            """{"slot":1,"name":"GREEN","color":"green","joined":true,"score":7,"fouls":2,"status":"foul"}""",
            """{"slot":2,"name":"YELLOW","color":"yellow","joined":true,"score":3,"fouls":0,"status":"pressed","reactionMs":234,"rank":1}""",
            """{"slot":3,"name":"WHITE","color":"white","joined":false,"score":0,"fouls":0,"status":"out"}""",
            """{"slot":4,"name":"BLUE","color":"blue","joined":true,"score":1,"fouls":0,"status":"waiting"}""",
        )
        game("window", round = 1, players = players)
        show()
        compose.onNodeWithTag(ReelRushTags.player(1)).assertTextContains("GREEN").assertTextContains("2F").assertTextContains("7").assertTextContains("FOUL")
        compose.onNodeWithTag(ReelRushTags.player(2)).assertTextContains("pressed #1 0.234s")
        compose.onNodeWithTag(ReelRushTags.player(3)).assertTextContains(s(R.string.reelrush_not_joined))
        compose.onNodeWithTag(ReelRushTags.player(4)).assertTextContains("waiting")
    }

    @Test
    fun `a round in progress shows the round and offers replay and end game`() {
        game("spinning", round = 2, players = fourJoined)
        show()
        compose.onNodeWithTag(ReelRushTags.PHASE).assertTextEquals(s(R.string.reelrush_phase_spinning))
        compose.onNodeWithText(s(R.string.reelrush_round_of, 2, 5)).assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertDoesNotExist()
        click(ReelRushTags.REPLAY)
        assertEquals(listOf("replay"), calls)
        listOf("intro", "window").forEach { phase ->
            game(phase, round = 2, players = fourJoined)
            compose.onNodeWithTag(ReelRushTags.REPLAY).assertIsDisplayed()
            compose.onNodeWithTag(ReelRushTags.END_GAME).assertIsDisplayed()
        }
    }

    @Test
    fun `sudden death replaces the round count`() {
        game("intro", round = 5, suddenDeath = true, players = fourJoined)
        show()
        compose.onNodeWithText(s(R.string.reelrush_sudden_death)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.reelrush_round_of, 5, 5)).assertDoesNotExist()
    }

    @Test
    fun `the press window offers close round`() {
        game("window", round = 1, players = fourJoined)
        show()
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_close_round))
        click(ReelRushTags.PRIMARY)
        assertEquals(listOf("close"), calls)
    }

    @Test
    fun `results offer next round and show the round table`() {
        game("results", round = 2, players = fourJoined, result = Fixtures.result)
        show()
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_next_round))
        click(ReelRushTags.PRIMARY)
        assertEquals(listOf("next"), calls)
        compose.onNodeWithTag(ReelRushTags.LAST_ROUND).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.result(2)).performScrollTo().assertTextContains("YELLOW").assertTextContains("0.234s").assertTextContains("+3")
        compose.onNodeWithTag(ReelRushTags.result(3)).assertTextContains(s(R.string.reelrush_foul))
        compose.onNodeWithTag(ReelRushTags.result(4)).assertTextContains("-")
    }

    @Test
    fun `the next button changes label like the web`() {
        val voided = """{"round":2,"voided":true,"entries":[],"winners":[]}"""
        val one = """{"round":5,"suddenDeath":true,"entries":[],"winners":[2]}"""
        val tie = """{"round":5,"suddenDeath":true,"entries":[],"winners":[1,2]}"""
        val plain = """{"round":5,"entries":[],"winners":[2]}"""
        game("results", 2, players = fourJoined, result = voided)
        show()
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_replay_the_round))
        compose.onNodeWithText(s(R.string.reelrush_voided)).assertExists()
        game("results", 5, suddenDeath = true, players = fourJoined, result = one)
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_show_winner))
        game("results", 5, suddenDeath = true, players = fourJoined, result = tie)
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_another_sudden_death))
        game("results", 5, players = fourJoined, result = plain)
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_finish_game))
    }

    @Test
    fun `the final standings show the winner and titles, and offer the lobby`() {
        game("final", round = 5, players = fourJoined, final = Fixtures.final, history = listOf(Fixtures.result))
        show()
        compose.onNodeWithTag(ReelRushTags.PHASE).assertTextEquals(s(R.string.reelrush_phase_final))
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertTextContains(s(R.string.reelrush_back_to_lobby))
        compose.onNodeWithTag(ReelRushTags.FINAL).performScrollTo().assertTextContains(s(R.string.reelrush_wins_sudden_death, "YELLOW"))
        compose.onNodeWithTag(ReelRushTags.FINAL).assertTextContains("9 pts").assertTextContains("best 0.180s")
        compose.onNodeWithTag(ReelRushTags.FINAL).assertTextContains("Jumpiest: GREEN & WHITE (2 fouls)")
        compose.onNodeWithTag(ReelRushTags.LAST_ROUND).assertDoesNotExist()
        compose.onNodeWithTag(ReelRushTags.REPLAY).assertDoesNotExist()
        click(ReelRushTags.PRIMARY)
        assertEquals(listOf("open-lobby"), calls)
    }

    @Test
    fun `end game asks first`() {
        game("results", round = 1, players = fourJoined, result = Fixtures.result)
        show()
        click(ReelRushTags.END_GAME)
        assertTrue(calls.isEmpty())
        compose.onNodeWithText(s(R.string.reelrush_end_game_message)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        assertTrue(calls.isEmpty())

        click(ReelRushTags.END_GAME)
        compose.onNodeWithTag(ReelRushTags.CONFIRM).performClick()
        assertEquals(listOf("end"), calls)
    }

    @Test
    fun `reset asks first`() {
        game("lobby", players = fourJoined)
        show()
        click(ReelRushTags.RESET)
        assertTrue(calls.isEmpty())
        compose.onNodeWithText(s(R.string.reelrush_reset_message)).assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.CONFIRM).performClick()
        assertEquals(listOf("reset"), calls)
    }

    @Test
    fun `offline keeps the game but locks every control`() {
        game("results", round = 1, players = fourJoined, result = Fixtures.result)
        ui = ui.copy(connected = false)
        show()
        compose.onNodeWithText(s(R.string.game_offline)).assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.PRIMARY).assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.REPLAY).assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.END_GAME).assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.RESET).assertIsNotEnabled()
        settingsTab()
        compose.onNodeWithTag(ReelRushTags.REELS).assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.HARD_MODE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.VOLUME).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.name(0)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsNotEnabled()
        assertTrue(calls.isEmpty())
    }

    // Settings

    @Test
    fun `settings show the server's values`() {
        show()
        settingsTab()
        assertEquals("3", fieldText(ReelRushTags.REELS))
        assertEquals("5", fieldText(ReelRushTags.ROUNDS))
        assertEquals("1", fieldText(ReelRushTags.ROUND_MIN))
        assertEquals("10", fieldText(ReelRushTags.ROUND_MAX))
        assertEquals("2", fieldText(ReelRushTags.NEAR_MISSES))
        assertEquals("25", fieldText(ReelRushTags.SPIN_SPEED))
        assertEquals("0.25", fieldText(ReelRushTags.DWELL_MIN))
        assertEquals("1.5", fieldText(ReelRushTags.DWELL_MAX))
        assertEquals("50", fieldText(ReelRushTags.DISPLAY_LAG))
        compose.onNodeWithTag(ReelRushTags.HARD_MODE).performScrollTo().assertIsOff()
        compose.onNodeWithTag(ReelRushTags.ASSIST).performScrollTo().assertIsOff()
        compose.onNodeWithTag(ReelRushTags.ROW_DECOYS).performScrollTo().assertIsOn()
        compose.onNodeWithTag(ReelRushTags.VOLUME).performScrollTo().assertIsDisplayed()
        listOf("GREEN", "YELLOW", "WHITE", "BLUE").forEachIndexed { i, n -> assertEquals(n, fieldText(ReelRushTags.name(i))) }
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.DISCARD).assertIsNotEnabled()
    }

    @Test
    fun `an edit stays local until save, then sends the whole settings`() {
        show()
        settingsTab()
        type(ReelRushTags.REELS, "4")
        compose.onNodeWithTag(ReelRushTags.HARD_MODE).performScrollTo().performClick()
        type(ReelRushTags.ROUND_MIN, "2.5")
        type(ReelRushTags.name(1), "ann")
        assertTrue(saved.isEmpty())
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsEnabled().performClick()
        assertEquals(
            listOf(
                Fixtures.settings.copy(
                    reels = 4, hardMode = true, roundMinMs = 2500,
                    names = listOf("GREEN", "ann", "WHITE", "BLUE"),
                ),
            ),
            saved,
        )
    }

    @Test
    fun `the draft survives a tab switch`() {
        show()
        settingsTab()
        type(ReelRushTags.ROUNDS, "9")
        compose.onNodeWithTag(ReelRushTags.TAB_GAME).performClick()
        compose.onNodeWithTag(ReelRushTags.PHASE).assertIsDisplayed()
        settingsTab()
        assertEquals("9", fieldText(ReelRushTags.ROUNDS))
    }

    @Test
    fun `discard puts the server's values back`() {
        show()
        settingsTab()
        type(ReelRushTags.REELS, "5")
        compose.onNodeWithTag(ReelRushTags.ASSIST).performScrollTo().performClick()
        compose.onNodeWithTag(ReelRushTags.DISCARD).performScrollTo().assertIsEnabled().performClick()
        assertEquals("3", fieldText(ReelRushTags.REELS))
        compose.onNodeWithTag(ReelRushTags.ASSIST).performScrollTo().assertIsOff()
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(ReelRushTags.DISCARD).assertIsNotEnabled()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a new server value replaces an unsaved draft`() {
        show()
        settingsTab()
        type(ReelRushTags.REELS, "5")
        ui = ui.copy(state = Fixtures.state(settings = Fixtures.settings.copy(rounds = 12).toJson()))
        compose.waitForIdle()
        assertEquals("3", fieldText(ReelRushTags.REELS))
        assertEquals("12", fieldText(ReelRushTags.ROUNDS))
    }

    @Test
    fun `other game updates leave the draft alone`() {
        show()
        settingsTab()
        type(ReelRushTags.REELS, "5")
        game("lobby", players = Fixtures.players(true, false, false, false))
        compose.waitForIdle()
        assertEquals("5", fieldText(ReelRushTags.REELS))
    }

    @Test
    fun `a bad value shows why and blocks save`() {
        show()
        settingsTab()
        type(ReelRushTags.REELS, "9")
        compose.onNodeWithText(s(R.string.reelrush_problem_out_of_range, "2", "5")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsNotEnabled()

        type(ReelRushTags.REELS, "")
        compose.onNodeWithText(s(R.string.reelrush_problem_not_a_number)).performScrollTo().assertIsDisplayed()

        type(ReelRushTags.REELS, "4")
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsEnabled()

        type(ReelRushTags.ROUND_MAX, "1")
        type(ReelRushTags.ROUND_MIN, "3")
        compose.onNodeWithText(s(R.string.reelrush_problem_max_below_min)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `number fields refuse letters and names are capped at ten characters`() {
        show()
        settingsTab()
        type(ReelRushTags.REELS, "4x")
        assertEquals("3", fieldText(ReelRushTags.REELS))
        type(ReelRushTags.name(0), "ABCDEFGHIJKLM")
        assertEquals("ABCDEFGHIJ", fieldText(ReelRushTags.name(0)))
    }

    @Test
    fun `the volume slider changes the draft in twentieths`() {
        show()
        settingsTab()
        compose.onNodeWithTag(ReelRushTags.VOLUME).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.35f) }
        compose.onNodeWithText("35%").assertIsDisplayed()
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsEnabled().performClick()
        assertEquals(0.35, saved.single().volume, 0.0)
    }

    @Test
    fun `typing the server's own value is not a change`() {
        show()
        settingsTab()
        type(ReelRushTags.ROUND_MIN, "1.0")
        compose.onNodeWithTag(ReelRushTags.SAVE).performScrollTo().assertIsNotEnabled()
    }
}
