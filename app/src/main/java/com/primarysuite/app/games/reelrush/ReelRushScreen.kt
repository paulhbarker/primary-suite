package com.primarysuite.app.games.reelrush

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

object ReelRushTags {
    const val TAB_GAME = "reelRush.tab.game"
    const val TAB_SETTINGS = "reelRush.tab.settings"
    const val CONNECTING = "reelRush.connecting"
    const val PHASE = "reelRush.phase"
    const val PRIMARY = "reelRush.primary"
    const val REPLAY = "reelRush.replay"
    const val END_GAME = "reelRush.endGame"
    const val RESET = "reelRush.reset"
    const val CONFIRM = "reelRush.confirm"
    const val LAST_ROUND = "reelRush.lastRound"
    const val FINAL = "reelRush.final"

    fun player(slot: Int) = "reelRush.player.$slot"
    fun remove(slot: Int) = "reelRush.remove.$slot"
    fun result(slot: Int) = "reelRush.result.$slot"

    const val REELS = "reelRush.set.reels"
    const val ROUNDS = "reelRush.set.rounds"
    const val HARD_MODE = "reelRush.set.hardMode"
    const val ASSIST = "reelRush.set.assist"
    const val ROW_DECOYS = "reelRush.set.rowDecoys"
    const val ROUND_MIN = "reelRush.set.roundMin"
    const val ROUND_MAX = "reelRush.set.roundMax"
    const val NEAR_MISSES = "reelRush.set.nearMisses"
    const val SPIN_SPEED = "reelRush.set.spinSpeed"
    const val DWELL_MIN = "reelRush.set.dwellMin"
    const val DWELL_MAX = "reelRush.set.dwellMax"
    const val DISPLAY_LAG = "reelRush.set.displayLag"
    const val VOLUME = "reelRush.set.volume"
    const val SAVE = "reelRush.set.save"
    const val DISCARD = "reelRush.set.discard"
    fun name(index: Int) = "reelRush.set.name.$index"
}

/** What the screen can ask for. */
class ReelRushActions(
    val onOpenLobby: () -> Unit,
    val onRemovePlayer: (Int) -> Unit,
    val onStart: () -> Unit,
    val onCloseRound: () -> Unit,
    val onNextRound: () -> Unit,
    val onReplayRound: () -> Unit,
    val onEndGame: () -> Unit,
    val onReset: () -> Unit,
    val onSaveSettings: (ReelRushSettings) -> Unit,
    val onBack: () -> Unit,
)

/** Wires the screen to its view model. */
@Composable
fun ReelRushRoute(vm: ReelRushViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }

    // A game runs for a long time and the socket must stay up: don't let the phone sleep.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    ReelRushScreen(
        ui = ui,
        snackbar = snackbar,
        actions = ReelRushActions(
            onOpenLobby = vm::openLobby,
            onRemovePlayer = vm::removePlayer,
            onStart = vm::startGame,
            onCloseRound = vm::closeRound,
            onNextRound = vm::nextRound,
            onReplayRound = vm::replayRound,
            onEndGame = vm::endGame,
            onReset = vm::reset,
            onSaveSettings = { vm.saveSettings(it) },
            onBack = onBack,
        ),
    )
}

private enum class Confirm { END_GAME, RESET }

/** The web admin (Admin.tsx): the game controls and the settings, in two tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelRushScreen(ui: ReelRushUiState, snackbar: SnackbarHostState, actions: ReelRushActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    val state = ui.state

    // The settings draft lives here so it survives a tab switch. Like the web, it follows the
    // server until the host edits, and a new server value replaces an unsaved draft.
    var edited by remember { mutableStateOf<SettingsForm?>(null) }
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
                            Text(stringResource(R.string.reelrush_title))
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
                        TabItem(tab == 0, R.string.reelrush_tab_game, ReelRushTags.TAB_GAME) { tab = 0 }
                        TabItem(tab == 1, R.string.reelrush_tab_settings, ReelRushTags.TAB_SETTINGS) { tab = 1 }
                    }
                }
            }
        },
    ) { padding ->
        if (state == null) {
            // Like the web: nothing to show until the server has sent the game.
            Box(Modifier.fillMaxSize().padding(padding).testTag(ReelRushTags.CONNECTING), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.reelrush_connecting), color = AppColors.OnSurfaceMedium)
                }
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                if (tab == 0) {
                    GamePanel(
                        state = state,
                        enabled = ui.connected,
                        actions = actions,
                        onConfirmEnd = { confirm = Confirm.END_GAME },
                        onConfirmReset = { confirm = Confirm.RESET },
                    )
                } else {
                    val form = edited ?: SettingsForm.from(state.settings)
                    val dirty = edited != null && edited?.toSettings() != state.settings
                    SettingsPanel(
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

    confirm?.let { which ->
        val (title, message, label, action) = when (which) {
            Confirm.END_GAME -> ConfirmText(R.string.reelrush_end_game_title, R.string.reelrush_end_game_message, R.string.reelrush_end_game, actions.onEndGame)
            Confirm.RESET -> ConfirmText(R.string.reelrush_reset_title, R.string.reelrush_reset_message, R.string.reelrush_reset, actions.onReset)
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        action()
                    },
                    modifier = Modifier.testTag(ReelRushTags.CONFIRM),
                ) { Text(stringResource(label), color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private data class ConfirmText(val title: Int, val message: Int, val label: Int, val action: () -> Unit)

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
