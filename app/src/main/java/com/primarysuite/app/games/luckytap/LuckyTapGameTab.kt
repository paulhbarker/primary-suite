package com.primarysuite.app.games.luckytap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.ui.components.SectionHeader
import com.primarysuite.app.ui.components.StatusCard
import com.primarysuite.app.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * The live game: who's checked in, their odds, this round's scans, the last
 * draw, and the admin actions. Pull down (or tap refresh) to resync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameTab(state: LuckyTapScreenState, actions: LuckyTapActions, onConfirm: (Confirm) -> Unit) {
    val game = state.game
    // Admin actions are off while a draw animates: they'd reset the projected display.
    val canAdmin = state.serverConnected && !game.drawing

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(game) { refreshing = false } // the answer arrived
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(3_000)
            refreshing = false
        }
    }
    val refresh = {
        if (canAdmin) {
            refreshing = true
            actions.onRefresh()
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.serverConnected || !game.synced) {
                Text(
                    stringResource(R.string.game_waiting_for_state),
                    color = AppColors.OnSurfaceMedium,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            StatusCard {
                SummaryRow(stringResource(R.string.game_checked_in), game.checkedIn.size.toString())
                SummaryRow(stringResource(R.string.game_round_scans), game.scansThisRound.toString())
                WinnersStepper(game.numWinners, enabled = canAdmin, onChange = actions.onSetNumWinners)
            }

            DrawStatus(game)

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.game_cards_header), Modifier.weight(1f))
                IconButton(onClick = refresh, enabled = canAdmin, modifier = Modifier.testTag(LuckyTapTags.REFRESH)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.game_refresh))
                }
            }
            CardGrid(game)

            SectionHeader(stringResource(R.string.section_admin), Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(R.string.button_check_in_all, canAdmin, LuckyTapTags.CHECK_IN_ALL, Modifier.weight(1f)) {
                    onConfirm(Confirm.CHECK_IN_ALL)
                }
                SecondaryButton(R.string.button_scan_all, canAdmin, LuckyTapTags.SCAN_ALL, Modifier.weight(1f)) {
                    onConfirm(Confirm.SCAN_ALL)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DestructiveButton(R.string.button_clear_scans, canAdmin, LuckyTapTags.CLEAR, Modifier.weight(1f)) {
                    onConfirm(Confirm.CLEAR)
                }
                DestructiveButton(R.string.button_reset_game, canAdmin, LuckyTapTags.RESET, Modifier.weight(1f)) {
                    onConfirm(Confirm.RESET)
                }
            }
            Text(
                stringResource(R.string.game_refresh_note),
                color = AppColors.OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = AppColors.OnSurface)
        Text(value, style = MaterialTheme.typography.titleMedium, color = AppColors.OnSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WinnersStepper(count: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.game_num_winners), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = AppColors.OnSurface)
        IconButton(
            onClick = { onChange(count - 1) },
            enabled = enabled && count > 1,
            modifier = Modifier.testTag(LuckyTapTags.FEWER_WINNERS),
        ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.cd_fewer_winners)) }
        Text(
            count.toString(),
            modifier = Modifier.width(32.dp).testTag(LuckyTapTags.NUM_WINNERS),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppColors.OnSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = { onChange(count + 1) },
            enabled = enabled && count < LuckyTapViewModel.MAX_WINNERS,
            modifier = Modifier.testTag(LuckyTapTags.MORE_WINNERS),
        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cd_more_winners)) }
    }
}

@Composable
private fun DrawStatus(game: LuckyTapGameState) {
    val (text, color) = when {
        game.drawing -> stringResource(R.string.game_drawing) to AppColors.Warning
        else -> when (val last = game.lastDraw) {
            is DrawResult.Winners -> stringResource(R.string.game_last_winners, last.numbers.joinToString(", ")) to AppColors.PrimaryGreen
            is DrawResult.Failed -> stringResource(R.string.game_last_draw_failed, last.message) to AppColors.Error
            null -> return
        }
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().testTag(LuckyTapTags.DRAW_STATUS),
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** One tile per checked-in card: number, odds, and this round's scan count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardGrid(game: LuckyTapGameState) {
    if (game.checkedIn.isEmpty()) {
        Text(stringResource(R.string.game_no_cards), color = AppColors.OnSurfaceMedium, style = MaterialTheme.typography.bodyMedium)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        game.checkedIn.forEach { card ->
            val scans = game.roundScans[card] ?: 0
            val odds = game.oddsPercent(card)
            val description = stringResource(R.string.cd_card_odds, card, odds, scans)
            Box(
                Modifier
                    .size(width = 64.dp, height = 60.dp)
                    .background(AppColors.SurfaceVariant, RoundedCornerShape(10.dp))
                    .border(
                        width = if (scans > 0) 2.dp else 1.dp,
                        color = if (scans > 0) AppColors.PrimaryGreen else AppColors.Outline,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .testTag(LuckyTapTags.card(card))
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.toString(), color = AppColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "$odds%",
                        color = if (odds < 100) AppColors.Warning else AppColors.OnSurfaceMedium,
                        fontSize = 11.sp,
                    )
                }
                if (scans > 0) {
                    Text(
                        "×$scans",
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp),
                        color = AppColors.PrimaryGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    Spacer(Modifier.size(0.dp))
}
