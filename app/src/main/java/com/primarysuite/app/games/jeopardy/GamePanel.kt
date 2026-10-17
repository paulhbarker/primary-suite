package com.primarysuite.app.games.jeopardy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.ui.theme.AppColors

/** In-game host controls for every phase after the lobby (host/GamePanel.tsx). */
@Composable
fun GamePanel(
    state: HostState,
    countdown: Countdown,
    serverBase: String,
    send: (GameMessage) -> Unit,
    confirm: (message: String, action: () -> Unit) -> Unit,
) {
    val round = state.round
    val roundLabel = round?.let { roundName(it.index, it.count) } ?: ""

    when (state.phase) {
        Phase.ROUND_INTRO -> {
            val visible = round?.categories?.filter { it.visible }.orEmpty()
            val revealedCount = visible.count { it.revealed }
            val next = visible.getOrNull(revealedCount)
            val last = visible.getOrNull(revealedCount - 1)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Title(roundLabel)
                if (last != null) {
                    Column(
                        Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(10.dp))
                            .border(1.dp, AppColors.Outline, RoundedCornerShape(10.dp)).padding(14.dp),
                    ) {
                        SectionLabel(stringResource(R.string.intro_now_showing))
                        Text(last.title.uppercase(), color = AppColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        if (last.description.isNotBlank()) Text(last.description, color = AppColors.OnSurfaceMedium)
                    }
                }
                BigButton(
                    text = if (next != null) {
                        stringResource(R.string.intro_reveal, revealedCount + 1, visible.size, next.title.ifBlank { stringResource(R.string.intro_untitled) })
                    } else {
                        stringResource(R.string.intro_start_round)
                    },
                    onClick = { send(HostCommands.next()) },
                )
            }
        }

        Phase.BOARD -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(roundLabel, Modifier.weight(1f))
                Text(
                    stringResource(R.string.board_pick, state.control?.let { state.nameOf(it) } ?: stringResource(R.string.board_anyone)),
                    color = AppColors.OnSurface,
                    fontSize = 14.sp,
                )
            }
            if (round != null) BoardGrid(round) { col, row -> send(HostCommands.selectClue(col, row)) }
            Text(stringResource(R.string.board_hint), color = AppColors.OnSurfaceDim, fontSize = 12.sp)
            val endRoundConfirm = stringResource(R.string.board_end_round_confirm)
            TextButton(
                onClick = { confirm(endRoundConfirm) { send(HostCommands.endRound()) } },
                modifier = Modifier.fillMaxWidth().testTag(JeopardyTags.END_ROUND),
            ) { Text(stringResource(R.string.board_end_round), color = AppColors.Warning) }
        }

        Phase.CLUE, Phase.ARMED, Phase.ANSWERING, Phase.DD_WAGER, Phase.DD_CLUE, Phase.REVEAL ->
            if (state.clue != null) CluePanel(state, state.clue, countdown, serverBase, send)

        Phase.ROUND_END -> {
            val last = round == null || round.index >= round.count - 1
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Title(stringResource(R.string.round_end_title, roundLabel))
                BigButton(
                    stringResource(if (last) R.string.round_end_final else R.string.round_end_next),
                    onClick = { send(HostCommands.nextRound()) },
                )
            }
        }

        Phase.GAME_OVER -> {
            val winner = state.players.maxByOrNull { it.score }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Title(stringResource(R.string.game_over_title))
                if (winner != null) {
                    Text(stringResource(R.string.game_over_winner, winner.name, money(winner.score)), color = AppColors.OnSurface, fontSize = 16.sp)
                }
                BigButton(stringResource(R.string.game_over_new_game), onClick = { send(HostCommands.resetGame()) })
            }
        }

        Phase.LOBBY -> Unit
    }
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = AppColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * The round's board in TV colours (components/TVBoard.tsx, compact). Blank
 * categories are hidden as in play. Tapping an available tile selects it.
 */
@Composable
fun BoardGrid(round: Round, onSelect: (col: Int, row: Int) -> Unit) {
    val columns = round.categories.indices.filter { round.categories[it].visible }
    val gap = 2.dp
    Column(
        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(6.dp)).padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            columns.forEach { col ->
                Box(
                    Modifier.weight(1f).height(52.dp).background(JeopardyColors.Board).padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        round.categories[col].title.uppercase(),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        for (row in round.values.indices) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                columns.forEach { col ->
                    val tile = round.tiles.getOrNull(col)?.getOrNull(row) ?: Tile(TileState.EMPTY, false)
                    val available = tile.state == TileState.AVAILABLE
                    val value = round.values[row]
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(if (available) JeopardyColors.Board else JeopardyColors.BoardDark)
                            .testTag(JeopardyTags.tile(col, row))
                            .semantics { contentDescription = "${round.categories[col].title} ${money(value)}${if (tile.dailyDouble) " Daily Double" else ""}" }
                            .let { if (available) it.clickable { onSelect(col, row) } else it },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (available) {
                            Text(money(value), color = JeopardyColors.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        if (tile.dailyDouble && available) {
                            Text(
                                "DD",
                                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                                    .background(JeopardyColors.Gold, RoundedCornerShape(3.dp)).padding(horizontal = 2.dp),
                                color = JeopardyColors.Board,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
