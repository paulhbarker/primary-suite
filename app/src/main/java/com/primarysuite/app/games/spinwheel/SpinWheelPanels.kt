package com.primarysuite.app.games.spinwheel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.AppColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Hint(text: String) = Text(text, color = AppColors.OnSurfaceMedium, fontSize = 12.sp)

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)

/** A value in its wedge colour, like the web's ValueChip. */
@Composable
fun ValueChip(value: Int, modifier: Modifier = Modifier, minWidth: Dp = 56.dp, fontSize: Int = 15) {
    val c = ValueColors.of(value)
    Box(
        modifier
            .widthIn(min = minWidth)
            .background(Color(c.fill), RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$value", color = Color(c.text), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
    }
}

@Composable
private fun buttonName(id: Int): String? = when (BoxButton.of(id)) {
    BoxButton.GREEN -> stringResource(R.string.spinwheel_button_green)
    BoxButton.YELLOW -> stringResource(R.string.spinwheel_button_yellow)
    BoxButton.WHITE -> stringResource(R.string.spinwheel_button_white)
    BoxButton.BLUE -> stringResource(R.string.spinwheel_button_blue)
    null -> null
}

/** Who spun it, as the web says it: "green button", "admin", "admin, rigged". */
@Composable
private fun source(r: WheelResult): String {
    val who = if (r.button > 0) {
        stringResource(R.string.spinwheel_source_button, buttonName(r.button) ?: "${r.button}")
    } else {
        stringResource(R.string.spinwheel_source_admin)
    }
    return if (r.rigged) stringResource(R.string.spinwheel_source_rigged, who) else who
}

@Composable
private fun phaseLabel(state: WheelState): String = stringResource(
    when {
        state.locked && state.phase == WheelPhase.ARMED -> R.string.spinwheel_phase_locked
        state.phase == WheelPhase.ARMED -> R.string.spinwheel_phase_armed
        state.phase == WheelPhase.SPINNING -> R.string.spinwheel_phase_spinning
        else -> R.string.spinwheel_phase_landed
    },
)

/** The Wheel tab: status and Spin, force the next result, and the history. */
@Composable
fun WheelPanel(state: WheelState, clockOffsetMs: Long, enabled: Boolean, actions: SpinWheelActions, onConfirmClear: () -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ControlsCard(state, enabled, actions)
        ForceCard(state, enabled, actions.onForceNext)
        HistoryCard(state, clockOffsetMs, enabled, onConfirmClear)
    }
}

@Composable
private fun ControlsCard(state: WheelState, enabled: Boolean, actions: SpinWheelActions) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Title(phaseLabel(state), Modifier.weight(1f, fill = false).testTag(SpinWheelTags.PHASE))
            if (state.phase == WheelPhase.LANDED) state.spinValue?.let { ValueChip(it) }
            if (state.locked) {
                Text(
                    stringResource(R.string.spinwheel_locked_badge),
                    modifier = Modifier.background(AppColors.Error.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = AppColors.Error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Button(
            onClick = actions.onSpin,
            enabled = enabled && state.phase != WheelPhase.SPINNING,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(SpinWheelTags.SPIN),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
        ) { Text(stringResource(R.string.spinwheel_spin), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.spinwheel_lock), fontSize = 16.sp)
                Hint(stringResource(R.string.spinwheel_lock_hint))
            }
            Switch(
                checked = state.locked,
                onCheckedChange = actions.onSetLocked,
                enabled = enabled,
                modifier = Modifier.testTag(SpinWheelTags.LOCK),
                colors = SwitchDefaults.colors(checkedTrackColor = AppColors.PrimaryGreen, checkedThumbColor = AppColors.OnPrimary),
            )
        }
        state.lastResult?.let { last ->
            Row(
                Modifier.semantics(mergeDescendants = true) {}.testTag(SpinWheelTags.LAST_RESULT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.spinwheel_last_result), color = AppColors.OnSurfaceMedium, fontSize = 14.sp)
                ValueChip(last.value)
                Text(source(last), color = AppColors.OnSurfaceMedium, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ForceCard(state: WheelState, enabled: Boolean, onForceNext: (Int?) -> Unit) {
    val forced = state.forcedNext
    Card {
        Row(Modifier.semantics(mergeDescendants = true) {}.testTag(SpinWheelTags.FORCE_TITLE), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (forced != null) {
                Title(stringResource(R.string.spinwheel_force_set))
                ValueChip(forced)
                Text(stringResource(R.string.spinwheel_rigged), color = AppColors.OnSurfaceMedium)
            } else {
                Title(stringResource(R.string.spinwheel_force_title))
            }
        }
        Hint(stringResource(R.string.spinwheel_force_hint))
        state.values.sorted().chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { v ->
                    val on = forced == v
                    val c = ValueColors.of(v)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .alpha(if (on || forced == null) 1f else 0.6f)
                            .background(Color(c.fill), RoundedCornerShape(8.dp))
                            .border(
                                if (on) 3.dp else 1.dp,
                                if (on) AppColors.OnSurface else Color.Black.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable(enabled = enabled, role = Role.Button) { onForceNext(if (on) null else v) }
                            .semantics { selected = on }
                            .testTag(SpinWheelTags.force(v)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$v", color = Color(c.text), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        if (forced != null) {
            OutlinedButton(
                onClick = { onForceNext(null) },
                enabled = enabled,
                modifier = Modifier.testTag(SpinWheelTags.FORCE_CLEAR),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.spinwheel_clear)) }
        }
    }
}

@Composable
private fun HistoryCard(state: WheelState, clockOffsetMs: Long, enabled: Boolean, onConfirmClear: () -> Unit) {
    val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault())
    Card {
        Row(Modifier.semantics(mergeDescendants = true) {}.testTag(SpinWheelTags.HISTORY_TITLE), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Title(stringResource(R.string.spinwheel_history_title))
            Text(pluralStringResource(R.plurals.spinwheel_history_count, state.history.size, state.history.size), color = AppColors.OnSurfaceMedium)
        }
        if (state.history.isEmpty()) {
            Text(stringResource(R.string.spinwheel_history_empty), color = AppColors.OnSurfaceMedium, fontSize = 14.sp)
            return@Card
        }
        Column {
            state.history.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = AppColors.Outline)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}.testTag(SpinWheelTags.historyRow(r.id)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ValueChip(r.value)
                    BoxButton.of(r.button)?.let { b ->
                        Spacer(
                            Modifier.size(12.dp).background(Color(b.rgb), CircleShape).border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
                        )
                    }
                    Text(source(r), color = AppColors.OnSurfaceMedium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (r.at > 0) {
                        Text(
                            timeFormat.format(Date(r.at + clockOffsetMs)),
                            color = AppColors.OnSurfaceMedium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        TextButton(onClick = onConfirmClear, enabled = enabled, modifier = Modifier.testTag(SpinWheelTags.CLEAR_HISTORY)) {
            Text(stringResource(R.string.spinwheel_clear_history), color = AppColors.OnSurfaceMedium)
        }
    }
}

/** The Odds tab: each value's weight with a share bar, and Reset to even. */
@Composable
fun OddsPanel(state: WheelState, enabled: Boolean, actions: SpinWheelActions) {
    val total = state.totalWeight
    val even = state.weights.distinct().size <= 1
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Title(stringResource(R.string.spinwheel_odds_title))
            Hint(stringResource(R.string.spinwheel_odds_hint))
            Column {
                state.odds.forEachIndexed { i, (v, w) ->
                    if (i > 0) HorizontalDivider(color = AppColors.Outline)
                    val share = if (total > 0) w.toFloat() / total else 0f
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ValueChip(v)
                        Box(Modifier.weight(1f).height(8.dp).background(AppColors.SurfaceVariant, CircleShape)) {
                            if (share > 0f) {
                                Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(Color(ValueColors.of(v).fill), CircleShape))
                            }
                        }
                        Text(
                            String.format(Locale.US, "%.1f%%", share * 100),
                            color = AppColors.OnSurfaceMedium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.width(52.dp),
                        )
                        OutlinedIconButton(
                            onClick = { actions.onSetWeight(v, w - 1) },
                            enabled = enabled && w > 0 && total != w,
                            modifier = Modifier.size(36.dp).testTag(SpinWheelTags.less(v)),
                        ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.cd_spinwheel_less, v)) }
                        Text(
                            "$w",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            modifier = Modifier.width(22.dp).testTag(SpinWheelTags.weight(v)),
                        )
                        OutlinedIconButton(
                            onClick = { actions.onSetWeight(v, w + 1) },
                            enabled = enabled && w < SpinWheelProtocol.WEIGHT.last,
                            modifier = Modifier.size(36.dp).testTag(SpinWheelTags.more(v)),
                        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cd_spinwheel_more, v)) }
                    }
                }
            }
            OutlinedButton(
                onClick = actions.onEvenOdds,
                enabled = enabled && !even,
                modifier = Modifier.testTag(SpinWheelTags.EVEN),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.spinwheel_odds_even)) }
        }
    }
}

/** The range shown in a field's hint and in its out-of-range message. */
private fun rangeOf(field: WheelField): Pair<String, String> {
    val r = if (field == WheelField.CELEBRATE) SpinWheelProtocol.CELEBRATE_MS else SpinWheelProtocol.SPIN_MS
    return WheelSettingsForm.formatSeconds(r.first) to WheelSettingsForm.formatSeconds(r.last)
}

/** The Settings tab: the web's settings card, with Save and Discard. */
@Composable
fun WheelSettingsPanel(
    form: WheelSettingsForm,
    dirty: Boolean,
    enabled: Boolean,
    onChange: (WheelSettingsForm) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val problems = form.problems()

    @Composable
    fun seconds(field: WheelField, label: Int, value: String, tag: String, hint: Int?, modifier: Modifier, update: (String) -> Unit) {
        val problem = problems[field]
        val (lo, hi) = rangeOf(field)
        OutlinedTextField(
            value = value,
            onValueChange = { if (WheelSettingsForm.isNumberDraft(it)) update(it) },
            label = { Text(stringResource(label)) },
            isError = problem != null,
            supportingText = {
                Text(
                    when (problem) {
                        WheelFieldProblem.NOT_A_NUMBER -> stringResource(R.string.spinwheel_problem_not_a_number)
                        WheelFieldProblem.OUT_OF_RANGE -> stringResource(R.string.spinwheel_problem_out_of_range, lo, hi)
                        WheelFieldProblem.MAX_BELOW_MIN -> stringResource(R.string.spinwheel_problem_max_below_min)
                        null -> {
                            val range = stringResource(R.string.spinwheel_range_hint, lo, hi)
                            if (hint != null) stringResource(hint) + " ($range)" else range
                        }
                    },
                )
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = modifier.testTag(tag),
        )
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                seconds(WheelField.SPIN_MIN, R.string.spinwheel_set_spin_min, form.spinMin, SpinWheelTags.SPIN_MIN, null, Modifier.weight(1f)) {
                    onChange(form.copy(spinMin = it))
                }
                seconds(WheelField.SPIN_MAX, R.string.spinwheel_set_spin_max, form.spinMax, SpinWheelTags.SPIN_MAX, null, Modifier.weight(1f)) {
                    onChange(form.copy(spinMax = it))
                }
            }
            Hint(stringResource(R.string.spinwheel_set_spin_hint))
            seconds(
                WheelField.CELEBRATE, R.string.spinwheel_set_celebrate, form.celebrate, SpinWheelTags.CELEBRATE,
                R.string.spinwheel_set_celebrate_hint, Modifier.fillMaxWidth(),
            ) { onChange(form.copy(celebrate = it)) }

            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.spinwheel_set_volume), modifier = Modifier.weight(1f), fontSize = 16.sp)
                    Text(WheelSettingsForm.volumeLabel(form.volume), color = AppColors.OnSurfaceMedium, fontSize = 15.sp)
                }
                Slider(
                    value = form.volume.toFloat(),
                    onValueChange = { onChange(form.copy(volume = WheelSettings.roundVolume(it))) },
                    valueRange = 0f..1f,
                    steps = 19,
                    enabled = enabled,
                    modifier = Modifier.testTag(SpinWheelTags.VOLUME),
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.PrimaryGreen,
                        activeTrackColor = AppColors.PrimaryGreen,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledActiveTickColor = Color.Transparent,
                        disabledInactiveTickColor = Color.Transparent,
                    ),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = enabled && dirty && form.isValid,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(SpinWheelTags.SAVE),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
                ) { Text(stringResource(R.string.spinwheel_set_save), fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = dirty,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(SpinWheelTags.DISCARD),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.spinwheel_set_discard)) }
            }
            Hint(stringResource(R.string.spinwheel_set_note))
        }
    }
}
