package com.primarysuite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primarysuite.app.ui.theme.AppColors

enum class StatusTone { OK, BUSY, BAD, NEUTRAL }

private fun StatusTone.color(): Color = when (this) {
    StatusTone.OK -> AppColors.StatusConnected
    StatusTone.BUSY -> AppColors.StatusConnecting
    StatusTone.BAD -> AppColors.StatusDisconnected
    StatusTone.NEUTRAL -> AppColors.OnSurfaceDim
}

/** The rounded, outlined card that holds status rows. */
@Composable
fun StatusCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceVariant, RoundedCornerShape(16.dp))
            .border(1.dp, AppColors.Outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** "Label ......... ● Value", with a spinner instead of the dot while busy. */
@Composable
fun StatusRow(label: String, value: String, tone: StatusTone, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = AppColors.OnSurface)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AppColors.OnSurfaceMedium)
            }
        }
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            if (tone == StatusTone.BUSY) {
                CircularProgressIndicator(Modifier.size(12.dp), color = tone.color(), strokeWidth = 2.dp)
            } else {
                Box(Modifier.size(10.dp).background(tone.color(), CircleShape))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tone.color(), fontWeight = FontWeight.Medium)
    }
}

/** Small all-caps section label, as in the XML theme's SectionHeader style. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(top = 8.dp),
        color = AppColors.OnSurfaceDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}
