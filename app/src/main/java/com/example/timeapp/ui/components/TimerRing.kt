package com.example.timeapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.TimeThemeTokens

/** `ring` track + `acc` sweep, drawn on a Canvas. Used by F1 (stopwatch) and F2 (Pomodoro). */
@Composable
fun TimerRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 220.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val colors = TimeThemeTokens.colors
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx())
            drawArc(
                color = colors.ring,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
                size = Size(this.size.width, this.size.height),
            )
            drawArc(
                color = colors.acc,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
                size = Size(this.size.width, this.size.height),
            )
        }
        Text(label, style = MaterialTheme.typography.displaySmall)
    }
}
