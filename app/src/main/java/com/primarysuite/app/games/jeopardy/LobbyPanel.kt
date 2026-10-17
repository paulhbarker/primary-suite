package com.primarysuite.app.games.jeopardy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.Picker
import com.primarysuite.app.core.ui.PickerOption
import com.primarysuite.app.ui.theme.AppColors

/** Mirrors host/LobbyPanel.tsx: pick a board and first pick, then start. */
@Composable
fun LobbyPanel(state: HostState, boards: BoardsState, send: (com.primarysuite.app.core.protocol.GameMessage) -> Unit, onRefreshBoards: () -> Unit) {
    val list = boards.boards
    val loaded = list?.firstOrNull { it.id == state.boardId }
    val roundWord = stringResource(R.string.lobby_round)
    val roundsWord = stringResource(R.string.lobby_rounds)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.lobby_new_game), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = AppColors.OnSurface)
        boards.error?.let { StatusBanner(it, Tone.WARNING) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Picker(
                label = stringResource(R.string.lobby_board),
                placeholder = stringResource(R.string.lobby_choose_board),
                options = list.orEmpty().map {
                    PickerOption(it.id, stringResource(R.string.lobby_board_option, it.name, it.roundCount, if (it.roundCount == 1) roundWord else roundsWord))
                },
                selected = loaded?.id,
                onSelect = { send(HostCommands.loadBoard(it)) },
                tag = JeopardyTags.LOBBY_BOARD,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefreshBoards, enabled = !boards.loading, modifier = Modifier.testTag(JeopardyTags.LOBBY_REFRESH)) {
                if (boards.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.lobby_refresh_boards))
                }
            }
        }
        if (state.boardId != null && loaded == null && list != null) {
            Text(stringResource(R.string.lobby_loaded_copy, state.boardName ?: ""), color = AppColors.OnSurfaceMedium, fontSize = 13.sp)
        }

        val random = "__random__"
        Picker(
            label = stringResource(R.string.lobby_first_pick),
            options = listOf(PickerOption(random, stringResource(R.string.lobby_random))) + state.players.map { PickerOption(it.id, it.name) },
            selected = state.control?.takeIf { id -> state.players.any { it.id == id } } ?: random,
            onSelect = { send(HostCommands.setControl(it.takeUnless { v -> v == random })) },
            tag = JeopardyTags.LOBBY_FIRST_PICK,
        )

        BigButton(
            text = stringResource(R.string.lobby_start),
            onClick = { send(HostCommands.startGame()) },
            enabled = state.boardId != null,
            tag = JeopardyTags.LOBBY_START,
        )
        Text(stringResource(R.string.lobby_join_hint), color = AppColors.OnSurfaceDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
