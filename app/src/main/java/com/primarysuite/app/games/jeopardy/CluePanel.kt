package com.primarysuite.app.games.jeopardy

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.Picker
import com.primarysuite.app.core.ui.PickerOption
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.ui.theme.AppColors

/** The clue phases: clue, armed, answering, dd-wager, dd-clue, reveal. */
@Composable
fun CluePanel(state: HostState, clue: ActiveClue, countdown: Countdown, serverBase: String, send: (GameMessage) -> Unit) {
    val phase = state.phase
    // A peek only applies to the clue it was taken on.
    var peek by rememberSaveable(clue.col, clue.row, state.round?.index) { mutableStateOf(false) }
    val reading = phase == Phase.CLUE || phase == Phase.DD_WAGER
    val eligible = state.players.filter { it.id !in clue.lockedOut }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                clue.category.uppercase(),
                modifier = Modifier.weight(1f),
                color = AppColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    !clue.dailyDouble -> money(clue.value)
                    clue.wagerSet -> stringResource(R.string.clue_daily_double_wager, money(clue.wager))
                    else -> stringResource(R.string.clue_daily_double)
                },
                color = JeopardyColors.Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        ContentBox(label = stringResource(R.string.clue_label), labelColor = AppColors.OnSurfaceMedium, edge = AppColors.Outline) {
            SideContent(clue.clue, serverBase)
        }

        // While the host reads the clue aloud, the response sits right below it and is easy to
        // read out by mistake. Hide it until the buzzers open, with a tap to peek.
        val hidden = reading && !peek
        ContentBox(
            label = stringResource(R.string.clue_response),
            labelColor = AppColors.Success,
            edge = AppColors.Success,
            hint = if (reading) stringResource(if (peek) R.string.clue_tap_to_hide else R.string.clue_tap_to_peek) else null,
            modifier = Modifier
                .clickable { peek = !peek }
                .testTag(if (hidden) JeopardyTags.RESPONSE_HIDDEN else JeopardyTags.RESPONSE),
        ) {
            Box(if (!hidden) Modifier else if (canBlur) Modifier.blur(14.dp) else Modifier.alpha(0f)) {
                SideContent(clue.response, serverBase)
            }
        }

        when (phase) {
            Phase.CLUE -> {
                if (clue.lockedOut.isNotEmpty()) {
                    StatusBanner(stringResource(R.string.clue_locked_out, clue.lockedOut.joinToString { state.nameOf(it) }))
                }
                if (clue.mode != BuzzerMode.NONE) {
                    BigButton(
                        stringResource(if (clue.mode == BuzzerMode.AUTOMATIC) R.string.clue_open_buzzers_now else R.string.clue_open_buzzers),
                        onClick = { send(HostCommands.arm()) },
                        enabled = eligible.isNotEmpty(),
                        success = true,
                    )
                } else {
                    Text(stringResource(R.string.clue_no_buzzers), color = AppColors.OnSurfaceMedium, fontSize = 14.sp)
                }
                TimerBar(countdown)
                ResponderPicker(eligible, stringResource(R.string.clue_pick_who), send)
                Secondary(canCancel = clue.lockedOut.isEmpty(), send)
            }

            Phase.ARMED -> {
                StatusBanner(stringResource(R.string.clue_buzzers_open), Tone.SUCCESS)
                TimerBar(countdown)
                ResponderPicker(eligible, stringResource(R.string.clue_answered_without_buzzing), send)
                Secondary(canCancel = clue.lockedOut.isEmpty(), send)
            }

            Phase.ANSWERING -> {
                StatusBanner(
                    stringResource(R.string.clue_is_answering, state.nameOf(clue.responder)),
                    Tone.WARNING,
                    player = state.player(clue.responder),
                    large = true,
                )
                TimerBar(countdown)
                JudgeButtons(send)
                Secondary(canCancel = false, send)
            }

            Phase.DD_WAGER -> WagerForm(state, clue, send)

            Phase.DD_CLUE -> {
                StatusBanner(stringResource(R.string.clue_wagered, state.nameOf(clue.wagerer), money(clue.wager)), Tone.WARNING, large = true)
                JudgeButtons(send)
                Secondary(canCancel = false, send)
            }

            Phase.REVEAL -> {
                val who = clue.responder ?: clue.wagerer
                val result = when (clue.result) {
                    "correct" -> stringResource(R.string.clue_result_correct, state.nameOf(who))
                    "timeout" -> stringResource(R.string.clue_result_timeout)
                    "incorrect" -> stringResource(R.string.clue_result_incorrect)
                    else -> stringResource(R.string.clue_result_ended)
                }
                StatusBanner(result, if (clue.result == "correct") Tone.SUCCESS else Tone.INFO)
                BigButton(stringResource(R.string.clue_back_to_board), onClick = { send(HostCommands.continueToBoard()) })
            }

            else -> Unit
        }
    }
}

@Composable
private fun ContentBox(
    label: String,
    labelColor: Color,
    edge: Color,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .border(2.dp, edge, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            SectionLabel(label, Modifier.weight(1f), color = labelColor)
            if (hint != null) Text(hint, color = AppColors.OnSurfaceDim, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
private fun ResponderPicker(eligible: List<Player>, placeholder: String, send: (GameMessage) -> Unit) {
    if (eligible.isEmpty()) return
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Picker(
            label = "",
            placeholder = placeholder,
            options = eligible.map { PickerOption(it.id, it.name) },
            selected = pick,
            onSelect = { pick = it },
            tag = JeopardyTags.RESPONDER_PICKER,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = {
                pick?.let { send(HostCommands.selectResponder(it)) }
                pick = null
            },
            enabled = pick != null,
            modifier = Modifier.testTag(JeopardyTags.RESPONDER_ANSWERING),
        ) { Text(stringResource(R.string.clue_answering_button)) }
    }
}

@Composable
private fun JudgeButtons(send: (GameMessage) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { send(HostCommands.judge(true)) },
            modifier = Modifier.weight(1f).heightIn(min = 68.dp).testTag(JeopardyTags.CORRECT),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success, contentColor = Color.White),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null)
            Text(" " + stringResource(R.string.clue_correct), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = { send(HostCommands.judge(false)) },
            modifier = Modifier.weight(1f).heightIn(min = 68.dp).testTag(JeopardyTags.INCORRECT),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error, contentColor = Color.White),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null)
            Text(" " + stringResource(R.string.clue_incorrect), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Secondary(canCancel: Boolean, send: (GameMessage) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { send(HostCommands.reveal()) }, modifier = Modifier.testTag(JeopardyTags.REVEAL)) {
            Text(stringResource(R.string.clue_end_and_reveal))
        }
        if (canCancel) {
            TextButton(onClick = { send(HostCommands.cancelClue()) }, modifier = Modifier.testTag(JeopardyTags.CANCEL_CLUE)) {
                Text(stringResource(R.string.clue_back_unplayed), color = AppColors.Warning)
            }
        }
    }
}

/** Daily Double: choose who plays it, take the wager within the server's bounds. */
@Composable
private fun WagerForm(state: HostState, clue: ActiveClue, send: (GameMessage) -> Unit) {
    var amount by rememberSaveable(clue.col, clue.row) { mutableStateOf("") }
    val n = amount.toIntOrNull()
    val min = clue.minWager ?: 5
    val max = clue.maxWager ?: 0
    val valid = clue.wagerer != null && n != null && n in min..max
    val submit = { if (valid && n != null) send(HostCommands.wager(n)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusBanner(
            stringResource(R.string.dd_ask, clue.wagerer?.let { state.nameOf(it) } ?: stringResource(R.string.dd_choose_player)),
            Tone.WARNING,
        )
        Picker(
            label = "",
            placeholder = stringResource(R.string.dd_who_plays),
            options = state.players.map { PickerOption(it.id, stringResource(R.string.dd_player_option, it.name, money(it.score))) },
            selected = clue.wagerer,
            onSelect = { send(HostCommands.setControl(it)) },
            tag = JeopardyTags.WAGERER,
        )
        if (clue.wagerer != null) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.dd_wager_label, money(min), money(max))) },
                isError = amount.isNotEmpty() && !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().testTag(JeopardyTags.WAGER),
            )
            val wagererScore = state.player(clue.wagerer)?.score ?: 0
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = submit,
                    enabled = valid,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(JeopardyTags.WAGER_SUBMIT),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
                ) { Text(stringResource(R.string.dd_show_clue), fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = { send(HostCommands.trueDailyDouble()) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag(JeopardyTags.WAGER_MAX),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        stringResource(if (max > wagererScore) R.string.dd_max else R.string.dd_true, money(max)),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        TextButton(onClick = { send(HostCommands.cancelClue()) }, modifier = Modifier.testTag(JeopardyTags.CANCEL_CLUE)) {
            Text(stringResource(R.string.clue_back_unplayed), color = AppColors.Warning)
        }
    }
}
