package com.primarysuite.app.games.luckytap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import com.primarysuite.app.core.ui.PressButton
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

            StatTiles(game, canAdmin, actions.onSetNumWinners)

            DrawStatus(game)

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.game_cards_header), Modifier.weight(1f))
                IconButton(onClick = refresh, enabled = canAdmin, modifier = Modifier.testTag(LuckyTapTags.REFRESH)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.game_refresh))
                }
            }
            CardGrid(game, canAdmin, actions.onSimulateScan)

            PressButton(
                text = stringResource(R.string.game_press_button),
                onPress = actions.onPressButton,
                enabled = canAdmin,
                minHeight = 72.dp,
                fontSize = 20.sp,
                modifier = Modifier.testTag(LuckyTapTags.PRESS_BUTTON),
            )

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

/** Three stat tiles in a row: checked in, scans this round, and a winners stepper. */
@Composable
private fun StatTiles(game: LuckyTapGameState, enabled: Boolean, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val tile = Modifier
        .background(AppColors.SurfaceVariant, shape)
        .border(1.dp, AppColors.Outline, shape)
        .padding(vertical = 10.dp, horizontal = 8.dp)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.game_checked_in), game.checkedIn.size.toString(), AppColors.PrimaryGreen, tile.weight(1f).fillMaxHeight())
        StatTile(stringResource(R.string.game_scans_short), game.scansThisRound.toString(), AppColors.OnSurface, tile.weight(1f).fillMaxHeight())
        Column(tile.weight(1.3f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onChange(game.numWinners - 1) },
                    enabled = enabled && game.numWinners > 1,
                    modifier = Modifier.size(32.dp).testTag(LuckyTapTags.FEWER_WINNERS),
                ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.cd_fewer_winners)) }
                Text(
                    game.numWinners.toString(),
                    modifier = Modifier.widthIn(min = 28.dp).testTag(LuckyTapTags.NUM_WINNERS),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Warning,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(
                    onClick = { onChange(game.numWinners + 1) },
                    enabled = enabled && game.numWinners < LuckyTapViewModel.MAX_WINNERS,
                    modifier = Modifier.size(32.dp).testTag(LuckyTapTags.MORE_WINNERS),
                ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cd_more_winners)) }
            }
            Text(stringResource(R.string.game_winners_short), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, color = AppColors.OnSurfaceMedium, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

/**
 * One playing card (5:7) per checked-in card, filling the width. Tap one to
 * simulate scanning it.
 */
@Composable
private fun CardGrid(game: LuckyTapGameState, enabled: Boolean, onScan: (Int) -> Unit) {
    if (game.checkedIn.isEmpty()) {
        Text(stringResource(R.string.game_no_cards), color = AppColors.OnSurfaceMedium, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val gap = 8.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + gap) / (72.dp + gap)).toInt().coerceAtLeast(1)
        val cardWidth = (maxWidth - gap * (columns - 1)) / columns
        val shape = RoundedCornerShape(8.dp)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            game.checkedIn.chunked(columns).forEach { rowCards ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowCards.forEach { card ->
                        val scans = game.roundScans[card] ?: 0
                        val odds = game.oddsPercent(card)
                        val description = stringResource(R.string.cd_card_odds, card, odds, scans)
                        Box(
                            Modifier
                                .width(cardWidth)
                                .aspectRatio(5f / 7f)
                                .background(AppColors.SurfaceVariant, shape)
                                .border(
                                    width = if (scans > 0) 2.dp else 1.dp,
                                    color = if (scans > 0) AppColors.PrimaryGreen else AppColors.Outline,
                                    shape = shape,
                                )
                                .clip(shape)
                                .clickable(enabled = enabled) { onScan(card) }
                                .testTag(LuckyTapTags.card(card))
                                .clearAndSetSemantics {
                                    contentDescription = description
                                    role = Role.Button
                                    if (enabled) onClick { onScan(card); true }
                                },
                        ) {
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(card.toString(), color = AppColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text(
                                    "$odds%",
                                    color = if (odds < 100) AppColors.Warning else AppColors.OnSurfaceMedium,
                                    fontSize = 11.sp,
                                )
                            }
                            if (scans > 0) {
                                Text(
                                    "×$scans",
                                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 3.dp),
                                    color = AppColors.PrimaryGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
