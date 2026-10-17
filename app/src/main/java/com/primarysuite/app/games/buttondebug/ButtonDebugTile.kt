package com.primarysuite.app.games.buttondebug

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.R
import com.primarysuite.app.ui.theme.AppColors

/** Test tags for one tile. [id] is the button id. */
object ButtonDebugTileTags {
    fun tile(id: Int) = "buttondebug.tile.$id"
    fun count(id: Int) = "buttondebug.tile.$id.count"
    fun color(id: Int) = "buttondebug.tile.$id.color"
    fun colorOption(id: Int, color: ButtonColor) = "buttondebug.tile.$id.color.${color.key}"
    fun line(id: Int) = "buttondebug.tile.$id.line"
    fun debounced(id: Int) = "buttondebug.tile.$id.debounced"
    fun led(id: Int) = "buttondebug.tile.$id.led"
    fun badge(id: Int, name: String) = "buttondebug.tile.$id.badge.$name"
    fun issues(id: Int) = "buttondebug.tile.$id.issues"
    fun stat(id: Int, key: String) = "buttondebug.tile.$id.stat.$key"
    fun wave(id: Int, key: String) = "buttondebug.tile.$id.wave.$key"
}

/** A tile's pulse: 0 just after a press arrives, 1 when finished. Not run for replayed history. */
@Composable
internal fun rememberPulse(key: Int, active: Boolean, millis: Int): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { Animatable(1f) }
    var seen by remember { mutableIntStateOf(key) }
    LaunchedEffect(key) {
        if (key != seen) {
            seen = key
            if (active) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(millis, easing = FastOutSlowInEasing))
            }
        }
    }
    return progress
}

internal fun Long.asColor() = Color(this)

internal val Mono = FontFamily.Monospace

/** One button of the box: lamp, line state, problems, stats and the last bursts. */
@Composable
fun ButtonTile(
    button: DebugButton,
    stats: Stats,
    status: DebugStatus?,
    color: ButtonColor,
    onColor: (Int, ButtonColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val i = button.index
    val id = button.id
    val stuck = status?.isStuck(i) == true
    val enabled = status?.isEnabled(i) ?: true
    val led = status?.isLed(i) == true
    val issues = problems(stats, stuck)
    val bg = color.bg.asColor()
    val fg = color.fg.asColor()
    val edge = color.edge.asColor()

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, if (issues.isNotEmpty()) AppColors.Error.copy(alpha = 0.5f) else AppColors.Outline), RoundedCornerShape(12.dp))
            .testTag(ButtonDebugTileTags.tile(id)),
    ) {
        // Header in the button's colour, with the colour picker.
        Row(
            Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ColorPicker(id, color, fg, onColor)
            Text(
                stringResource(R.string.buttondebug_tile_ids, id, button.switchPin, button.ledPin),
                color = fg.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(edge))

        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Lamp(id, stats, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Indicator(
                    stringResource(R.string.buttondebug_line), on = status?.isLow(i) == true,
                    onText = stringResource(R.string.buttondebug_line_low), offText = stringResource(R.string.buttondebug_line_high),
                    tag = ButtonDebugTileTags.line(id),
                )
                Indicator(
                    stringResource(R.string.buttondebug_debounced), on = status?.isDown(i) == true,
                    onText = stringResource(R.string.buttondebug_held), offText = stringResource(R.string.buttondebug_up),
                    bad = stuck, tag = ButtonDebugTileTags.debounced(id),
                )
                Indicator(
                    stringResource(R.string.buttondebug_led), on = led,
                    onText = stringResource(R.string.buttondebug_on), offText = stringResource(R.string.buttondebug_off),
                    icon = true, tag = ButtonDebugTileTags.led(id),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!enabled) Badge(stringResource(R.string.buttondebug_badge_disabled), AppColors.Error.copy(alpha = 0.15f), AppColors.Error, ButtonDebugTileTags.badge(id, "disabled"))
                    if (stuck) Badge(stringResource(R.string.buttondebug_badge_stuck), AppColors.Error, Color.White, ButtonDebugTileTags.badge(id, "stuck"))
                    if (issues.isEmpty() && stats.presses > 0) {
                        Badge(stringResource(R.string.buttondebug_badge_healthy), AppColors.Success.copy(alpha = 0.15f), AppColors.Success, ButtonDebugTileTags.badge(id, "healthy"))
                    }
                }
            }
        }

        if (issues.isNotEmpty()) {
            Column(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Error.copy(alpha = 0.1f))
                    .padding(8.dp)
                    .testTag(ButtonDebugTileTags.issues(id)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                issues.forEach { Text("•  $it", color = AppColors.Error, fontSize = 12.sp) }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Outline))
        StatList(id, stats, status)
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Outline))

        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Wave(stringResource(R.string.buttondebug_wave_press), stats.pressBurst, edge, ButtonDebugTileTags.wave(id, "press"), Modifier.weight(1f))
                Wave(stringResource(R.string.buttondebug_wave_release), stats.releaseBurst, edge, ButtonDebugTileTags.wave(id, "release"), Modifier.weight(1f))
            }
            if (stats.noiseBurst != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Wave(stringResource(R.string.buttondebug_wave_noise), stats.noiseBurst, AppColors.Warning, ButtonDebugTileTags.wave(id, "noise"), Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorPicker(id: Int, color: ButtonColor, fg: Color, onColor: (Int, ButtonColor) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable { open = true }.padding(vertical = 8.dp).testTag(ButtonDebugTileTags.color(id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(color.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = stringResource(R.string.buttondebug_color_cd), tint = fg)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ButtonColor.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label) },
                    leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(c.bg.asColor()).border(1.dp, c.edge.asColor(), CircleShape)) },
                    onClick = {
                        open = false
                        onColor(id, c)
                    },
                    modifier = Modifier.testTag(ButtonDebugTileTags.colorOption(id, c)),
                )
            }
        }
    }
}

/** The big lamp: filled while the contact is closed, from the raw edges, with the press count inside. */
@Composable
private fun Lamp(id: Int, stats: Stats, color: ButtonColor) {
    val bg = color.bg.asColor()
    val edge = color.edge.asColor()
    val pulse = rememberPulse(stats.pressKey, stats.live, 450)
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val r = size.minDimension / 2
            if (stats.down) {
                drawCircle(bg.copy(alpha = 0.25f), radius = r)
                drawCircle(bg, radius = r - stroke)
            }
            drawCircle(edge, radius = r - stroke / 2, style = Stroke(stroke))
            val p = pulse.value
            if (p < 1f) {
                drawCircle(edge.copy(alpha = 1f - p), radius = (r - stroke / 2) * (1f + 0.4f * p), style = Stroke(stroke))
            }
        }
        Text(
            stats.presses.toString(),
            color = if (stats.down) color.fg.asColor() else AppColors.OnSurface,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            modifier = Modifier.testTag(ButtonDebugTileTags.count(id)),
        )
    }
}

@Composable
private fun Indicator(label: String, on: Boolean, onText: String, offText: String, tag: String, bad: Boolean = false, icon: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon) {
            Icon(
                Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(14.dp),
                tint = if (on) AppColors.Warning else AppColors.OnSurfaceDim,
            )
        } else {
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape).background(
                        when {
                            bad -> AppColors.Error
                            on -> AppColors.Success
                            else -> AppColors.OnSurfaceDim
                        },
                    ),
                )
            }
        }
        Text(label, color = AppColors.OnSurfaceMedium, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text(
            if (on) onText else offText,
            color = if (bad) AppColors.Error else AppColors.OnSurface,
            fontFamily = Mono,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color, tag: String) {
    Text(
        text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp).testTag(tag),
    )
}

@Composable
private fun StatList(id: Int, s: Stats, status: DebugStatus?) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Stat(id, "sent", R.string.buttondebug_stat_sent, "${s.presses} / ${s.delivered}", bad = s.presses > s.delivered + 1)
        Stat(id, "late", R.string.buttondebug_stat_late, "${s.latePresses} (${fmtUsOrDash(s.lateMaxUs)})")
        Stat(id, "lost", R.string.buttondebug_stat_lost, "${s.lostPresses} / ${s.lostReleases}", bad = s.lostPresses + s.lostReleases > 0)
        Stat(id, "noise", R.string.buttondebug_stat_noise, s.blips.toString(), bad = s.blips > 0)
        Stat(id, "short", R.string.buttondebug_stat_short, s.shortPresses.toString(), bad = s.shortPresses > 0)
        Stat(id, "dropped", R.string.buttondebug_stat_dropped, s.dropped.toString())
        Stat(id, "edges", R.string.buttondebug_stat_edges, "${s.edges} / ${s.kernelDropped}")
        Stat(id, "pressBounce", R.string.buttondebug_stat_press_bounce, fmtUsOrDash(s.maxPressBounceUs))
        Stat(
            id, "releaseBounce", R.string.buttondebug_stat_release_bounce, fmtUsOrDash(s.maxReleaseBounceUs),
            bad = status != null && s.maxReleaseBounceUs > status.quietMs * 500,
        )
        Stat(id, "hold", R.string.buttondebug_stat_hold, "${fmtMs(s.lastHoldMs, 0)} / ${fmtMs(s.minHoldMs, 0)}")
        Stat(
            id, "lag", R.string.buttondebug_stat_lag, "${fmtUs(avgUs(s.lagSumUs, s.lagN))} / ${fmtUsOrDash(s.lagMaxUs)}",
            bad = s.lagMaxUs > 20_000,
        )
        Stat(
            id, "hub", R.string.buttondebug_stat_hub, "${fmtUs(avgUs(s.hubSumUs, s.hubN))} / ${fmtUsOrDash(s.hubMaxUs)}",
            bad = s.hubMaxUs > 30_000,
        )
    }
}

@Composable
private fun Stat(id: Int, key: String, label: Int, value: String, bad: Boolean = false) =
    StatRow(stringResource(label), value, ButtonDebugTileTags.stat(id, key), bad)

/** A label on the left and a monospace value on the right. */
@Composable
internal fun StatRow(label: String, value: String, tag: String, bad: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = AppColors.OnSurfaceMedium, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = if (bad) AppColors.Error else AppColors.OnSurface,
            fontWeight = if (bad) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = Mono,
            fontSize = 12.sp,
            modifier = Modifier.testTag(tag),
        )
    }
}

/**
 * One burst of edges on a timeline. Edge directions from the kernel are unreliable
 * inside a bounce, so this shows when edges happened (tall ticks: claimed closing,
 * short: claimed opening), not a level trace.
 */
@Composable
private fun Wave(title: String, burst: Burst?, color: Color, tag: String, modifier: Modifier = Modifier) {
    Column(modifier.testTag(tag)) {
        if (burst == null) {
            Text(title, color = AppColors.OnSurfaceMedium, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
            Box(
                Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.buttondebug_wave_none), color = AppColors.OnSurfaceDim, fontSize = 10.sp) }
            return@Column
        }
        val span = burst.spanUs
        val window = waveWindowUs(burst)
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = AppColors.OnSurfaceMedium, fontSize = 11.sp, maxLines = 1)
            Text(
                "${burst.edges.size}× · ${fmtUs(span)}", color = AppColors.OnSurfaceMedium, fontFamily = Mono, fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.testTag("$tag.count"),
            )
        }
        val axis = AppColors.OnSurface.copy(alpha = 0.15f)
        Canvas(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.SurfaceVariant)) {
            val sx = size.width / WAVE_W.toFloat()
            val sy = size.height / WAVE_H.toFloat()
            val base = (WAVE_H - 4).toFloat() * sy
            drawLine(axis, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            burst.edges.forEach { e ->
                val x = waveX(e.tUs, window).toFloat() * sx
                val top = (if (e.claimed) 4.0 else WAVE_H / 2).toFloat() * sy
                drawLine(color, Offset(x, base), Offset(x, top), strokeWidth = 1.25.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", color = AppColors.OnSurfaceDim, fontFamily = Mono, fontSize = 10.sp)
            Text(fmtUs(window), color = AppColors.OnSurfaceDim, fontFamily = Mono, fontSize = 10.sp)
        }
    }
}
