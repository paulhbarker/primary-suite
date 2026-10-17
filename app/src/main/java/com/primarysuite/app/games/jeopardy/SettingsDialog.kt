package com.primarysuite.app.games.jeopardy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.AppColors
import java.util.Locale

object SettingsTags {
    fun toggle(key: String) = "jeopardy.settings.$key"
    fun mode(mode: BuzzerMode) = "jeopardy.settings.mode.${mode.wire}"
    const val LOCKOUT_MS = "jeopardy.settings.lockoutMs"
    const val BUZZ_WINDOW = "jeopardy.settings.buzzWindow"
    const val ANSWER_TIME = "jeopardy.settings.answerTime"
    const val READ_PER_CHAR = "jeopardy.settings.readPerChar"
    const val READ_MIN = "jeopardy.settings.readMin"
}

/** Game settings (host/SettingsDialog.tsx). Edits a copy; Save sends the whole object. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(settings: Settings, onSave: (Settings) -> Unit, onClose: () -> Unit) {
    var s by remember(settings) { mutableStateOf(settings) }
    // Number fields keep their own text (state-based fields; see PlayerDialog) and are parsed on save.
    val lockoutMs = rememberTextFieldState(settings.earlyBuzzPenaltyMs.toString())
    val buzzWindow = rememberTextFieldState(formatSeconds(settings.buzzWindowMs))
    val answerTime = rememberTextFieldState(formatSeconds(settings.answerWindowMs))
    val readPerChar = rememberTextFieldState(settings.readMsPerChar.toString())
    val readMin = rememberTextFieldState(formatSeconds(settings.readMinMs))
    fun withNumbers(base: Settings) = base.copy(
        earlyBuzzPenaltyMs = lockoutMs.text.toString().toIntOrNull()?.coerceIn(0, 5000) ?: 0,
        buzzWindowMs = seconds(buzzWindow, 120_000),
        answerWindowMs = seconds(answerTime, 120_000),
        readMsPerChar = readPerChar.text.toString().toIntOrNull()?.coerceIn(0, 1000) ?: 0,
        readMinMs = seconds(readMin, 60_000),
    )
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = AppColors.Background,
            contentColor = AppColors.OnSurface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.jeopardy_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel)) }
                    },
                    actions = {
                        TextButton(onClick = { onSave(withNumbers(s)) }, modifier = Modifier.testTag(JeopardyTags.SETTINGS_SAVE)) {
                            Text(stringResource(R.string.settings_save), color = AppColors.PrimaryGreen, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel(stringResource(R.string.settings_rules))
                Toggle("dailyDoubles", stringResource(R.string.settings_daily_doubles), stringResource(R.string.settings_daily_doubles_help), s.dailyDoubles) { s = s.copy(dailyDoubles = it) }
                Toggle("deductIncorrect", stringResource(R.string.settings_deduct), stringResource(R.string.settings_deduct_help), s.deductIncorrect) { s = s.copy(deductIncorrect = it) }
                Toggle("lowestScoreStartsRound", stringResource(R.string.settings_lowest_first), null, s.lowestScoreStartsRound) { s = s.copy(lowestScoreStartsRound = it) }
                Toggle("revealCategories", stringResource(R.string.settings_reveal_categories), null, s.revealCategories) { s = s.copy(revealCategories = it) }
                // Stored as hideResponses; the switch reads the way the host thinks about it.
                Toggle("showResponses", stringResource(R.string.settings_show_responses), stringResource(R.string.settings_show_responses_help), !s.hideResponses) { s = s.copy(hideResponses = !it) }

                HorizontalDivider(color = AppColors.Outline)
                SectionLabel(stringResource(R.string.settings_buzzers))
                Text(stringResource(R.string.settings_buzzer_mode), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                listOf(
                    BuzzerMode.MANUAL to R.string.settings_mode_manual,
                    BuzzerMode.AUTOMATIC to R.string.settings_mode_automatic,
                    BuzzerMode.NONE to R.string.settings_mode_none,
                ).forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = s.buzzerMode == mode, role = Role.RadioButton) { s = s.copy(buzzerMode = mode) }
                            .testTag(SettingsTags.mode(mode)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.buzzerMode == mode, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                    }
                }
                Toggle("earlyBuzzPenalty", stringResource(R.string.settings_early_lockout), stringResource(R.string.settings_early_lockout_help), s.earlyBuzzPenalty) { s = s.copy(earlyBuzzPenalty = it) }
                if (s.earlyBuzzPenalty) {
                    NumberField(stringResource(R.string.settings_lockout_ms), lockoutMs, SettingsTags.LOCKOUT_MS)
                }
                Toggle("rearmAfterIncorrect", stringResource(R.string.settings_rearm), null, s.rearmAfterIncorrect) { s = s.copy(rearmAfterIncorrect = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(stringResource(R.string.settings_buzz_window), buzzWindow, SettingsTags.BUZZ_WINDOW, Modifier.weight(1f), decimal = true)
                    NumberField(stringResource(R.string.settings_answer_time), answerTime, SettingsTags.ANSWER_TIME, Modifier.weight(1f), decimal = true)
                }
                Text(stringResource(R.string.settings_no_limit), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
                if (s.buzzerMode == BuzzerMode.AUTOMATIC) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(stringResource(R.string.settings_read_per_char), readPerChar, SettingsTags.READ_PER_CHAR, Modifier.weight(1f))
                        NumberField(stringResource(R.string.settings_read_min), readMin, SettingsTags.READ_MIN, Modifier.weight(1f), decimal = true)
                    }
                }

                HorizontalDivider(color = AppColors.Outline)
                SectionLabel(stringResource(R.string.settings_audio))
                Text(stringResource(R.string.settings_volume), fontSize = 14.sp)
                Slider(
                    value = s.soundVolume.toFloat(),
                    onValueChange = { s = s.copy(soundVolume = (Math.round(it * 20) / 20.0)) },
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.testTag(SettingsTags.toggle("soundVolume")),
                )
            }
        }
    }
}

@Composable
private fun Toggle(key: String, label: String, help: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.testTag(SettingsTags.toggle(key)),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (help != null) Text(help, color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A number field; [decimal] allows one decimal point (seconds). */
@Composable
private fun NumberField(label: String, state: TextFieldState, tag: String, modifier: Modifier = Modifier, decimal: Boolean = false) {
    val pattern = if (decimal) Regex("\\d{0,4}(\\.\\d{0,2})?") else Regex("\\d{0,6}")
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        inputTransformation = InputTransformation { if (!pattern.matches(asCharSequence())) revertAllChanges() },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/** Seconds typed by the host, as milliseconds within 0..[maxMs]. */
private fun seconds(state: TextFieldState, maxMs: Int): Int =
    ((state.text.toString().toDoubleOrNull() ?: 0.0) * 1000).toInt().coerceIn(0, maxMs)

private fun formatSeconds(ms: Int): String {
    val s = ms / 1000.0
    return if (s == Math.floor(s)) s.toInt().toString() else String.format(Locale.US, "%.1f", s)
}
