package com.primarysuite.app.games.jeopardy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.OfflineBadge
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.ui.theme.AppColors

/** Wires the host screen to its view model. */
@Composable
fun JeopardyHostRoute(vm: JeopardyHostViewModel, serverBase: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val boards by vm.boards.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }

    // Hosting takes a while: don't let the phone sleep mid-game.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val now = rememberNow(active = (state?.deadline ?: 0L) != 0L)
    JeopardyHostScreen(
        state = state,
        connected = connected,
        boards = boards,
        countdown = Countdown.of(state, vm.clockOffsetMs, now),
        serverBase = serverBase,
        snackbar = snackbar,
        send = vm::send,
        onRefreshBoards = vm::refreshBoards,
        onBack = onBack,
    )
}

private val PHASE_LABELS = mapOf(
    Phase.LOBBY to R.string.phase_lobby,
    Phase.ROUND_INTRO to R.string.phase_round_intro,
    Phase.BOARD to R.string.phase_board,
    Phase.CLUE to R.string.phase_clue,
    Phase.ARMED to R.string.phase_armed,
    Phase.ANSWERING to R.string.phase_answering,
    Phase.DD_WAGER to R.string.phase_daily_double,
    Phase.DD_CLUE to R.string.phase_daily_double,
    Phase.REVEAL to R.string.phase_reveal,
    Phase.ROUND_END to R.string.phase_round_end,
    Phase.GAME_OVER to R.string.phase_game_over,
)

/** The host controller (host/Host.tsx). Below 600 dp one column; wider, game | players. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeopardyHostScreen(
    state: HostState?,
    connected: Boolean,
    boards: BoardsState,
    countdown: Countdown,
    serverBase: String,
    snackbar: SnackbarHostState,
    send: (GameMessage) -> Unit,
    onRefreshBoards: () -> Unit,
    onBack: () -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val confirm: (String, () -> Unit) -> Unit = { message, action -> confirmation = message to action }

    Scaffold(
        containerColor = AppColors.Background,
        contentColor = AppColors.OnSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.jeopardy_title), color = JeopardyColors.Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            state?.boardName?.let {
                                Text("  $it", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.OnSurface)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                    },
                    actions = {
                        if (state != null) {
                            PhaseBadge(state.phase)
                            if (!connected) OfflineBadge(stringResource(R.string.game_offline))
                            IconButton(onClick = { send(HostCommands.undo()) }, enabled = state.canUndo, modifier = Modifier.testTag(JeopardyTags.UNDO)) {
                                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.jeopardy_undo))
                            }
                            IconButton(onClick = { settingsOpen = true }, modifier = Modifier.testTag(JeopardyTags.SETTINGS)) {
                                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.jeopardy_settings))
                            }
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(JeopardyTags.MORE)) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.jeopardy_more))
                            }
                            OverflowMenu(menuOpen, state.phase, onDismiss = { menuOpen = false }, send = send, confirm = confirm)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
                )
                state?.player(state.assigningButtonFor)?.let { assigning ->
                    Row(
                        Modifier.fillMaxWidth().background(AppColors.PrimaryGreen.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag(JeopardyTags.ASSIGN_BANNER),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.jeopardy_assign_banner, assigning.name), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        TextButton(onClick = { send(HostCommands.assignButton(null)) }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        },
    ) { padding ->
        if (state == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Text(
                    stringResource(if (connected) R.string.jeopardy_loading else R.string.jeopardy_connecting),
                    color = AppColors.OnSurfaceMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            return@Scaffold
        }

        val main: @Composable () -> Unit = {
            if (state.phase == Phase.LOBBY) {
                LobbyPanel(state, boards, send, onRefreshBoards)
            } else {
                GamePanel(state, countdown, serverBase, send, confirm)
            }
        }
        val side: @Composable () -> Unit = {
            PlayersPanel(state, send, confirm)
            ScoreLog(state.log)
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { main() }
                    Column(
                        Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { side() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    main()
                    side()
                }
            }
        }
    }

    if (settingsOpen && state != null) {
        SettingsDialog(
            settings = state.settings,
            onSave = { send(HostCommands.updateSettings(it)); settingsOpen = false },
            onClose = { settingsOpen = false },
        )
    }
    confirmation?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            text = { Text(message, fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = { confirmation = null; action() }, modifier = Modifier.testTag(JeopardyTags.CONFIRM)) {
                    Text(stringResource(R.string.jeopardy_confirm), color = AppColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = AppColors.SurfaceVariant,
        )
    }
}

@Composable
private fun PhaseBadge(phase: Phase) {
    val armed = phase == Phase.ARMED
    Text(
        stringResource(PHASE_LABELS.getValue(phase)),
        modifier = Modifier
            .background(if (armed) AppColors.Success else AppColors.SurfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag(JeopardyTags.PHASE),
        color = if (armed) Color.White else AppColors.OnSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun OverflowMenu(open: Boolean, phase: Phase, onDismiss: () -> Unit, send: (GameMessage) -> Unit, confirm: (String, () -> Unit) -> Unit) {
    val endConfirm = stringResource(R.string.jeopardy_end_game_confirm)
    val resetConfirm = stringResource(R.string.jeopardy_reset_game_confirm)
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (phase != Phase.LOBBY && phase != Phase.GAME_OVER) {
            DropdownMenuItem(
                text = { MenuText(stringResource(R.string.jeopardy_end_game), stringResource(R.string.jeopardy_end_game_help)) },
                onClick = { onDismiss(); confirm(endConfirm) { send(HostCommands.endGame()) } },
            )
        }
        DropdownMenuItem(
            text = { MenuText(stringResource(R.string.jeopardy_reset_game), stringResource(R.string.jeopardy_reset_game_help)) },
            onClick = { onDismiss(); confirm(resetConfirm) { send(HostCommands.resetGame()) } },
        )
    }
}

@Composable
private fun MenuText(title: String, help: String) {
    Column {
        Text(title)
        Text(help, fontSize = 12.sp, color = AppColors.OnSurfaceMedium)
    }
}
