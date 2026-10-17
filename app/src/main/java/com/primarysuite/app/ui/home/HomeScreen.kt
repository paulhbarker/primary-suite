package com.primarysuite.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.primarysuite.app.R
import com.primarysuite.app.catalog.Game
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.ui.components.SectionHeader
import com.primarysuite.app.ui.components.StatusCard
import com.primarysuite.app.ui.components.StatusRow
import com.primarysuite.app.ui.components.StatusTone
import com.primarysuite.app.ui.theme.AppColors

object HomeTags {
    const val WIFI_STATUS = "home.wifi"
    const val SERVER_STATUS = "home.server"
    fun game(slug: String) = "home.game.$slug"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onGameClick: (Game) -> Unit,
    onSettingsClick: () -> Unit,
    onWifiClick: () -> Unit,
) {
    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_open_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionHeader(stringResource(R.string.home_section_status)) }
            item { ConnectionStatus(state, onWifiClick) }
            item { SectionHeader(stringResource(R.string.home_section_games), Modifier.padding(top = 8.dp)) }
            items(state.games, key = { it.slug }) { game ->
                GameRow(game, onClick = { onGameClick(game) })
            }
        }
    }
}

@Composable
private fun ConnectionStatus(state: HomeUiState, onWifiClick: () -> Unit) {
    val (wifiValue, wifiTone) = when (state.wifiState) {
        WifiConnectionState.CONNECTED -> stringResource(R.string.home_wifi_connected) to StatusTone.OK
        WifiConnectionState.CONNECTING -> stringResource(R.string.home_wifi_connecting) to StatusTone.BUSY
        WifiConnectionState.DISCONNECTED -> stringResource(R.string.home_wifi_disconnected) to StatusTone.BAD
        WifiConnectionState.ERROR -> stringResource(R.string.home_wifi_error) to StatusTone.BAD
    }
    val wifiOk = state.wifiState == WifiConnectionState.CONNECTED || state.wifiState == WifiConnectionState.CONNECTING
    val (serverValue, serverTone) = when (state.serverStatus) {
        ServerStatus.WAITING_FOR_WIFI -> stringResource(R.string.home_server_waiting) to StatusTone.NEUTRAL
        ServerStatus.CHECKING -> stringResource(R.string.home_server_checking) to StatusTone.BUSY
        ServerStatus.REACHABLE -> stringResource(R.string.home_server_reachable) to StatusTone.OK
        ServerStatus.UNREACHABLE -> stringResource(R.string.home_server_unreachable) to StatusTone.BAD
    }
    StatusCard {
        StatusRow(
            label = stringResource(R.string.home_label_wifi),
            value = wifiValue,
            tone = wifiTone,
            detail = if (wifiOk) state.wifiSsid else stringResource(R.string.home_wifi_tap_to_retry, state.wifiSsid),
            modifier = Modifier
                .testTag(HomeTags.WIFI_STATUS)
                .clickable(enabled = !wifiOk, onClick = onWifiClick),
        )
        StatusRow(
            label = stringResource(R.string.home_label_server),
            value = serverValue,
            tone = serverTone,
            detail = state.server.toString(),
            modifier = Modifier.testTag(HomeTags.SERVER_STATUS),
        )
    }
}

@Composable
private fun GameRow(game: Game, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTags.game(game.slug))
            .background(AppColors.Surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .alpha(if (game.hasTools) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(AppColors.SurfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(gameIcon(game.slug), contentDescription = null, tint = if (game.hasTools) AppColors.PrimaryGreen else AppColors.OnSurfaceMedium)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(game.name, style = MaterialTheme.typography.titleMedium, color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (game.hasTools) game.blurb else stringResource(R.string.home_tools_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.OnSurfaceMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (game.hasTools) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AppColors.OnSurfaceMedium)
        }
    }
}

fun gameIcon(slug: String): ImageVector = when (slug) {
    "lucky-tap" -> Icons.Outlined.Nfc
    "jeopardy" -> Icons.Outlined.GridView
    "state-switch" -> Icons.Outlined.Bolt
    "reel-rush" -> Icons.Outlined.Casino
    "block-stacker" -> Icons.Outlined.Layers
    "button-debug" -> Icons.Outlined.Memory
    else -> Icons.Outlined.Extension
}
