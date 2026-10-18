package com.primarysuite.app.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.primarysuite.app.R
import com.primarysuite.app.catalog.Game
import com.primarysuite.app.catalog.GameCatalog
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.net.GameConnection
import com.primarysuite.app.core.net.ServerProbe
import com.primarysuite.app.core.net.gameSocket
import com.primarysuite.app.core.nfc.NfcHub
import com.primarysuite.app.core.ui.StringProvider
import com.primarysuite.app.core.config.serverConfig
import com.primarysuite.app.games.blockstacker.BlockStackerRoute
import com.primarysuite.app.games.buttondebug.ButtonColorStore
import com.primarysuite.app.games.buttondebug.ButtonDebugRoute
import com.primarysuite.app.games.buttondebug.ButtonDebugViewModel
import com.primarysuite.app.games.buttondebug.InMemoryButtonColorStore
import com.primarysuite.app.games.blockstacker.BlockStackerViewModel
import com.primarysuite.app.games.jeopardy.HttpJeopardyBoardsApi
import com.primarysuite.app.games.jeopardy.JeopardyBoardsApi
import com.primarysuite.app.games.jeopardy.JeopardyHostRoute
import com.primarysuite.app.games.jeopardy.JeopardyHostViewModel
import com.primarysuite.app.games.luckytap.LuckyTapRoute
import com.primarysuite.app.games.reelrush.ReelRushRoute
import com.primarysuite.app.games.reelrush.ReelRushViewModel
import com.primarysuite.app.games.stateswitch.StateSwitchRoute
import com.primarysuite.app.games.stateswitch.StateSwitchViewModel
import com.primarysuite.app.games.luckytap.LuckyTapViewModel
import kotlinx.coroutines.CoroutineScope
import com.primarysuite.app.core.wifi.WifiConnector
import com.primarysuite.app.ui.comingsoon.ComingSoonScreen
import com.primarysuite.app.ui.home.HomeScreen
import com.primarysuite.app.ui.home.HomeViewModel
import com.primarysuite.app.ui.settings.SettingsSaved
import com.primarysuite.app.ui.settings.SettingsScreen
import com.primarysuite.app.ui.settings.SettingsViewModel

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val GAME = "game/{slug}"
    fun game(slug: String) = "game/$slug"
}

/** Opens the `/ws/{slug}` socket for a game. Tests pass a fake. */
typealias SocketFactory = (slug: String, scope: CoroutineScope, canConnect: () -> Boolean) -> GameConnection

/** Builds the screens' view models from the app's dependencies (or fakes in tests). */
fun suiteViewModelFactory(
    settings: ConnectionSettings,
    wifi: WifiConnector,
    probe: ServerProbe,
    nfc: NfcHub,
    strings: StringProvider,
    games: List<Game> = GameCatalog.games,
    socketFactory: SocketFactory = { slug, scope, canConnect -> gameSocket(slug, settings, scope, canConnect) },
    jeopardyBoards: JeopardyBoardsApi = HttpJeopardyBoardsApi({ settings.serverConfig.httpUrl }),
    onBuzz: () -> Unit = {},
    /** Remembers which colour each box button is, for the Button Debugger. */
    buttonColors: ButtonColorStore = InMemoryButtonColorStore(),
): ViewModelProvider.Factory = viewModelFactory {
    initializer { HomeViewModel(settings, wifi, probe, games) }
    initializer { SettingsViewModel(settings) }
    initializer {
        LuckyTapViewModel(strings, wifi, nfc, { scope, canConnect ->
            socketFactory(LuckyTapViewModel.GAME_SLUG, scope, canConnect)
        })
    }
    initializer {
        JeopardyHostViewModel(
            wifi = wifi,
            socketFactory = { scope, canConnect -> socketFactory(JeopardyHostViewModel.GAME_SLUG, scope, canConnect) },
            boardsApi = jeopardyBoards,
            onBuzz = onBuzz,
        )
    }
    initializer {
        StateSwitchViewModel(wifi) { scope, canConnect -> socketFactory(StateSwitchViewModel.GAME_SLUG, scope, canConnect) }
    }
    initializer {
        BlockStackerViewModel(wifi, { scope, canConnect -> socketFactory(BlockStackerViewModel.GAME_SLUG, scope, canConnect) })
    }
    initializer {
        ReelRushViewModel(wifi) { scope, canConnect -> socketFactory(ReelRushViewModel.GAME_SLUG, scope, canConnect) }
    }
    initializer {
        ButtonDebugViewModel(
            wifi = wifi,
            socketFactory = { scope, canConnect -> socketFactory(ButtonDebugViewModel.GAME_SLUG, scope, canConnect) },
            colorStore = buttonColors,
        )
    }
}

/**
 * @param onEnableNfc opens the system NFC settings.
 * @param onWifiRetry asks again for WiFi permissions or prompts the user declined.
 * @param onSettingsSaved lets the caller rejoin WiFi when the network changed.
 */
@Composable
fun PrimarySuiteNavHost(
    viewModelFactory: ViewModelProvider.Factory,
    onEnableNfc: () -> Unit,
    onWifiRetry: () -> Unit,
    onSettingsSaved: (SettingsSaved) -> Unit,
    games: List<Game> = GameCatalog.games,
    /** The game server's base URL, for images on the board. */
    serverBase: () -> String = { "" },
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val home: HomeViewModel = viewModel(factory = viewModelFactory)
            val state by home.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onGameClick = { game -> navController.navigate(Routes.game(game.slug)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onWifiClick = onWifiRetry,
            )
        }

        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory)
            // Shared with the home destination's owner so it can refresh after a save.
            val homeEntry = navController.getBackStackEntry(Routes.HOME)
            val home: HomeViewModel = viewModel(homeEntry, factory = viewModelFactory)
            val state by settingsVm.uiState.collectAsStateWithLifecycle()

            fun applied(result: SettingsSaved) {
                home.refresh()
                onSettingsSaved(result)
            }

            SettingsScreen(
                state = state,
                onSsidChange = settingsVm::onWifiSsidChange,
                onPasswordChange = settingsVm::onWifiPasswordChange,
                onHostChange = settingsVm::onServerHostChange,
                onPortChange = settingsVm::onServerPortChange,
                onSave = {
                    settingsVm.save()?.let { result ->
                        applied(result)
                        Toast.makeText(context, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                onReset = {
                    applied(settingsVm.resetToDefaults())
                    Toast.makeText(context, R.string.toast_settings_reset, Toast.LENGTH_SHORT).show()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.GAME) { entry ->
            val game = entry.arguments?.getString("slug")?.let { slug -> games.firstOrNull { it.slug == slug } }
            val onBack: () -> Unit = { navController.popBackStack() }
            when {
                game == null -> onBack()
                game.slug == GameCatalog.LUCKY_TAP -> {
                    // Scoped to this destination: the socket closes when the screen does.
                    val vm: LuckyTapViewModel = viewModel(factory = viewModelFactory)
                    LuckyTapRoute(vm, onBack = onBack, onEnableNfc = onEnableNfc)
                }
                game.slug == GameCatalog.JEOPARDY -> {
                    val vm: JeopardyHostViewModel = viewModel(factory = viewModelFactory)
                    JeopardyHostRoute(vm, serverBase = serverBase(), onBack = onBack)
                }
                game.slug == GameCatalog.STATE_SWITCH -> {
                    val vm: StateSwitchViewModel = viewModel(factory = viewModelFactory)
                    StateSwitchRoute(vm, onBack = onBack)
                }
                game.slug == GameCatalog.BLOCK_STACKER -> {
                    val vm: BlockStackerViewModel = viewModel(factory = viewModelFactory)
                    BlockStackerRoute(vm, onBack = onBack)
                }
                game.slug == GameCatalog.REEL_RUSH -> {
                    val vm: ReelRushViewModel = viewModel(factory = viewModelFactory)
                    ReelRushRoute(vm, onBack = onBack)
                }
                game.slug == GameCatalog.BUTTON_DEBUG -> {
                    val vm: ButtonDebugViewModel = viewModel(factory = viewModelFactory)
                    ButtonDebugRoute(vm, onBack = onBack)
                }
                else -> ComingSoonScreen(game, onBack = onBack)
            }
        }
    }
}
