package com.primarysuite.app.games.jeopardy

import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.AppColors
import java.text.NumberFormat
import java.util.Locale

/** The show's colours, as in the web's jeopardy/theme.ts. */
object JeopardyColors {
    val Board = Color(0xFF05127D)
    val BoardDark = Color(0xFF030B4B)
    val Gold = Color(0xFFFFCC00)
    val Negative = Color(0xFFFF5252)
}

object JeopardyTags {
    const val LOBBY_BOARD = "jeopardy.lobby.board"
    const val LOBBY_REFRESH = "jeopardy.lobby.refresh"
    const val LOBBY_FIRST_PICK = "jeopardy.lobby.firstPick"
    const val LOBBY_START = "jeopardy.lobby.start"
    const val PHASE = "jeopardy.phase"
    const val UNDO = "jeopardy.undo"
    const val SETTINGS = "jeopardy.settings"
    const val MORE = "jeopardy.more"
    const val CONFIRM = "jeopardy.confirm"
    const val ASSIGN_BANNER = "jeopardy.assignBanner"
    const val PRIMARY = "jeopardy.primary"
    const val END_ROUND = "jeopardy.endRound"
    const val RESPONSE = "jeopardy.response"
    const val RESPONSE_HIDDEN = "jeopardy.responseHidden"
    const val TIMER = "jeopardy.timer"
    const val RESPONDER_PICKER = "jeopardy.responder"
    const val RESPONDER_ANSWERING = "jeopardy.responderAnswering"
    const val CORRECT = "jeopardy.correct"
    const val INCORRECT = "jeopardy.incorrect"
    const val REVEAL = "jeopardy.reveal"
    const val CANCEL_CLUE = "jeopardy.cancelClue"
    const val BANNER = "jeopardy.banner"
    const val WAGERER = "jeopardy.wagerer"
    const val WAGER = "jeopardy.wager"
    const val WAGER_SUBMIT = "jeopardy.wagerSubmit"
    const val WAGER_MAX = "jeopardy.wagerMax"
    const val PLAYERS_ADD = "jeopardy.players.add"
    const val SCORE_LOG = "jeopardy.scoreLog"
    const val SETTINGS_SAVE = "jeopardy.settings.save"
    fun tile(col: Int, row: Int) = "jeopardy.tile.$col.$row"
    fun player(id: String) = "jeopardy.player.$id"
    fun playerScore(id: String) = "jeopardy.player.$id.score"
}

/** `$1,200` or `-$400`, like the web's money(). */
fun money(v: Int): String {
    val s = "$" + NumberFormat.getIntegerInstance(Locale.US).format(kotlin.math.abs(v))
    return if (v < 0) "-$s" else s
}

/** The show's round names; a single-round board is just "Jeopardy!". */
fun roundName(index: Int, count: Int): String = when {
    count == 1 -> "Jeopardy!"
    else -> listOf("Jeopardy!", "Double Jeopardy!", "Triple Jeopardy!").getOrElse(index) { "Round ${index + 1}" }
}

/** Board images are stored with URLs relative to the game server. */
fun resolveImageUrl(serverBase: String, url: String): String =
    if (url.startsWith("http://") || url.startsWith("https://")) url
    else serverBase.trimEnd('/') + (if (url.startsWith("/")) "" else "/") + url

/** Parses the server's `#rrggbb` player colours. */
fun parseColor(hex: String?): Color? = try {
    hex?.takeIf { it.startsWith("#") && it.length == 7 }?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (_: IllegalArgumentException) {
    null
}

/** Current time, ticking every frame while [active]. Paused in tests, like other infinite animations. */
@Composable
fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        if (active) while (true) withInfiniteAnimationFrameMillis { now = System.currentTimeMillis() }
    }
    return now
}

/** The big full-width action button used for each phase's main step. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    success: Boolean = false,
    tag: String = JeopardyTags.PRIMARY,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (success) AppColors.Success else AppColors.PrimaryGreen,
            contentColor = if (success) Color.White else AppColors.OnPrimary,
        ),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

enum class Tone { INFO, SUCCESS, WARNING }

/**
 * A phase-state banner. Given a player with a box button colour, it fills
 * with that colour so the host can call out "Yellow!" without reading the name.
 */
@Composable
fun StatusBanner(text: String, tone: Tone = Tone.INFO, player: Player? = null, large: Boolean = false) {
    val playerColor = parseColor(player?.color)
    val (bg, fg, edge) = when {
        playerColor != null -> Triple(playerColor, parseColor(player?.ink) ?: Color.White, Color.Black.copy(alpha = 0.5f))
        tone == Tone.SUCCESS -> Triple(AppColors.Success.copy(alpha = 0.12f), AppColors.Success, AppColors.Success.copy(alpha = 0.4f))
        tone == Tone.WARNING -> Triple(AppColors.Warning.copy(alpha = 0.12f), AppColors.Warning, AppColors.Warning.copy(alpha = 0.4f))
        else -> Triple(AppColors.SurfaceVariant, AppColors.OnSurface, AppColors.Outline)
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (playerColor != null) 2.dp else 1.dp, edge, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(JeopardyTags.BANNER),
        color = fg,
        fontSize = if (large) 17.sp else 14.sp,
        fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/** A countdown bar with its label, like the web host's timer. */
@Composable
fun TimerBar(countdown: Countdown) {
    if (!countdown.active) return
    val seconds = String.format(Locale.US, "%.1f", countdown.remainingMs / 1000.0)
    val label = when (countdown.kind) {
        "read" -> stringResource(R.string.timer_read, seconds)
        "buzz" -> stringResource(R.string.timer_buzz, seconds)
        else -> stringResource(R.string.timer_answer, seconds)
    }
    Column(Modifier.fillMaxWidth().testTag(JeopardyTags.TIMER), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { countdown.fraction },
            modifier = Modifier.fillMaxWidth().heightIn(min = 10.dp),
            color = if (countdown.kind == "answer") AppColors.Warning else AppColors.PrimaryGreen,
            trackColor = AppColors.SurfaceVariant,
            drawStopIndicator = {},
        )
        Text(label, color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
    }
}

/** A clue or response: text, or an image from the server with its captions. */
@Composable
fun SideContent(side: Side?, serverBase: String, modifier: Modifier = Modifier) {
    if (side == null) return
    if (!side.isImage) {
        Text(side.text, modifier = modifier, color = AppColors.OnSurface, fontSize = 20.sp, lineHeight = 26.sp)
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (side.topCaption.isNotBlank()) Text(side.topCaption, color = AppColors.OnSurface, fontSize = 18.sp, textAlign = TextAlign.Center)
        if (side.imageUrl.isNotBlank()) {
            AsyncImage(
                model = resolveImageUrl(serverBase, side.imageUrl),
                contentDescription = side.topCaption.ifBlank { side.bottomCaption }.ifBlank { stringResource(R.string.clue_image) },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
            )
        } else {
            Text(stringResource(R.string.clue_image), color = AppColors.OnSurfaceMedium)
        }
        if (side.bottomCaption.isNotBlank()) Text(side.bottomCaption, color = AppColors.OnSurface, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

/** A dot in the player's box button colour. */
@Composable
fun ButtonSwatch(color: Color) {
    Box(Modifier.size(12.dp).background(color, CircleShape).border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape))
}

/** True where Modifier.blur actually blurs (API 31+); older phones hide the response instead. */
val canBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = AppColors.OnSurfaceMedium) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}
