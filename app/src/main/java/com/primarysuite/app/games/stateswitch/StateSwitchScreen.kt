package com.primarysuite.app.games.stateswitch

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.OfflineBadge
import com.primarysuite.app.core.ui.Picker
import com.primarysuite.app.core.ui.PickerOption
import com.primarysuite.app.core.ui.PressButton
import com.primarysuite.app.ui.theme.AppColors
import java.util.Locale

object StateSwitchTags {
    const val SWITCH = "stateSwitch.switch"
    const val GAME = "stateSwitch.game"
    const val TIMEOUT = "stateSwitch.timeout"
    const val TIMEOUT_VALUE = "stateSwitch.timeout.value"
    const val LIMIT_ENABLED = "stateSwitch.limitEnabled"
    const val LIMIT = "stateSwitch.limit"
    const val LIMIT_VALUE = "stateSwitch.limit.value"
    const val RESET_ACTUATIONS = "stateSwitch.resetActuations"
    const val RESET_ALL = "stateSwitch.resetAll"
    const val CONFIRM = "stateSwitch.confirm"
    const val CONNECTING = "stateSwitch.connecting"
    const val DISPLAY = "stateSwitch.display"
}

/** Wires the screen to its view model. */
@Composable
fun StateSwitchRoute(vm: StateSwitchViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }

    // The Switch button is used all through a game: don't let the phone sleep.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var display by rememberSaveable { mutableStateOf(false) }
    if (display) {
        BackHandler { display = false }
        StateSwitchDisplay(ui)
        return
    }

    StateSwitchScreen(
        ui = ui,
        snackbar = snackbar,
        onSettings = { next -> vm.updateSettings { next } },
        onSwitch = vm::switch,
        onResetActuations = vm::resetActuations,
        onResetAll = vm::resetAll,
        onBack = onBack,
        onDisplay = { display = true },
    )
}

/** The web admin (StateSwitchAdmin.tsx), plus a Switch button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateSwitchScreen(
    ui: StateSwitchUiState,
    snackbar: SnackbarHostState,
    onSettings: (StateSwitchSettings) -> Unit,
    onSwitch: () -> Unit,
    onResetActuations: () -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit,
    onDisplay: () -> Unit = {},
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.Background,
        contentColor = AppColors.OnSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.state_switch_title))
                        if (!ui.connected && ui.settings != null) OfflineBadge(stringResource(R.string.game_offline))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                actions = {
                    IconButton(
                        onClick = onDisplay,
                        enabled = ui.settings != null,
                        modifier = Modifier.testTag(StateSwitchTags.DISPLAY),
                    ) { Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(R.string.state_switch_display)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        val settings = ui.settings
        if (settings == null) {
            // Like the web: nothing to edit until the server has sent the settings.
            Box(Modifier.fillMaxSize().padding(padding).testTag(StateSwitchTags.CONNECTING), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.state_switch_connecting), color = AppColors.OnSurfaceMedium)
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
                SwitchButton(ui, onSwitch)
                HorizontalDivider(color = AppColors.Outline)
                Settings(settings, enabled = ui.connected, onSettings = onSettings)

                Button(
                    onClick = onResetActuations,
                    enabled = ui.connected,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(StateSwitchTags.RESET_ACTUATIONS),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
                ) { Text(stringResource(R.string.state_switch_reset_actuations), fontWeight = FontWeight.SemiBold) }

                Button(
                    onClick = { confirmReset = true },
                    enabled = ui.connected,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(StateSwitchTags.RESET_ALL),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error, contentColor = Color.White),
                ) { Text(stringResource(R.string.state_switch_reset_all), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.state_switch_reset_all_title)) },
            text = { Text(stringResource(R.string.state_switch_reset_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetAll()
                    },
                    modifier = Modifier.testTag(StateSwitchTags.CONFIRM),
                ) { Text(stringResource(R.string.state_switch_reset_all), color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Switches the display like the physical button; held back by the same timeout and limit. */
@Composable
private fun SwitchButton(ui: StateSwitchUiState, onSwitch: () -> Unit) {
    val label = when {
        !ui.connected -> R.string.state_switch_switch
        ui.limitReached -> R.string.state_switch_limit_reached
        ui.cooldown -> R.string.state_switch_cooling_down
        else -> R.string.state_switch_switch
    }
    // Fires on touch, like the physical button.
    PressButton(
        text = stringResource(label),
        onPress = onSwitch,
        enabled = ui.canSwitch,
        modifier = Modifier.testTag(StateSwitchTags.SWITCH),
    )
}

@Composable
private fun Settings(settings: StateSwitchSettings, enabled: Boolean, onSettings: (StateSwitchSettings) -> Unit) {
    val options = GameType.entries.map { PickerOption(it.slug, it.label) }.let { known ->
        // Keep an unknown type from the server visible rather than blank.
        if (GameType.of(settings.game) == null) known + PickerOption(settings.game, settings.game) else known
    }
    Picker(
        label = stringResource(R.string.state_switch_game),
        options = options,
        selected = settings.game,
        onSelect = { onSettings(settings.copy(game = it)) },
        tag = StateSwitchTags.GAME,
        enabled = enabled,
    )

    ReleaseSlider(
        label = stringResource(R.string.state_switch_timeout),
        value = settings.timeout,
        range = 0f..StateSwitchProtocol.TIMEOUT_MAX,
        steps = (StateSwitchProtocol.TIMEOUT_MAX / StateSwitchProtocol.TIMEOUT_STEP).toInt() - 1,
        format = { stringResource(R.string.state_switch_seconds, String.format(Locale.US, "%.1f", StateSwitchSettings.roundTimeout(it))) },
        enabled = enabled,
        tag = StateSwitchTags.TIMEOUT,
        valueTag = StateSwitchTags.TIMEOUT_VALUE,
        // 90 steps: their dots would just be noise.
        showTicks = false,
        onRelease = { onSettings(settings.copy(timeout = StateSwitchSettings.roundTimeout(it))) },
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.state_switch_enforce_limit), modifier = Modifier.weight(1f), fontSize = 16.sp)
        Checkbox(
            checked = settings.limitEnabled,
            onCheckedChange = { onSettings(settings.copy(limitEnabled = it)) },
            enabled = enabled,
            modifier = Modifier.testTag(StateSwitchTags.LIMIT_ENABLED),
            colors = CheckboxDefaults.colors(checkedColor = AppColors.PrimaryGreen, checkmarkColor = AppColors.OnPrimary),
        )
    }

    if (settings.limitEnabled) {
        ReleaseSlider(
            label = stringResource(R.string.state_switch_limit),
            value = settings.limit.toFloat(),
            range = 0f..StateSwitchProtocol.LIMIT_MAX.toFloat(),
            steps = StateSwitchProtocol.LIMIT_MAX / StateSwitchProtocol.LIMIT_STEP - 1,
            format = { StateSwitchSettings.roundLimit(it).toString() },
            enabled = enabled,
            tag = StateSwitchTags.LIMIT,
            valueTag = StateSwitchTags.LIMIT_VALUE,
            onRelease = { onSettings(settings.copy(limit = StateSwitchSettings.roundLimit(it))) },
        )
    }
}

/**
 * A labelled slider that shows the value while dragging and reports it once,
 * on release. Until then the server's value stands.
 */
@Composable
private fun ReleaseSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: @Composable (Float) -> String,
    enabled: Boolean,
    tag: String,
    valueTag: String,
    showTicks: Boolean = true,
    onRelease: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
            Text(format(shown), color = AppColors.OnSurfaceMedium, fontSize = 15.sp, modifier = Modifier.testTag(valueTag))
        }
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onRelease)
                dragging = null
            },
            valueRange = range,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.testTag(tag),
            colors = if (showTicks) {
                SliderDefaults.colors(thumbColor = AppColors.PrimaryGreen, activeTrackColor = AppColors.PrimaryGreen)
            } else {
                SliderDefaults.colors(
                    thumbColor = AppColors.PrimaryGreen,
                    activeTrackColor = AppColors.PrimaryGreen,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent,
                )
            },
        )
    }
}
