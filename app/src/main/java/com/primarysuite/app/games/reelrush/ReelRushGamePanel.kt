package com.primarysuite.app.games.reelrush

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.AppColors
import java.util.Locale

private fun secs(ms: Int?): String = if (ms == null) "-" else String.format(Locale.US, "%.3fs", ms / 1000.0)

@Composable
private fun phaseLabel(phase: Phase) = stringResource(
    when (phase) {
        Phase.IDLE -> R.string.reelrush_phase_idle
        Phase.LOBBY -> R.string.reelrush_phase_lobby
        Phase.INTRO -> R.string.reelrush_phase_intro
        Phase.SPINNING -> R.string.reelrush_phase_spinning
        Phase.WINDOW -> R.string.reelrush_phase_window
        Phase.RESULTS -> R.string.reelrush_phase_results
        Phase.FINAL -> R.string.reelrush_phase_final
    },
)

/** The Game tab: the phase card and its buttons, the players, and the last round or final standings. */
@Composable
fun GamePanel(
    state: ReelRushState,
    enabled: Boolean,
    actions: ReelRushActions,
    onConfirmEnd: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ControlsCard(state, enabled, actions, onConfirmEnd, onConfirmReset)
        PlayersCard(state, enabled, actions.onRemovePlayer)
        LastRoundCard(state)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Swatch(player: ReelRushPlayer?) {
    Spacer(
        Modifier
            .size(14.dp)
            .background(Color(player?.color?.rgb ?: PlayerColor.WHITE.rgb), CircleShape)
            .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
    )
}

@Composable
private fun ControlsCard(
    state: ReelRushState,
    enabled: Boolean,
    actions: ReelRushActions,
    onConfirmEnd: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    val phase = state.phase
    val joined = state.joinedCount
    Card {
        Column {
            Text(phaseLabel(phase), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag(ReelRushTags.PHASE))
            if (state.showRound) {
                Text(
                    if (state.suddenDeath) {
                        stringResource(R.string.reelrush_sudden_death)
                    } else {
                        stringResource(R.string.reelrush_round_of, state.round, state.rounds)
                    },
                    color = AppColors.OnSurfaceMedium,
                    fontSize = 14.sp,
                )
            }
        }
        when (phase) {
            Phase.IDLE, Phase.FINAL -> PrimaryButton(
                if (phase == Phase.FINAL) R.string.reelrush_back_to_lobby else R.string.reelrush_open_lobby,
                enabled,
                actions.onOpenLobby,
            )
            Phase.LOBBY -> PrimaryButton(
                stringResource(
                    if (joined < ReelRushProtocol.MIN_PLAYERS) R.string.reelrush_start_needs else R.string.reelrush_start_with,
                    *(if (joined < ReelRushProtocol.MIN_PLAYERS) arrayOf<Any>(ReelRushProtocol.MIN_PLAYERS, joined) else arrayOf<Any>(joined)),
                ),
                enabled && joined >= ReelRushProtocol.MIN_PLAYERS,
                actions.onStart,
            )
            Phase.WINDOW -> PrimaryButton(R.string.reelrush_close_round, enabled, actions.onCloseRound)
            Phase.RESULTS -> PrimaryButton(
                when (state.nextAction) {
                    NextAction.NEXT_ROUND -> R.string.reelrush_next_round
                    NextAction.REPLAY_ROUND -> R.string.reelrush_replay_the_round
                    NextAction.SHOW_WINNER -> R.string.reelrush_show_winner
                    NextAction.ANOTHER_SUDDEN_DEATH -> R.string.reelrush_another_sudden_death
                    NextAction.FINISH_GAME -> R.string.reelrush_finish_game
                },
                enabled,
                actions.onNextRound,
            )
            else -> Unit
        }
        if (phase.inRound || phase == Phase.RESULTS) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = actions.onReplayRound,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReelRushTags.REPLAY),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.reelrush_replay_round)) }
                OutlinedButton(
                    onClick = onConfirmEnd,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReelRushTags.END_GAME),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.reelrush_end_game)) }
            }
        }
        if (phase != Phase.IDLE) {
            TextButton(
                onClick = onConfirmReset,
                enabled = enabled,
                modifier = Modifier.testTag(ReelRushTags.RESET),
            ) { Text(stringResource(R.string.reelrush_reset), color = AppColors.OnSurfaceMedium) }
        }
    }
}

@Composable
private fun PrimaryButton(label: Int, enabled: Boolean, onClick: () -> Unit) = PrimaryButton(stringResource(label), enabled, onClick)

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag(ReelRushTags.PRIMARY),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.PrimaryGreen,
            contentColor = AppColors.OnPrimary,
            disabledContainerColor = AppColors.SurfaceVariant,
            disabledContentColor = AppColors.OnSurfaceDim,
        ),
    ) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun PlayersCard(state: ReelRushState, enabled: Boolean, onRemove: (Int) -> Unit) {
    val lobby = state.phase == Phase.LOBBY
    Card {
        Text(stringResource(R.string.reelrush_players), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        state.players.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = AppColors.Outline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.weight(1f).alpha(if (p.joined) 1f else 0.45f).semantics(mergeDescendants = true) {}.testTag(ReelRushTags.player(p.slot)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Swatch(p)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!p.joined) Text(stringResource(R.string.reelrush_not_joined), color = AppColors.OnSurfaceMedium, fontSize = 13.sp)
                        else {
                            val status = statusText(p)
                            if (status.isNotEmpty()) Text(status, color = AppColors.OnSurfaceMedium, fontSize = 13.sp)
                        }
                    }
                    if (p.joined) {
                        if (p.fouls > 0) Text(stringResource(R.string.reelrush_fouls, p.fouls), color = AppColors.Error, fontSize = 14.sp)
                        Text(
                            p.score.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            modifier = Modifier.width(40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
                if (lobby && p.joined) {
                    OutlinedButton(
                        onClick = { onRemove(p.slot) },
                        enabled = enabled,
                        modifier = Modifier.testTag(ReelRushTags.remove(p.slot)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) { Text(stringResource(R.string.reelrush_remove), fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun statusText(p: ReelRushPlayer): String = when (p.status) {
    PlayerStatus.OUT -> ""
    PlayerStatus.WAITING -> stringResource(R.string.reelrush_status_waiting)
    PlayerStatus.PRESSED -> stringResource(R.string.reelrush_status_pressed) + " " +
        stringResource(R.string.reelrush_pressed_detail, p.rank, secs(p.reactionMs))
    PlayerStatus.FOUL -> stringResource(R.string.reelrush_status_foul)
    PlayerStatus.CLOSED -> stringResource(R.string.reelrush_status_closed)
}

@Composable
private fun LastRoundCard(state: ReelRushState) {
    val final = state.final
    if (state.phase == Phase.FINAL && final != null) {
        FinalCard(state, final)
        return
    }
    val result = state.lastRound ?: return
    Card(Modifier.testTag(ReelRushTags.LAST_ROUND)) {
        Column {
            Text(
                if (result.suddenDeath) stringResource(R.string.reelrush_sudden_death) else stringResource(R.string.reelrush_round_title, result.round),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (result.voided) Text(stringResource(R.string.reelrush_voided), color = AppColors.Error, fontSize = 14.sp)
        }
        result.entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = AppColors.Outline)
            val p = state.player(e.slot)
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(ReelRushTags.result(e.slot)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(e.rank?.toString() ?: "", modifier = Modifier.width(20.dp))
                Swatch(p)
                Text(p?.name ?: "", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (e.foul) stringResource(R.string.reelrush_foul) else if (e.closedOut) "-" else secs(e.reactionMs),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
                Text(
                    if (result.suddenDeath) "" else stringResource(R.string.reelrush_points, e.points),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier.width(36.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun FinalCard(state: ReelRushState, final: FinalView) {
    val winners = final.winners.joinToString(" & ") { state.player(it)?.name ?: "" }
    Card(Modifier.semantics(mergeDescendants = true) {}.testTag(ReelRushTags.FINAL)) {
        Text(
            stringResource(if (final.tiebreak) R.string.reelrush_wins_sudden_death else R.string.reelrush_wins, winners),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        final.standings.forEachIndexed { i, st ->
            if (i > 0) HorizontalDivider(color = AppColors.Outline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(st.rank.toString(), modifier = Modifier.width(20.dp))
                Swatch(state.player(st.slot))
                Text(state.player(st.slot)?.name ?: "", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.reelrush_score_pts, st.score), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.reelrush_best, secs(st.bestMs)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = AppColors.OnSurfaceMedium,
                    )
                }
            }
        }
        final.titles.forEach { t ->
            Text(
                stringResource(R.string.reelrush_title_line, t.label, t.slots.joinToString(" & ") { state.player(it)?.name ?: "" }, t.value),
                fontSize = 14.sp,
            )
        }
    }
}
