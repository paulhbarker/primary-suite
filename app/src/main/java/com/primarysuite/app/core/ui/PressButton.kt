package com.primarysuite.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.ui.theme.AppColors

/**
 * A big button that stands in for the physical game button, so it fires the
 * moment a finger lands on it, like an arcade button, not on release as a
 * normal button does. It looks pressed until the finger lifts. Accessibility
 * services (TalkBack) still activate it with a normal click.
 */
@Composable
fun PressButton(
    text: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 96.dp,
    fontSize: TextUnit = 26.sp,
    color: Color = AppColors.SecondaryBlue,
    contentColor: Color = AppColors.OnPrimary,
) {
    var pressed by remember { mutableStateOf(false) }
    val press by rememberUpdatedState(onPress)
    val isEnabled by rememberUpdatedState(enabled)
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val background = when {
        !enabled -> AppColors.SurfaceVariant
        pressed -> lerp(color, Color.Black, 0.25f)
        else -> color
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .scale(if (pressed && enabled) 0.98f else 1f)
            .background(background, shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!isEnabled) return@awaitEachGesture
                    pressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    press()
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .semantics {
                role = Role.Button
                if (enabled) onClick { press(); true } else disabled()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) contentColor else AppColors.OnSurfaceDim,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
