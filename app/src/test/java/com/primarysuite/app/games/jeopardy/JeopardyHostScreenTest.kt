package com.primarysuite.app.games.jeopardy

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.ui.theme.PrimarySuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The host screen against fixture states. Each test checks that the controls
 * for a phase appear and send exactly what the web host would.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1600dp")
class JeopardyHostScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val sent = mutableListOf<GameMessage>()
    private var refreshes = 0
    private var backs = 0
    private var state by mutableStateOf<HostState?>(null)
    private var boards by mutableStateOf(BoardsState(boards = listOf(BoardSummary("b1", "Science Night", 2), BoardSummary("b2", "Kids", 1))))
    private var countdown by mutableStateOf(Countdown.IDLE)
    private var connected by mutableStateOf(true)

    private fun show(initial: HostState?) {
        state = initial
        compose.setContent {
            PrimarySuiteTheme {
                JeopardyHostScreen(
                    state = state,
                    connected = connected,
                    boards = boards,
                    countdown = countdown,
                    serverBase = "http://10.0.0.1:8080/",
                    snackbar = SnackbarHostState(),
                    send = { sent += it },
                    onRefreshBoards = { refreshes++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    /** Clicks a node, scrolling to it first when it sits in a scrolling column. */
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
    }
    private fun confirmDialog() = compose.onNodeWithTag(JeopardyTags.CONFIRM).performClick()

    private val lobby = Fixtures.state("lobby")
    private val board = Fixtures.state("board")
    private val answering = Fixtures.state("answering")
    private val reading = answering.copy(phase = Phase.CLUE, clue = answering.clue!!.copy(responder = null, lockedOut = emptyList()))

    // --- Shell ---

    @Test
    fun `waits for the server`() {
        connected = false
        show(null)
        compose.onNodeWithText(s(R.string.jeopardy_connecting)).assertIsDisplayed()
    }

    @Test
    fun `header shows the phase, offline, and undo`() {
        connected = false
        show(board)
        compose.onNodeWithTag(JeopardyTags.PHASE).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.phase_board)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.jeopardy_offline)).assertIsDisplayed()

        click(JeopardyTags.UNDO)
        assertEquals(listOf(HostCommands.undo()), sent)
    }

    @Test
    fun `undo is off when there's nothing to undo`() {
        show(lobby)
        compose.onNodeWithTag(JeopardyTags.UNDO).assertIsNotEnabled()
    }

    @Test
    fun `reset and end game are confirmed first`() {
        show(board)
        click(JeopardyTags.MORE)
        compose.onNodeWithText(s(R.string.jeopardy_end_game)).performClick()
        compose.onNodeWithText(s(R.string.jeopardy_end_game_confirm)).assertIsDisplayed()
        assertTrue(sent.isEmpty())
        confirmDialog()

        click(JeopardyTags.MORE)
        compose.onNodeWithText(s(R.string.jeopardy_reset_game)).performClick()
        confirmDialog()

        assertEquals(listOf(HostCommands.endGame(), HostCommands.resetGame()), sent)
    }

    @Test
    fun `there's no end game in the lobby`() {
        show(lobby)
        click(JeopardyTags.MORE)
        compose.onNodeWithText(s(R.string.jeopardy_end_game)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.jeopardy_reset_game)).assertIsDisplayed()
    }

    @Test
    fun `the assign banner can be cancelled`() {
        show(answering) // assigningButtonFor = p3 (Cat)
        compose.onNodeWithText(s(R.string.jeopardy_assign_banner, "Cat")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        assertEquals(listOf(HostCommands.assignButton(null)), sent)
    }

    @Test
    fun `back leaves the screen`() {
        show(lobby)
        compose.onNodeWithContentDescription(s(R.string.cd_back)).performClick()
        assertEquals(1, backs)
    }

    // --- Lobby ---

    @Test
    fun `lobby picks a board and first pick, then starts`() {
        show(lobby)
        compose.onNodeWithTag(JeopardyTags.LOBBY_START).assertIsNotEnabled()

        click(JeopardyTags.LOBBY_BOARD)
        compose.onNodeWithTag("${JeopardyTags.LOBBY_BOARD}.b1").performClick()
        click(JeopardyTags.LOBBY_FIRST_PICK)
        compose.onNodeWithTag("${JeopardyTags.LOBBY_FIRST_PICK}.p2").performClick()
        click(JeopardyTags.LOBBY_FIRST_PICK)
        compose.onNodeWithTag("${JeopardyTags.LOBBY_FIRST_PICK}.__random__").performClick()

        state = lobby.copy(boardId = "b1", boardName = "Science Night")
        click(JeopardyTags.LOBBY_START)

        assertEquals(
            listOf(HostCommands.loadBoard("b1"), HostCommands.setControl("p2"), HostCommands.setControl(null), HostCommands.startGame()),
            sent,
        )
    }

    @Test
    fun `lobby refreshes boards and shows load errors`() {
        boards = BoardsState(error = "Couldn't reach the game server")
        show(lobby)
        compose.onNodeWithText("Couldn't reach the game server").assertIsDisplayed()
        click(JeopardyTags.LOBBY_REFRESH)
        assertEquals(1, refreshes)
    }

    @Test
    fun `lobby notes a loaded board that's no longer listed`() {
        show(lobby.copy(boardId = "gone", boardName = "Old Board"))
        compose.onNodeWithText(s(R.string.lobby_loaded_copy, "Old Board")).assertIsDisplayed()
    }

    // --- Round intro, board, round end, game over ---

    @Test
    fun `round intro reveals categories, then starts the round`() {
        val round = board.round!!
        val categories = round.categories.mapIndexed { i, c -> if (i == 0) c else c.copy(revealed = false) }
        show(board.copy(phase = Phase.ROUND_INTRO, round = round.copy(categories = categories)))

        compose.onNodeWithText(s(R.string.intro_now_showing).uppercase()).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.intro_reveal, 2, 5, "CAT 2")).assertIsDisplayed()
        click(JeopardyTags.PRIMARY)

        state = board.copy(phase = Phase.ROUND_INTRO)
        compose.onNodeWithText(s(R.string.intro_start_round)).assertIsDisplayed()
        click(JeopardyTags.PRIMARY)

        assertEquals(List(2) { HostCommands.next() }, sent)
    }

    @Test
    fun `board selects available tiles only and marks the daily double`() {
        show(board)
        compose.onNodeWithText(s(R.string.board_pick, "Amy")).assertIsDisplayed()
        compose.onNodeWithText("DD").assertIsDisplayed()
        compose.onNodeWithTag(JeopardyTags.tile(5, 0)).assertDoesNotExist() // blank category hidden

        click(JeopardyTags.tile(0, 0)) // used
        click(JeopardyTags.tile(1, 2))

        assertEquals(listOf(HostCommands.selectClue(1, 2)), sent)
    }

    @Test
    fun `ending a round early is confirmed`() {
        show(board)
        click(JeopardyTags.END_ROUND)
        assertTrue(sent.isEmpty())
        confirmDialog()
        assertEquals(listOf(HostCommands.endRound()), sent)
    }

    @Test
    fun `round end goes to the next round or the final standings`() {
        show(board.copy(phase = Phase.ROUND_END))
        compose.onNodeWithText(s(R.string.round_end_next)).assertIsDisplayed()
        state = board.copy(phase = Phase.ROUND_END, round = board.round!!.copy(index = 1))
        compose.onNodeWithText(s(R.string.round_end_final)).assertIsDisplayed()
        click(JeopardyTags.PRIMARY)
        assertEquals(listOf(HostCommands.nextRound()), sent)
    }

    @Test
    fun `game over names the winner and starts over`() {
        show(board.copy(phase = Phase.GAME_OVER))
        compose.onNodeWithText(s(R.string.game_over_winner, "Amy", "$400")).assertIsDisplayed()
        click(JeopardyTags.PRIMARY)
        assertEquals(listOf(HostCommands.resetGame()), sent)
    }

    // --- Clue phases ---

    @Test
    fun `while reading, the response is hidden until peeked`() {
        show(reading)
        compose.onNodeWithTag(JeopardyTags.RESPONSE_HIDDEN).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.clue_tap_to_peek)).assertIsDisplayed()

        click(JeopardyTags.RESPONSE_HIDDEN)

        compose.onNodeWithTag(JeopardyTags.RESPONSE).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.clue_tap_to_hide)).assertIsDisplayed()
    }

    @Test
    fun `clue phase opens buzzers, picks a responder, reveals or cancels`() {
        show(reading)
        compose.onNodeWithText("This planet is red").assertIsDisplayed()
        compose.onNodeWithText("$600").assertIsDisplayed()
        click(JeopardyTags.PRIMARY)

        compose.onNodeWithTag(JeopardyTags.RESPONDER_ANSWERING).assertIsNotEnabled()
        click(JeopardyTags.RESPONDER_PICKER)
        compose.onNodeWithTag("${JeopardyTags.RESPONDER_PICKER}.p2").performClick()
        click(JeopardyTags.RESPONDER_ANSWERING)

        click(JeopardyTags.REVEAL)
        click(JeopardyTags.CANCEL_CLUE)

        assertEquals(
            listOf(HostCommands.arm(), HostCommands.selectResponder("p2"), HostCommands.reveal(), HostCommands.cancelClue()),
            sent,
        )
    }

    @Test
    fun `automatic and no-buzzer clues`() {
        show(reading.copy(clue = reading.clue!!.copy(mode = BuzzerMode.AUTOMATIC)))
        compose.onNodeWithText(s(R.string.clue_open_buzzers_now)).assertIsDisplayed()

        state = reading.copy(clue = reading.clue!!.copy(mode = BuzzerMode.NONE))
        compose.onNodeWithText(s(R.string.clue_no_buzzers)).assertIsDisplayed()
        compose.onNodeWithTag(JeopardyTags.PRIMARY).assertDoesNotExist()
    }

    @Test
    fun `once someone is locked out the clue can't be cancelled`() {
        show(reading.copy(clue = reading.clue!!.copy(lockedOut = listOf("p3"))))
        compose.onNodeWithText(s(R.string.clue_locked_out, "Cat")).assertIsDisplayed()
        compose.onNodeWithTag(JeopardyTags.CANCEL_CLUE).assertDoesNotExist()
        compose.onNodeWithTag(JeopardyTags.REVEAL).assertIsDisplayed()
    }

    @Test
    fun `armed waits for a buzz with a countdown`() {
        countdown = Countdown(active = true, remainingMs = 3_200, fraction = 0.64f, kind = "buzz")
        show(reading.copy(phase = Phase.ARMED))
        compose.onNodeWithText(s(R.string.clue_buzzers_open)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.timer_buzz, "3.2")).assertIsDisplayed()
        compose.onNodeWithTag(JeopardyTags.RESPONSE).assertIsDisplayed() // no longer hidden
    }

    @Test
    fun `answering is judged`() {
        show(answering)
        compose.onNodeWithText(s(R.string.clue_is_answering, "Bob")).assertIsDisplayed()
        compose.onNodeWithTag(JeopardyTags.CANCEL_CLUE).assertDoesNotExist()
        click(JeopardyTags.CORRECT)
        click(JeopardyTags.INCORRECT)
        assertEquals(listOf(HostCommands.judge(true), HostCommands.judge(false)), sent)
    }

    @Test
    fun `daily double wager stays within the bounds`() {
        show(Fixtures.state("dd-wager")) // Amy, $5 to $1,000, score $400
        compose.onNodeWithText(s(R.string.dd_wager_label, "$5", "$1,000")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.dd_max, "$1,000")).assertIsDisplayed()

        compose.onNodeWithTag(JeopardyTags.WAGER).performTextInput("2000")
        compose.onNodeWithTag(JeopardyTags.WAGER_SUBMIT).assertIsNotEnabled()
        compose.onNodeWithTag(JeopardyTags.WAGER).performTextClearance()
        compose.onNodeWithTag(JeopardyTags.WAGER).performTextInput("500")
        click(JeopardyTags.WAGER_SUBMIT)
        click(JeopardyTags.WAGER_MAX)

        click(JeopardyTags.WAGERER)
        compose.onNodeWithTag("${JeopardyTags.WAGERER}.p2").performClick()

        assertEquals(listOf(HostCommands.wager(500), HostCommands.trueDailyDouble(), HostCommands.setControl("p2")), sent)
    }

    @Test
    fun `a true daily double when the score is above the top value`() {
        val dd = Fixtures.state("dd-wager")
        show(dd.copy(clue = dd.clue!!.copy(maxWager = 400))) // max equals Amy's score
        compose.onNodeWithText(s(R.string.dd_true, "$400")).assertIsDisplayed()
    }

    @Test
    fun `daily double clue is judged against the wager`() {
        val dd = Fixtures.state("dd-wager")
        show(dd.copy(phase = Phase.DD_CLUE, clue = dd.clue!!.copy(wager = 500, wagerSet = true)))
        compose.onNodeWithText(s(R.string.clue_wagered, "Amy", "$500")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.clue_daily_double_wager, "$500")).assertIsDisplayed()
        click(JeopardyTags.CORRECT)
        assertEquals(listOf(HostCommands.judge(true)), sent)
    }

    @Test
    fun `reveal shows the result and returns to the board`() {
        show(answering.copy(phase = Phase.REVEAL, clue = answering.clue!!.copy(result = "correct")))
        compose.onNodeWithText(s(R.string.clue_result_correct, "Bob")).assertIsDisplayed()
        state = answering.copy(phase = Phase.REVEAL, clue = answering.clue!!.copy(result = "timeout", responder = null))
        compose.onNodeWithText(s(R.string.clue_result_timeout)).assertIsDisplayed()
        click(JeopardyTags.PRIMARY)
        assertEquals(listOf(HostCommands.continueToBoard()), sent)
    }

    @Test
    fun `image sides show their captions`() {
        show(answering)
        compose.onNodeWithText("What is").assertIsDisplayed()
        compose.onNodeWithText("Mars?").assertIsDisplayed()
    }

    // --- Players ---

    @Test
    fun `players list scores, control and buttons`() {
        show(board)
        compose.onNodeWithTag(JeopardyTags.playerScore("p1"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("-$200").assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.players_has_control)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.players_has_phone)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.players_last_button, 4, "Amy", java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(board.lastButton!!.atMs))))
            .assertIsDisplayed()
    }

    @Test
    fun `add a player without a phone`() {
        show(lobby)
        click(JeopardyTags.PLAYERS_ADD)
        compose.onNodeWithTag(PlayerDialogTags.ADD_CONFIRM).assertIsNotEnabled()
        compose.onNodeWithTag(PlayerDialogTags.ADD_NAME).performTextInput("Dee")
        compose.onNodeWithTag(PlayerDialogTags.ADD_CONFIRM).performClick()
        assertEquals(listOf(HostCommands.addPlayer("Dee")), sent)
    }

    @Test
    fun `player dialog adjusts score by the clue value and sets it`() {
        show(answering) // clue value $600
        click(JeopardyTags.player("p1"))
        compose.onNodeWithText("+$600").assertIsDisplayed()
        click(PlayerDialogTags.PLUS)
        click(PlayerDialogTags.MINUS)
        compose.onNodeWithTag(PlayerDialogTags.SET_SCORE).assertIsNotEnabled()
        compose.onNodeWithTag(PlayerDialogTags.SCORE).performTextClearance()
        compose.onNodeWithTag(PlayerDialogTags.SCORE).performTextInput("1200")
        click(PlayerDialogTags.SET_SCORE)

        assertEquals(
            listOf(HostCommands.adjustScore("p1", 600), HostCommands.adjustScore("p1", -600), HostCommands.setScore("p1", 1200)),
            sent,
        )
    }

    @Test
    fun `player dialog renames, gives control, assigns and moves`() {
        show(board)
        click(JeopardyTags.player("p2"))
        compose.onNodeWithTag(PlayerDialogTags.RENAME).assertIsNotEnabled()
        compose.onNodeWithTag(PlayerDialogTags.NAME).performTextClearance()
        compose.onNodeWithTag(PlayerDialogTags.NAME).performTextInput("Bobby")
        click(PlayerDialogTags.RENAME)
        click(PlayerDialogTags.CONTROL)
        click(PlayerDialogTags.ASSIGN)
        click(PlayerDialogTags.UP)
        click(PlayerDialogTags.DOWN)

        assertEquals(
            listOf(
                HostCommands.renamePlayer("p2", "Bobby"),
                HostCommands.setControl("p2"),
                HostCommands.assignButton("p2"),
                HostCommands.movePlayer("p2", 0),
                HostCommands.movePlayer("p2", 2),
            ),
            sent,
        )
    }

    @Test
    fun `player with control and a button can clear both`() {
        show(board)
        click(JeopardyTags.player("p1")) // has control, button 4, first in order
        compose.onNodeWithTag(PlayerDialogTags.UP).assertIsNotEnabled()
        click(PlayerDialogTags.CONTROL)
        click(PlayerDialogTags.CLEAR_BUTTON)
        assertEquals(listOf(HostCommands.setControl(null), HostCommands.clearButton("p1")), sent)
    }

    @Test
    fun `removing a player is confirmed`() {
        show(board)
        click(JeopardyTags.player("p3"))
        click(PlayerDialogTags.REMOVE)
        compose.onNodeWithText(s(R.string.player_remove_confirm, "Cat")).assertIsDisplayed()
        confirmDialog()
        assertEquals(listOf(HostCommands.removePlayer("p3")), sent)
    }

    @Test
    fun `holding drain sends a tick at once and more while held`() {
        show(board)
        click(JeopardyTags.player("p2"))
        compose.onNodeWithTag(PlayerDialogTags.DRAIN).performScrollTo()

        compose.onNodeWithTag(PlayerDialogTags.DRAIN).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HostCommands.DRAIN_INTERVAL_MS * 2 + 50)
        compose.onNodeWithTag(PlayerDialogTags.DRAIN).performTouchInput { up() }
        val ticks = sent.size
        compose.mainClock.advanceTimeBy(1_000)

        assertTrue("expected at least 3 ticks, got $ticks", ticks >= 3)
        assertEquals(ticks, sent.size) // stops on release
        assertTrue(sent.all { it == HostCommands.drainTick("p2") })
    }

    @Test
    fun `score log opens newest first`() {
        show(board)
        click(JeopardyTags.SCORE_LOG)
        compose.onNodeWithText("Amy correct").assertIsDisplayed()
        compose.onNodeWithText("  +$200").assertIsDisplayed()
    }

    // --- Settings ---

    @Test
    fun `settings save the whole object with the changes`() {
        show(board)
        click(JeopardyTags.SETTINGS)
        compose.onNodeWithTag(SettingsTags.toggle("dailyDoubles")).performClick()
        compose.onNodeWithTag(SettingsTags.toggle("showResponses")).performClick()
        compose.onNodeWithTag(SettingsTags.mode(BuzzerMode.AUTOMATIC)).performClick()
        compose.onNodeWithTag(SettingsTags.READ_PER_CHAR).performScrollTo().performTextClearance()
        compose.onNodeWithTag(SettingsTags.READ_PER_CHAR).performTextInput("80")
        compose.onNodeWithTag(SettingsTags.BUZZ_WINDOW).performScrollTo().performTextClearance()
        compose.onNodeWithTag(SettingsTags.BUZZ_WINDOW).performTextInput("7.5")
        compose.onNodeWithTag(JeopardyTags.SETTINGS_SAVE).performClick()

        val expected = board.settings.copy(
            dailyDoubles = false,
            hideResponses = true,
            buzzerMode = BuzzerMode.AUTOMATIC,
            readMsPerChar = 80,
            buzzWindowMs = 7_500,
        )
        assertEquals(listOf(HostCommands.updateSettings(expected)), sent)
    }

    @Test
    fun `settings hide fields that don't apply`() {
        show(board.copy(settings = board.settings.copy(earlyBuzzPenalty = false)))
        click(JeopardyTags.SETTINGS)
        compose.onNodeWithTag(SettingsTags.LOCKOUT_MS).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.READ_PER_CHAR).assertDoesNotExist() // manual mode
        compose.onNodeWithText(s(R.string.cancel)).assertDoesNotExist()
        compose.onNodeWithContentDescription(s(R.string.cancel)).performClick()
        assertTrue(sent.isEmpty())
    }

    // --- Drain points ---

    @Test
    fun `drain points sits beneath the scores, and only with players`() {
        show(lobby.copy(players = emptyList()))
        compose.onNodeWithTag(DrainTags.OPEN).assertDoesNotExist()

        state = board
        val lastScore = compose.onNodeWithTag(JeopardyTags.player("p3")).fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag(DrainTags.OPEN).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("drain button should be below the last player", button.top >= lastScore.bottom)
    }

    @Test
    fun `drain screen has an evenly sized button per player`() {
        show(board)
        click(DrainTags.OPEN)

        val heights = listOf("p1", "p2", "p3").map {
            compose.onNodeWithTag(DrainTags.player(it)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.height
        }
        assertTrue("heights differ: $heights", heights.max() - heights.min() < 2f)
        assertTrue("buttons should fill the screen: $heights", heights.first() > 150f)
    }

    @Test
    fun `holding a player's drain button drains only that player until released`() {
        show(board)
        click(DrainTags.OPEN)

        compose.onNodeWithTag(DrainTags.player("p2")).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HostCommands.DRAIN_INTERVAL_MS * 3 + 50)
        compose.onNodeWithTag(DrainTags.player("p2")).performTouchInput { up() }
        val ticks = sent.size
        compose.mainClock.advanceTimeBy(1_000)

        assertTrue("expected at least 4 ticks, got $ticks", ticks >= 4)
        assertEquals(ticks, sent.size)
        assertTrue(sent.all { it == HostCommands.drainTick("p2") })
    }

    @Test
    fun `drain screen shows live scores and closes`() {
        show(board)
        click(DrainTags.OPEN)
        compose.onNodeWithContentDescription("Bob -$200").assertIsDisplayed()

        state = board.copy(players = board.players.map { if (it.id == "p2") it.copy(score = -215) else it })
        compose.onNodeWithContentDescription("Bob -$215").assertIsDisplayed()

        click(DrainTags.CLOSE)
        compose.onNodeWithTag(DrainTags.player("p2")).assertDoesNotExist()
        assertTrue(sent.isEmpty())
    }
}
