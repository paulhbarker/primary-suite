package com.primarysuite.app.games.jeopardy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.primarysuite.app.R
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.ui.theme.AppColors
import java.text.DateFormat
import java.util.Date

object PlayerDialogTags {
    const val NAME = "jeopardy.playerDialog.name"
    const val RENAME = "jeopardy.playerDialog.rename"
    const val MINUS = "jeopardy.playerDialog.minus"
    const val PLUS = "jeopardy.playerDialog.plus"
    const val DRAIN = "jeopardy.playerDialog.drain"
    const val SCORE = "jeopardy.playerDialog.score"
    const val SET_SCORE = "jeopardy.playerDialog.setScore"
    const val CONTROL = "jeopardy.playerDialog.control"
    const val ASSIGN = "jeopardy.playerDialog.assign"
    const val CLEAR_BUTTON = "jeopardy.playerDialog.clearButton"
    const val UP = "jeopardy.playerDialog.up"
    const val DOWN = "jeopardy.playerDialog.down"
    const val REMOVE = "jeopardy.playerDialog.remove"
    const val DONE = "jeopardy.playerDialog.done"
    const val ADD_NAME = "jeopardy.addPlayer.name"
    const val ADD_CONFIRM = "jeopardy.addPlayer.confirm"
}

/** Scoreboard with per-player management: tap a player (host/PlayersPanel.tsx). */
@Composable
fun PlayersPanel(state: HostState, send: (GameMessage) -> Unit, confirm: (String, () -> Unit) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var draining by rememberSaveable { mutableStateOf(false) }
    val clue = state.clue

    Column(
        Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.players_header, state.players.size), Modifier.weight(1f))
            TextButton(onClick = { adding = true }, modifier = Modifier.testTag(JeopardyTags.PLAYERS_ADD)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.players_add))
            }
        }
        if (state.players.isEmpty()) {
            Text(stringResource(R.string.players_none), color = AppColors.OnSurfaceMedium, fontSize = 14.sp)
        }
        state.players.forEach { p ->
            val answering = clue?.responder == p.id || (clue?.wagerer == p.id && state.phase.wire.startsWith("dd"))
            val locked = clue?.lockedOut?.contains(p.id) == true
            PlayerRow(p, hasControl = state.control == p.id, answering = answering, locked = locked) { selected = p.id }
        }
        if (state.players.isNotEmpty()) {
            Box(Modifier.padding(top = 4.dp)) { DrainPointsButton { draining = true } }
        }
        state.lastButton?.let { b ->
            val who = b.playerId?.let { id -> state.players.firstOrNull { it.id == id }?.name ?: "?" } ?: stringResource(R.string.players_not_assigned)
            Text(
                stringResource(R.string.players_last_button, b.buttonId, who, DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(b.atMs))),
                color = AppColors.OnSurfaceDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    state.players.firstOrNull { it.id == selected }?.let { player ->
        PlayerDialog(player, state, send, confirm, onClose = { selected = null })
    }
    if (draining) DrainScreen(state.players, send, onClose = { draining = false })
    if (adding) AddPlayerDialog(onAdd = { send(HostCommands.addPlayer(it)); adding = false }, onDismiss = { adding = false })
}

@Composable
private fun PlayerRow(p: Player, hasControl: Boolean, answering: Boolean, locked: Boolean, onClick: () -> Unit) {
    val swatch = parseColor(p.color)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (!p.connected) 0.5f else if (locked) 0.6f else 1f)
            .background(if (answering) JeopardyColors.Gold.copy(alpha = 0.15f) else AppColors.SurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, if (answering) JeopardyColors.Gold else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(JeopardyTags.player(p.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasControl) {
            Icon(Icons.Outlined.Star, contentDescription = stringResource(R.string.players_has_control), tint = JeopardyColors.Gold, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(p.name, color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (locked) Text(stringResource(R.string.players_locked_out), color = AppColors.OnSurfaceDim, fontSize = 12.sp)
        }
        if (p.hasPhone) {
            Icon(Icons.Outlined.Smartphone, contentDescription = stringResource(R.string.players_has_phone), tint = AppColors.OnSurfaceMedium, modifier = Modifier.size(16.dp))
        }
        if (p.buttonId != null) {
            Row(
                Modifier.background(AppColors.Outline, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (swatch != null) ButtonSwatch(swatch) else Icon(Icons.Outlined.RadioButtonChecked, null, Modifier.size(12.dp), tint = AppColors.OnSurface)
                Text(p.buttonId.toString(), color = AppColors.OnSurface, fontSize = 12.sp)
            }
        }
        Text(
            money(p.score),
            modifier = Modifier
                .widthIn(min = 72.dp)
                .background(if (p.draining) JeopardyColors.Negative.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp)
                .testTag(JeopardyTags.playerScore(p.id)),
            color = if (p.score < 0) JeopardyColors.Negative else AppColors.OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AddPlayerDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val nameState = rememberTextFieldState()
    val name = nameState.text.toString()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.players_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    state = nameState,
                    label = { Text(stringResource(R.string.players_name)) },
                    inputTransformation = InputTransformation.maxLength(24),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().testTag(PlayerDialogTags.ADD_NAME),
                )
                Text(stringResource(R.string.players_add_hint), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.testTag(PlayerDialogTags.ADD_CONFIRM)) {
                Text(stringResource(R.string.players_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = AppColors.SurfaceVariant,
    )
}

/** Rename, score, control, button, order and removal for one player. */
@Composable
private fun PlayerDialog(
    player: Player,
    state: HostState,
    send: (GameMessage) -> Unit,
    confirm: (String, () -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val index = state.players.indexOf(player)
    // State-based text fields: the value-based ones never settle inside a dialog window under Robolectric.
    val nameState = rememberTextFieldState(player.name)
    val name = nameState.text.toString()
    val scoreState = rememberTextFieldState(player.score.toString())
    val score = scoreState.text.toString()
    // Keep "Set score" in step when the score changes underneath (e.g. while draining).
    LaunchedEffect(player.score) { scoreState.setTextAndPlaceCursorAtEnd(player.score.toString()) }
    val step = state.clue?.value?.takeIf { it > 0 } ?: state.round?.values?.firstOrNull() ?: 200
    val scoreNum = score.toIntOrNull()
    val hasControl = state.control == player.id
    val removeConfirm = stringResource(R.string.player_remove_confirm, player.name)

    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = AppColors.SurfaceVariant) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(player.name, color = AppColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        state = nameState,
                        label = { Text(stringResource(R.string.players_name)) },
                        inputTransformation = InputTransformation.maxLength(24),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.weight(1f).testTag(PlayerDialogTags.NAME),
                    )
                    OutlinedButton(
                        onClick = { send(HostCommands.renamePlayer(player.id, name.trim())) },
                        enabled = name.isNotBlank() && name != player.name,
                        modifier = Modifier.testTag(PlayerDialogTags.RENAME),
                    ) { Text(stringResource(R.string.player_rename)) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { send(HostCommands.adjustScore(player.id, -step)) },
                        modifier = Modifier.weight(1f).testTag(PlayerDialogTags.MINUS),
                    ) { Text("−" + money(step)) }
                    OutlinedButton(
                        onClick = { send(HostCommands.adjustScore(player.id, step)) },
                        modifier = Modifier.weight(1f).testTag(PlayerDialogTags.PLUS),
                    ) { Text("+" + money(step)) }
                }
                DrainButton { send(HostCommands.drainTick(player.id)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        state = scoreState,
                        label = { Text(stringResource(R.string.player_set_score)) },
                        inputTransformation = ScoreInput,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag(PlayerDialogTags.SCORE),
                    )
                    OutlinedButton(
                        onClick = { scoreNum?.let { send(HostCommands.setScore(player.id, it)) } },
                        enabled = scoreNum != null && scoreNum != player.score,
                        modifier = Modifier.testTag(PlayerDialogTags.SET_SCORE),
                    ) { Text(stringResource(R.string.player_set)) }
                }

                HorizontalDivider(color = AppColors.Outline)
                Button(
                    onClick = { send(HostCommands.setControl(if (hasControl) null else player.id)) },
                    modifier = Modifier.fillMaxWidth().testTag(PlayerDialogTags.CONTROL),
                    colors = if (hasControl) {
                        ButtonDefaults.buttonColors(containerColor = JeopardyColors.Gold, contentColor = JeopardyColors.Board)
                    } else {
                        ButtonDefaults.outlinedButtonColors(contentColor = AppColors.OnSurface)
                    },
                ) {
                    Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" " + stringResource(if (hasControl) R.string.player_clear_control else R.string.player_give_control))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.player_button, player.buttonId?.let { "#$it" } ?: stringResource(R.string.player_button_none)),
                            color = AppColors.OnSurface,
                            fontSize = 14.sp,
                        )
                        parseColor(player.color)?.let { ButtonSwatch(it) }
                    }
                    if (state.assigningButtonFor == player.id) {
                        TextButton(onClick = { send(HostCommands.assignButton(null)) }, modifier = Modifier.testTag(PlayerDialogTags.ASSIGN)) {
                            Text(stringResource(R.string.player_assign_cancel), color = AppColors.Warning)
                        }
                    } else {
                        OutlinedButton(onClick = { send(HostCommands.assignButton(player.id)) }, modifier = Modifier.testTag(PlayerDialogTags.ASSIGN)) {
                            Text(stringResource(R.string.player_assign))
                        }
                    }
                    if (player.buttonId != null) {
                        OutlinedButton(onClick = { send(HostCommands.clearButton(player.id)) }, modifier = Modifier.testTag(PlayerDialogTags.CLEAR_BUTTON)) {
                            Text(stringResource(R.string.player_clear_button))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.player_position, index + 1, state.players.size), color = AppColors.OnSurface, fontSize = 14.sp)
                        if (player.color != null) Text(stringResource(R.string.player_box_order), color = AppColors.OnSurfaceDim, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { send(HostCommands.movePlayer(player.id, index - 1)) },
                        enabled = index > 0,
                        modifier = Modifier.testTag(PlayerDialogTags.UP),
                    ) { Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.player_move_up)) }
                    IconButton(
                        onClick = { send(HostCommands.movePlayer(player.id, index + 1)) },
                        enabled = index < state.players.size - 1,
                        modifier = Modifier.testTag(PlayerDialogTags.DOWN),
                    ) { Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.player_move_down)) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { confirm(removeConfirm) { send(HostCommands.removePlayer(player.id)); onClose() } },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error, contentColor = Color.White),
                        modifier = Modifier.testTag(PlayerDialogTags.REMOVE),
                    ) { Text(stringResource(R.string.player_remove)) }
                    TextButton(onClick = onClose, modifier = Modifier.testTag(PlayerDialogTags.DONE)) { Text(stringResource(R.string.player_done)) }
                }
            }
        }
    }
}

/**
 * Press and hold to drain points as a penalty: one tick at once, then one
 * every [HostCommands.DRAIN_INTERVAL_MS] until released.
 */
@Composable
private fun DrainButton(onTick: () -> Unit) {
    var draining by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (draining) AppColors.Error else Color.Transparent, RoundedCornerShape(24.dp))
            .border(1.dp, AppColors.Error, RoundedCornerShape(24.dp))
            .holdToRepeat(onHoldChange = { draining = it }, onTick = onTick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(PlayerDialogTags.DRAIN),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = if (draining) Color.White else AppColors.Error, modifier = Modifier.size(18.dp))
        Text(
            " " + stringResource(if (draining) R.string.player_draining else R.string.player_drain),
            color = if (draining) Color.White else AppColors.Error,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** An optional leading minus, then up to six digits. */
private val ScoreInput = InputTransformation {
    if (!Regex("-?\\d{0,6}").matches(asCharSequence())) revertAllChanges()
}

/** Collapsible score log, newest first. */
@Composable
fun ScoreLog(log: List<LogEntry>) {
    if (log.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val time = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    Column(
        Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp).testTag(JeopardyTags.SCORE_LOG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.score_log), Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = AppColors.OnSurfaceMedium)
        }
        if (open) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                log.asReversed().forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(time.format(Date(entry.atMs)) + "  ", color = AppColors.OnSurfaceDim, fontSize = 11.sp)
                        Text(entry.text, color = AppColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                        if (entry.delta != 0) {
                            Text(
                                "  " + (if (entry.delta > 0) "+" else "") + money(entry.delta),
                                color = if (entry.delta > 0) AppColors.Success else JeopardyColors.Negative,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
