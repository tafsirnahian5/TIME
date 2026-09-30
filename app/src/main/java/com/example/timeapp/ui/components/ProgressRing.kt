package com.example.timeapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A progress ring, as the refined-screens canvas draws them (habit rate, savings goals,
 * timers). [fraction] animates when it changes, so a set-aside visibly closes the ring.
 * [content] sits centred inside.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    stroke: Dp = 6.dp,
    content: @Composable () -> Unit = {},
) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2f
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            if (shown > 0f) drawArc(color, -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A dashed circular outline — the canvas's "rest day" mark. */
fun Modifier.dashedCircle(color: Color, width: Dp = 2.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawCircle(
        color, radius = size.minDimension / 2f - w / 2f,
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}
