package com.primarysuite.app.games.jeopardy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.primarysuite.app.R
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object DrainTags {
    const val OPEN = "jeopardy.drain.open"
    const val CLOSE = "jeopardy.drain.close"
    fun player(id: String) = "jeopardy.drain.player.$id"
}

/**
 * Press and hold to repeat [onTick]: once at once, then every
 * [HostCommands.DRAIN_INTERVAL_MS] until released. [onHoldChange] reports
 * when the hold starts and ends, for styling.
 */
fun Modifier.holdToRepeat(onHoldChange: (Boolean) -> Unit = {}, onTick: () -> Unit): Modifier = composed {
    val tick by rememberUpdatedState(onTick)
    val holdChange by rememberUpdatedState(onHoldChange)
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            holdChange(true)
            val job = scope.launch {
                while (isActive) {
                    tick()
                    delay(HostCommands.DRAIN_INTERVAL_MS)
                }
            }
            waitForUpOrCancellation()
            job.cancel()
            holdChange(false)
        }
    }
}

/** The red "Drain points" button under the scores. */
@Composable
fun DrainPointsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(DrainTags.OPEN),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error, contentColor = Color.White),
    ) {
        Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(" " + stringResource(R.string.drain_points), fontWeight = FontWeight.SemiBold)
    }
}

/**
 * One big button per player, sharing the screen's height evenly. Holding a
 * button drains that player's points, 5 every quarter second, as a penalty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrainScreen(players: List<Player>, send: (GameMessage) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = AppColors.Background,
            contentColor = AppColors.OnSurface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.drain_points)) },
                    navigationIcon = {
                        IconButton(onClick = onClose, modifier = Modifier.testTag(DrainTags.CLOSE)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.drain_close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.drain_hint),
                    color = AppColors.OnSurfaceMedium,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (players.isEmpty()) {
                    Text(stringResource(R.string.players_none), color = AppColors.OnSurfaceMedium)
                }
                players.forEach { player ->
                    DrainPlayerButton(player, Modifier.weight(1f)) { send(HostCommands.drainTick(player.id)) }
                }
            }
        }
    }
}

@Composable
private fun DrainPlayerButton(player: Player, modifier: Modifier, onTick: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    val accent = parseColor(player.color)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            // A normal button until held; red while it drains.
            .background(if (holding) AppColors.Error else AppColors.SurfaceVariant, shape)
            .border(1.dp, if (holding) AppColors.Error else AppColors.Outline, shape)
            .holdToRepeat(onHoldChange = { holding = it }, onTick = onTick)
            .testTag(DrainTags.player(player.id))
            .semantics { contentDescription = "${player.name} ${money(player.score)}" },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (accent != null) {
                Box(Modifier.size(18.dp).background(accent, RoundedCornerShape(9.dp)).border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(9.dp)))
                Box(Modifier.width(12.dp))
            }
            Text(
                player.name,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                money(player.score),
                color = if (!holding && player.score < 0) JeopardyColors.Negative else Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
