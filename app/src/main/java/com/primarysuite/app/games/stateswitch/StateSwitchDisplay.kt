package com.primarysuite.app.games.stateswitch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.OfflineBadge
import com.primarysuite.app.ui.theme.SocafeFontFamily

object StateSwitchDisplayTags {
    const val ROOT = "stateSwitchDisplay"
    const val WORD = "stateSwitchDisplay.word"
}

/** One side of a pair: the word and its colours, copied from the web's slides. */
data class StateSlide(val word: String, val background: Color, val text: Color)

object StateSlides {
    /** The web's empty display surface, shown for Random and unknown games. */
    val BLANK = Color(0xFF304A6C)

    private val white = Color.White
    private val slow = StateSlide("Slow", Color(0xFF13AEF0), white)
    private val fast = StateSlide("Fast", Color(0xFFF1DA07), white)
    private val go = StateSlide("Go", Color(0xFF2BAE6C), white)
    private val stop = StateSlide("Stop", Color(0xFFA31621), white)
    private val loud = StateSlide("Loud", Color(0xFFA31621), Color(0xFFFCF7F8))
    private val soft = StateSlide("Soft", Color(0xFFCED3DC), Color(0xFF4E8098))
    private val left = StateSlide("Left", Color(0xFF98CE00), Color(0xFF001011))
    private val right = StateSlide("Right", Color(0xFF001011), Color(0xFF98CE00))
    private val legato = StateSlide("Legato", Color(0xFF507DBC), white)
    private val staccato = StateSlide("Staccato", Color(0xFF04080F), white)
    private val marching = StateSlide("Marching", Color(0xFFE7CFBC), Color(0xFF853A2B))
    private val clapping = StateSlide("Clapping", Color(0xFF72788D), Color(0xFFFAE0D2))
    private val standing = StateSlide("Standing", Color(0xFFFA198B), Color(0xFF0E0004))
    private val sitting = StateSlide("Sitting", Color(0xFF0E0004), Color(0xFFFA198B))
    private val singing = StateSlide("Singing", Color(0xFFA31621), Color(0xFFFFE4CA))
    private val humming = StateSlide("Humming", Color(0xFF00859E), Color(0xFFFFE4CA))

    /** Index 0 and 1 follow the server's `current`, in the order of StateSwitchContainer.tsx. */
    private val pairs: Map<GameType, List<StateSlide>> = mapOf(
        GameType.FAST_SLOW to listOf(slow, fast),
        GameType.GO_STOP to listOf(go, stop),
        GameType.LOUD_SOFT to listOf(loud, soft),
        GameType.LEFT_RIGHT to listOf(left, right),
        GameType.STACCATO_LEGATO to listOf(legato, staccato),
        GameType.CLAPPING_MARCHING to listOf(marching, clapping),
        GameType.STANDING_SITTING to listOf(standing, sitting),
        GameType.SINGING_HUMMING to listOf(singing, humming),
    )

    /** The slide the projector shows, or null where it shows nothing (Random, unknown games). */
    fun of(game: String, current: Int): StateSlide? =
        GameType.of(game)?.let { pairs[it] }?.getOrNull(current)
}

/**
 * Full-screen display of the current state, like the projector: the side's
 * colour fills the screen with its word in the middle. Taps do nothing; the
 * caller handles Back. Hides the system bars while shown.
 */
@Composable
fun StateSwitchDisplay(ui: StateSwitchUiState) {
    HideSystemBars()

    val settings = ui.settings
    val slide = settings?.let { StateSlides.of(it.game, ui.state.current) }
    val background by animateColorAsState(slide?.background ?: StateSlides.BLANK, tween(200), label = "background")

    Box(
        Modifier.fillMaxSize().background(background).testTag(StateSwitchDisplayTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        when {
            settings == null -> Word(stringResource(R.string.state_switch_connecting), Color.White.copy(alpha = 0.7f), small = true)
            slide != null -> Word(slide.word, slide.text)
        }
        if (!ui.connected && settings != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) { OfflineBadge(stringResource(R.string.game_offline)) }
        }
    }
}

/** The word, as large as fits on one line in the middle of the screen. */
@Composable
private fun Word(text: String, color: Color, small: Boolean = false) {
    BasicText(
        text = text,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(if (small) 0.15f else 0.6f)
            .wrapContentHeight()
            .testTag(StateSwitchDisplayTags.WORD),
        style = TextStyle(
            color = color,
            fontFamily = SocafeFontFamily,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(6f, 6f), 16f),
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 400.sp, stepSize = 4.sp),
    )
}

/** Immersive mode while on screen. A swipe from the edge shows the bars for a moment. */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
