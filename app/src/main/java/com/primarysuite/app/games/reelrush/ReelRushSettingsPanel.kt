package com.primarysuite.app.games.reelrush

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.AppColors

/** The range shown in a field's hint and in its out-of-range message. */
private fun rangeOf(field: SettingsField): Pair<String, String> = when (field) {
    SettingsField.REELS -> "${ReelRushProtocol.REELS.first}" to "${ReelRushProtocol.REELS.last}"
    SettingsField.ROUNDS -> "${ReelRushProtocol.ROUNDS.first}" to "${ReelRushProtocol.ROUNDS.last}"
    SettingsField.NEAR_MISSES -> "${ReelRushProtocol.NEAR_MISSES.first}" to "${ReelRushProtocol.NEAR_MISSES.last}"
    SettingsField.DISPLAY_LAG -> "${ReelRushProtocol.DISPLAY_LAG_MS.first}" to "${ReelRushProtocol.DISPLAY_LAG_MS.last}"
    SettingsField.SPIN_SPEED -> "15" to "40"
    SettingsField.ROUND_MIN, SettingsField.ROUND_MAX ->
        SettingsForm.formatSeconds(ReelRushProtocol.ROUND_MS.first) to SettingsForm.formatSeconds(ReelRushProtocol.ROUND_MS.last)
    SettingsField.DWELL_MIN, SettingsField.DWELL_MAX ->
        SettingsForm.formatSeconds(ReelRushProtocol.DWELL_MS.first) to SettingsForm.formatSeconds(ReelRushProtocol.DWELL_MS.last)
}

/** The Settings tab: the web's settings card, with Save and Discard. */
@Composable
fun SettingsPanel(
    form: SettingsForm,
    dirty: Boolean,
    enabled: Boolean,
    onChange: (SettingsForm) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val problems = form.problems()

    @Composable
    fun number(
        field: SettingsField,
        label: Int,
        value: String,
        tag: String,
        decimal: Boolean,
        hint: Int? = null,
        modifier: Modifier = Modifier,
        update: (String) -> Unit,
    ) {
        val problem = problems[field]
        val (lo, hi) = rangeOf(field)
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                val ok = if (decimal) SettingsForm.isNumberDraft(text) else text.all(Char::isDigit)
                if (ok) update(text)
            },
            label = { Text(stringResource(label)) },
            isError = problem != null,
            supportingText = {
                Text(
                    when (problem) {
                        FieldProblem.NOT_A_NUMBER -> stringResource(R.string.reelrush_problem_not_a_number)
                        FieldProblem.OUT_OF_RANGE -> stringResource(R.string.reelrush_problem_out_of_range, lo, hi)
                        FieldProblem.MAX_BELOW_MIN -> stringResource(R.string.reelrush_problem_max_below_min)
                        null -> if (hint != null) stringResource(hint) + " (" + stringResource(R.string.reelrush_range_hint, lo, hi) + ")"
                        else stringResource(R.string.reelrush_range_hint, lo, hi)
                    },
                )
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = modifier.testTag(tag),
        )
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                number(SettingsField.REELS, R.string.reelrush_set_reels, form.reels, ReelRushTags.REELS, false, modifier = Modifier.weight(1f)) {
                    onChange(form.copy(reels = it))
                }
                number(SettingsField.ROUNDS, R.string.reelrush_set_rounds, form.rounds, ReelRushTags.ROUNDS, false, modifier = Modifier.weight(1f)) {
                    onChange(form.copy(rounds = it))
                }
            }
            Toggle(R.string.reelrush_set_hard_mode, R.string.reelrush_set_hard_mode_hint, form.hardMode, enabled, ReelRushTags.HARD_MODE) {
                onChange(form.copy(hardMode = it))
            }
            Toggle(R.string.reelrush_set_assist, R.string.reelrush_set_assist_hint, form.assist, enabled, ReelRushTags.ASSIST) {
                onChange(form.copy(assist = it))
            }
            Toggle(R.string.reelrush_set_row_decoys, R.string.reelrush_set_row_decoys_hint, form.rowDecoys, enabled, ReelRushTags.ROW_DECOYS) {
                onChange(form.copy(rowDecoys = it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                number(SettingsField.ROUND_MIN, R.string.reelrush_set_round_min, form.roundMin, ReelRushTags.ROUND_MIN, true, modifier = Modifier.weight(1f)) {
                    onChange(form.copy(roundMin = it))
                }
                number(SettingsField.ROUND_MAX, R.string.reelrush_set_round_max, form.roundMax, ReelRushTags.ROUND_MAX, true, modifier = Modifier.weight(1f)) {
                    onChange(form.copy(roundMax = it))
                }
            }
            number(
                SettingsField.NEAR_MISSES, R.string.reelrush_set_near_misses, form.nearMisses, ReelRushTags.NEAR_MISSES, false,
                modifier = Modifier.fillMaxWidth(),
            ) { onChange(form.copy(nearMisses = it)) }
            Text(stringResource(R.string.reelrush_set_near_misses_hint), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
            number(
                SettingsField.SPIN_SPEED, R.string.reelrush_set_spin_speed, form.spinSpeed, ReelRushTags.SPIN_SPEED, true,
                hint = R.string.reelrush_set_spin_speed_hint, modifier = Modifier.fillMaxWidth(),
            ) { onChange(form.copy(spinSpeed = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                number(SettingsField.DWELL_MIN, R.string.reelrush_set_dwell_min, form.dwellMin, ReelRushTags.DWELL_MIN, true, modifier = Modifier.weight(1f)) {
                    onChange(form.copy(dwellMin = it))
                }
                number(SettingsField.DWELL_MAX, R.string.reelrush_set_dwell_max, form.dwellMax, ReelRushTags.DWELL_MAX, true, modifier = Modifier.weight(1f)) {
                    onChange(form.copy(dwellMax = it))
                }
            }
            number(
                SettingsField.DISPLAY_LAG, R.string.reelrush_set_display_lag, form.displayLag, ReelRushTags.DISPLAY_LAG, false,
                modifier = Modifier.fillMaxWidth(),
            ) { onChange(form.copy(displayLag = it)) }
            Text(stringResource(R.string.reelrush_set_display_lag_hint), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)

            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reelrush_set_volume), modifier = Modifier.weight(1f), fontSize = 16.sp)
                    Text(SettingsForm.volumeLabel(form.volume), color = AppColors.OnSurfaceMedium, fontSize = 15.sp)
                }
                Slider(
                    value = form.volume.toFloat(),
                    onValueChange = { onChange(form.copy(volume = ReelRushSettings.roundVolume(it))) },
                    valueRange = 0f..1f,
                    steps = 19,
                    enabled = enabled,
                    modifier = Modifier.testTag(ReelRushTags.VOLUME),
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reelrush_set_names), fontSize = 16.sp)
                form.names.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { col, name ->
                            val index = row * 2 + col
                            OutlinedTextField(
                                value = name,
                                onValueChange = { text ->
                                    onChange(form.copy(names = form.names.mapIndexed { i, n -> if (i == index) text.take(ReelRushProtocol.MAX_NAME_LENGTH) else n }))
                                },
                                label = { Text(stringResource(R.string.reelrush_set_player_n, index + 1)) },
                                enabled = enabled,
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag(ReelRushTags.name(index)),
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = enabled && dirty && form.isValid,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ReelRushTags.SAVE),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
                ) { Text(stringResource(R.string.reelrush_set_save), fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = dirty,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ReelRushTags.DISCARD),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.reelrush_set_discard)) }
            }
            Text(stringResource(R.string.reelrush_set_note), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Toggle(label: Int, hint: Int, checked: Boolean, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), fontSize = 16.sp)
            Text(stringResource(hint), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.PrimaryGreen, checkedThumbColor = AppColors.OnPrimary),
        )
    }
}
