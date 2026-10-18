package com.primarysuite.app.games.blockstacker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.PressButton
import com.primarysuite.app.core.ui.OfflineBadge
import com.primarysuite.app.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object BlockStackerTags {
    const val MAX_LEVELS = "blockStacker.maxLevels"
    const val MAX_LEVELS_VALUE = "blockStacker.maxLevels.value"
    const val INITIAL_SPEED = "blockStacker.initialSpeed"
    const val INITIAL_SPEED_VALUE = "blockStacker.initialSpeed.value"
    const val SPEED_INCREMENT = "blockStacker.speedIncrement"
    const val SPEED_INCREMENT_VALUE = "blockStacker.speedIncrement.value"
    const val GAME_ENABLED = "blockStacker.gameEnabled"
    const val SAVE = "blockStacker.save"
    const val UNSAVED = "blockStacker.unsaved"
    const val RESET = "blockStacker.reset"
    const val TEST_PRESS = "blockStacker.testPress"
    const val STATUS = "blockStacker.status"
    const val STATUS_VALUE = "blockStacker.status.value"
    const val TOTAL_GAMES = "blockStacker.totalGames"
    const val HIGH_SCORE = "blockStacker.highScore"
    const val LAST_UPDATED = "blockStacker.lastUpdated"
    const val LOG = "blockStacker.log"
    const val LOG_EMPTY = "blockStacker.log.empty"
    const val CONNECTING = "blockStacker.connecting"
}

/** Wires the screen to its view model. */
@Composable
fun BlockStackerRoute(vm: BlockStackerViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }

    BlockStackerScreen(
        ui = ui,
        snackbar = snackbar,
        onEdit = vm::edit,
        onSave = vm::save,
        onResetDefaults = vm::resetToDefaults,
        onTestPress = vm::testPress,
        onBack = onBack,
    )
}

/** The web admin (BlockStackerAdmin.tsx). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockStackerScreen(
    ui: BlockStackerUiState,
    snackbar: SnackbarHostState,
    onEdit: ((BlockStackerSettings) -> BlockStackerSettings) -> Unit,
    onSave: () -> Unit,
    onResetDefaults: () -> Unit,
    onTestPress: () -> Unit,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        containerColor = AppColors.Background,
        contentColor = AppColors.OnSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.blockstacker_title))
                        if (!ui.connected && ui.server != null) OfflineBadge(stringResource(R.string.game_offline))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        if (ui.server == null) {
            // Nothing to edit until the server has sent its settings.
            Box(Modifier.fillMaxSize().padding(padding).testTag(BlockStackerTags.CONNECTING), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.blockstacker_connecting), color = AppColors.OnSurfaceMedium)
                }
            }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsSection(ui, onEdit, onSave, onResetDefaults)
                // Fires on touch, like the physical button.
                PressButton(
                    text = stringResource(R.string.blockstacker_test_press),
                    onPress = onTestPress,
                    enabled = ui.connected,
                    minHeight = 72.dp,
                    fontSize = 20.sp,
                    modifier = Modifier.testTag(BlockStackerTags.TEST_PRESS),
                )

                HorizontalDivider(color = AppColors.Outline)
                StatusSection(ui.state, zone)
                HorizontalDivider(color = AppColors.Outline)
                HowToPlay()
                HorizontalDivider(color = AppColors.Outline)
                LogSection(ui.log, zone)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingsSection(
    ui: BlockStackerUiState,
    onEdit: ((BlockStackerSettings) -> BlockStackerSettings) -> Unit,
    onSave: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    val draft = ui.draft
    SectionTitle(stringResource(R.string.blockstacker_settings_title))

    SettingSlider(
        label = stringResource(R.string.blockstacker_max_levels),
        help = stringResource(R.string.blockstacker_max_levels_help),
        shown = draft.maxLevels.toString(),
        value = draft.maxLevels.toFloat(),
        range = BlockStackerProtocol.LEVELS_MIN.toFloat()..BlockStackerProtocol.LEVELS_MAX.toFloat(),
        steps = BlockStackerProtocol.LEVELS_MAX - BlockStackerProtocol.LEVELS_MIN - 1,
        tag = BlockStackerTags.MAX_LEVELS,
        valueTag = BlockStackerTags.MAX_LEVELS_VALUE,
        onChange = { v -> onEdit { it.copy(maxLevels = v.toInt().coerceIn(BlockStackerProtocol.LEVELS_MIN, BlockStackerProtocol.LEVELS_MAX)) } },
    )
    SettingSlider(
        label = stringResource(R.string.blockstacker_initial_speed),
        help = stringResource(R.string.blockstacker_initial_speed_help),
        shown = tenths(draft.initialSpeed),
        value = draft.initialSpeed.toFloat(),
        range = BlockStackerProtocol.INITIAL_SPEED_MIN.toFloat()..BlockStackerProtocol.INITIAL_SPEED_MAX.toFloat(),
        steps = 98,
        tag = BlockStackerTags.INITIAL_SPEED,
        valueTag = BlockStackerTags.INITIAL_SPEED_VALUE,
        onChange = { v -> onEdit { it.copy(initialSpeed = BlockStackerSettings.roundToTenth(v.toDouble())) } },
    )
    SettingSlider(
        label = stringResource(R.string.blockstacker_speed_increment),
        help = stringResource(R.string.blockstacker_speed_increment_help),
        shown = tenths(draft.speedIncrement),
        value = draft.speedIncrement.toFloat(),
        range = BlockStackerProtocol.SPEED_INCREMENT_MIN.toFloat()..BlockStackerProtocol.SPEED_INCREMENT_MAX.toFloat(),
        steps = 49,
        tag = BlockStackerTags.SPEED_INCREMENT,
        valueTag = BlockStackerTags.SPEED_INCREMENT_VALUE,
        onChange = { v -> onEdit { it.copy(speedIncrement = BlockStackerSettings.roundToTenth(v.toDouble())) } },
    )

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.blockstacker_game_enabled), modifier = Modifier.weight(1f), fontSize = 16.sp)
            Switch(
                checked = draft.gameEnabled,
                onCheckedChange = { checked -> onEdit { it.copy(gameEnabled = checked) } },
                modifier = Modifier.testTag(BlockStackerTags.GAME_ENABLED),
                colors = SwitchDefaults.colors(checkedTrackColor = AppColors.PrimaryGreen, checkedThumbColor = AppColors.OnPrimary),
            )
        }
        Text(stringResource(R.string.blockstacker_game_enabled_help), color = AppColors.OnSurfaceMedium, fontSize = 13.sp)
    }

    Button(
        onClick = onSave,
        enabled = ui.connected,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(BlockStackerTags.SAVE),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
    ) { Text(stringResource(R.string.blockstacker_save), fontWeight = FontWeight.SemiBold) }
    if (ui.dirty) {
        Text(
            stringResource(R.string.blockstacker_unsaved),
            color = AppColors.OnSurfaceMedium,
            fontSize = 13.sp,
            modifier = Modifier.testTag(BlockStackerTags.UNSAVED),
        )
    }
    OutlinedButton(
        onClick = onResetDefaults,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(BlockStackerTags.RESET),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.OnSurface),
    ) { Text(stringResource(R.string.blockstacker_reset_defaults), fontWeight = FontWeight.SemiBold) }
}

private fun tenths(value: Double): String = String.format(Locale.US, "%.1f", value)

/** A labelled slider with its value and a help line. It edits the draft only. */
@Composable
private fun SettingSlider(
    label: String,
    help: String,
    shown: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    tag: String,
    valueTag: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
            Text(shown, color = AppColors.OnSurfaceMedium, fontSize = 15.sp, modifier = Modifier.testTag(valueTag))
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.PrimaryGreen,
                activeTrackColor = AppColors.PrimaryGreen,
                // Dots on 50 to 100 steps would only be noise.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(help, color = AppColors.OnSurfaceMedium, fontSize = 13.sp)
    }
}

@Composable
private fun StatusSection(state: BlockStackerState?, zone: ZoneId) {
    SectionTitle(stringResource(R.string.blockstacker_status_title))
    if (state == null) {
        Text(stringResource(R.string.blockstacker_connecting), color = AppColors.OnSurfaceMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusLine(stringResource(R.string.blockstacker_status), state.status, BlockStackerTags.STATUS)
        StatusLine(stringResource(R.string.blockstacker_total_games), state.totalGamesPlayed.toString(), BlockStackerTags.TOTAL_GAMES)
        StatusLine(
            stringResource(R.string.blockstacker_high_score),
            stringResource(R.string.blockstacker_high_score_value, state.highScore),
            BlockStackerTags.HIGH_SCORE,
        )
        StatusLine(stringResource(R.string.blockstacker_last_updated), state.lastUpdated?.let { formatDateTime(it, zone) } ?: "", BlockStackerTags.LAST_UPDATED)
    }
}

@Composable
private fun StatusLine(label: String, value: String, tag: String) {
    Row(Modifier.fillMaxWidth().testTag(tag), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(value, color = AppColors.OnSurfaceMedium, fontSize = 15.sp, modifier = Modifier.testTag("$tag.value"))
    }
}

@Composable
private fun HowToPlay() {
    SectionTitle(stringResource(R.string.blockstacker_how_title))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            R.string.blockstacker_how_1,
            R.string.blockstacker_how_2,
            R.string.blockstacker_how_3,
            R.string.blockstacker_how_4,
            R.string.blockstacker_how_5,
        ).forEach { Text("•  " + stringResource(it), color = AppColors.OnSurfaceMedium, fontSize = 14.sp) }
    }
}

@Composable
private fun LogSection(log: List<LogEntry>, zone: ZoneId) {
    SectionTitle(stringResource(R.string.blockstacker_log_title))
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag(BlockStackerTags.LOG),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (log.isEmpty()) {
            Text(stringResource(R.string.blockstacker_log_empty), color = AppColors.OnSurfaceMedium, fontSize = 13.sp, modifier = Modifier.testTag(BlockStackerTags.LOG_EMPTY))
        }
        log.forEach { entry ->
            Text(
                "[${formatTime(entry.timeMillis, zone)}] ${entry.type}: ${entry.payload}",
                color = AppColors.OnSurfaceMedium,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun formatTime(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withZone(zone).format(Instant.ofEpochMilli(millis))

private fun formatDateTime(at: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(zone).format(at)
