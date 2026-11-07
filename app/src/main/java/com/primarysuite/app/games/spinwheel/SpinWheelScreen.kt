package com.primarysuite.app.games.spinwheel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.OfflineBadge
import com.primarysuite.app.ui.theme.AppColors

object SpinWheelTags {
    const val TAB_WHEEL = "spinWheel.tab.wheel"
    const val TAB_ODDS = "spinWheel.tab.odds"
    const val TAB_SETTINGS = "spinWheel.tab.settings"
    const val CONNECTING = "spinWheel.connecting"
    const val PHASE = "spinWheel.phase"
    const val SPIN = "spinWheel.spin"
    const val LOCK = "spinWheel.lock"
    const val LAST_RESULT = "spinWheel.lastResult"
    const val FORCE_TITLE = "spinWheel.force.title"
    const val FORCE_CLEAR = "spinWheel.force.clear"
    const val HISTORY_TITLE = "spinWheel.history.title"
    const val CLEAR_HISTORY = "spinWheel.history.clear"
    const val CONFIRM = "spinWheel.confirm"
    const val EVEN = "spinWheel.odds.even"
    const val SPIN_MIN = "spinWheel.set.spinMin"
    const val SPIN_MAX = "spinWheel.set.spinMax"
    const val CELEBRATE = "spinWheel.set.celebrate"
    const val VOLUME = "spinWheel.set.volume"
    const val SAVE = "spinWheel.set.save"
    const val DISCARD = "spinWheel.set.discard"

    fun force(value: Int) = "spinWheel.force.$value"
    fun count(value: Int) = "spinWheel.count.$value"
    fun historyRow(id: Int) = "spinWheel.history.$id"
    fun weight(value: Int) = "spinWheel.weight.$value"
    fun less(value: Int) = "spinWheel.less.$value"
    fun more(value: Int) = "spinWheel.more.$value"
}

/** What the screen can ask for. */
class SpinWheelActions(
    val onSpin: () -> Unit,
    val onSetLocked: (Boolean) -> Unit,
    val onForceNext: (Int?) -> Unit,
    val onSetWeight: (value: Int, weight: Int) -> Unit,
    val onEvenOdds: () -> Unit,
    val onClearHistory: () -> Unit,
    val onSaveSettings: (WheelSettings) -> Unit,
    val onBack: () -> Unit,
)

/** Wires the screen to its view model. */
@Composable
fun SpinWheelRoute(vm: SpinWheelViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }

    // The wheel stays up for a whole event and the socket must stay open: don't let the phone sleep.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    SpinWheelScreen(
        ui = ui,
        snackbar = snackbar,
        actions = SpinWheelActions(
            onSpin = { vm.spin() },
            onSetLocked = { vm.setLocked(it) },
            onForceNext = { vm.forceNext(it) },
            onSetWeight = vm::setWeight,
            onEvenOdds = vm::evenOdds,
            onClearHistory = { vm.clearHistory() },
            onSaveSettings = { vm.saveSettings(it) },
            onBack = onBack,
        ),
    )
}

/** The web admin (Admin.tsx) in three tabs: the wheel, the odds and the settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinWheelScreen(ui: SpinWheelUiState, snackbar: SnackbarHostState, actions: SpinWheelActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val state = ui.state

    // The settings draft lives here so it survives a tab switch. Like the web, it follows the
    // server until the host edits, and a new server value replaces an unsaved draft.
    var edited by remember { mutableStateOf<WheelSettingsForm?>(null) }
    LaunchedEffect(state?.settings) { edited = null }

    Scaffold(
        containerColor = AppColors.Background,
        contentColor = AppColors.OnSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.spinwheel_title))
                            if (!ui.connected && state != null) OfflineBadge(stringResource(R.string.game_offline))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
                )
                if (state != null) {
                    PrimaryTabRow(selectedTabIndex = tab, containerColor = AppColors.Background) {
                        TabItem(tab == 0, R.string.spinwheel_tab_wheel, SpinWheelTags.TAB_WHEEL) { tab = 0 }
                        TabItem(tab == 1, R.string.spinwheel_tab_odds, SpinWheelTags.TAB_ODDS) { tab = 1 }
                        TabItem(tab == 2, R.string.spinwheel_tab_settings, SpinWheelTags.TAB_SETTINGS) { tab = 2 }
                    }
                }
            }
        },
    ) { padding ->
        if (state == null) {
            // Like the web: nothing to show until the server has sent the wheel.
            Box(Modifier.fillMaxSize().padding(padding).testTag(SpinWheelTags.CONNECTING), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.spinwheel_connecting), color = AppColors.OnSurfaceMedium)
                }
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                when (tab) {
                    0 -> WheelPanel(state, ui.clockOffsetMs, ui.connected, actions, onConfirmClear = { confirmClear = true })
                    1 -> OddsPanel(state, ui.connected, actions)
                    else -> {
                        val form = edited ?: WheelSettingsForm.from(state.settings)
                        val dirty = edited != null && edited?.toSettings() != state.settings
                        WheelSettingsPanel(
                            form = form,
                            dirty = dirty,
                            enabled = ui.connected,
                            onChange = { edited = it },
                            onSave = { form.toSettings()?.let(actions.onSaveSettings) },
                            onDiscard = { edited = null },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.spinwheel_clear_history)) },
            text = { Text(stringResource(R.string.spinwheel_clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        actions.onClearHistory()
                    },
                    modifier = Modifier.testTag(SpinWheelTags.CONFIRM),
                ) { Text(stringResource(R.string.spinwheel_clear_history), color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TabItem(selected: Boolean, label: Int, tag: String, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        selectedContentColor = AppColors.PrimaryGreen,
        unselectedContentColor = AppColors.OnSurfaceMedium,
        text = { Text(stringResource(label)) },
        modifier = Modifier.testTag(tag),
    )
}
