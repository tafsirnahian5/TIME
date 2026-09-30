package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Celebrations — motion for moments that matter (the refined-screens canvas, "Celebrations"):
 * an animated check for finishing a task or checking in a habit, and confetti plus a banner
 * for real milestones only (a savings goal reached, a 7/30/100-day habit streak). Never for
 * every tap, and never when the system's animations are switched off.
 */

/** True when the user has turned animations off system-wide (Settings › Accessibility). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val system = remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
    // The app's own switch (Appearance › Reduce celebrations) as well as the system's.
    return system || com.example.timeapp.ui.theme.LocalReduceCelebrations.current
}

/** The check-and-burst total, so a caller can wait for it before the row disappears. */
const val CHECK_ANIMATION_MS = 600L

/**
 * A completion circle: outline when open; on becoming [checked] the fill springs in with a
 * slight overshoot, the tick draws itself, and eight sparks burst and fade. Drawn at [size]
 * inside the full 48dp target (rule 6). Unchecking just returns to the outline.
 */
@Composable
fun AnimatedCheckCircle(
    checked: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val colors = TimeThemeTokens.colors
    val outline = TimeThemeTokens.status.controlOutline
    val onFill = MaterialTheme.colorScheme.onPrimary
    val reduced = rememberReducedMotion()
    val fill = remember { Animatable(if (checked) 1f else 0f) }
    val tick = remember { Animatable(if (checked) 1f else 0f) }
    val burst = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    // Only a change *seen* by this composition animates; one that was already checked
    // when it appeared (scrolling back to a done row) simply shows as done.
    var last by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked == last) return@LaunchedEffect
        last = checked
        // Felt even with celebrations reduced — that switch is about motion, not touch.
        if (checked) com.example.timeapp.data.Haptics.perform(com.example.timeapp.data.HapticEvent.CONFIRM)
        if (!checked || reduced) {
            fill.snapTo(if (checked) 1f else 0f); tick.snapTo(if (checked) 1f else 0f); burst.snapTo(0f); scale.snapTo(1f)
            return@LaunchedEffect
        }
        launch { scale.snapTo(0.88f); scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        launch { fill.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
        launch { delay(120); tick.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
        launch { delay(160); burst.snapTo(0f); burst.animateTo(1f, tween(440, easing = LinearEasing)); burst.snapTo(0f) }
    }
    Box(
        modifier = modifier
            .size(Dimens.touchMin)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription; role = Role.Checkbox },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size).scale(scale.value)) {
            val r = this.size.minDimension / 2f
            val c = center
            // Sparks, outside the circle, only while bursting.
            val b = burst.value
            if (b > 0f && b < 1f) {
                val alpha = 1f - b
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0 - 90.0)
                    val start = r * (1.15f + b * 0.55f)
                    val end = start + r * 0.35f * (1f - b)
                    drawLine(
                        colors.acc.copy(alpha = alpha),
                        Offset(c.x + (cos(a) * start).toFloat(), c.y + (sin(a) * start).toFloat()),
                        Offset(c.x + (cos(a) * end).toFloat(), c.y + (sin(a) * end).toFloat()),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
            // Outline fades as the fill grows.
            if (fill.value < 1f) drawCircle(outline, r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            if (fill.value > 0f) drawCircle(colors.acc, r * fill.value, c)
            if (tick.value > 0f) {
                val path = Path().apply {
                    moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
                    lineTo(c.x - r * 0.1f, c.y + r * 0.34f)
                    lineTo(c.x + r * 0.46f, c.y - r * 0.3f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * tick.value, partial, true)
                drawPath(partial, onFill, style = Stroke(r * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** A milestone worth confetti. */
sealed class Celebration(val message: String) {
    class GoalReached(name: String) : Celebration(L("Goal reached — %1\$s", name))
    /** [length] like "30-day" or "7-week". */
    class Streak(habit: String, length: String) : Celebration(L("%1\$s streak on %2\$s", length, habit))
}

/** Streak lengths that celebrate. */
val StreakMilestones = setOf(7, 30, 100, 365)

/**
 * App-wide channel for milestones: a ViewModel emits, the root [CelebrationHost] shows them.
 * A process-lifetime object because the moment belongs to the app, not one screen — a goal
 * can be reached from a dialog that closes as the confetti starts.
 */
object CelebrationBus {
    private val events = MutableSharedFlow<Celebration>(extraBufferCapacity = 4)
    val flow: SharedFlow<Celebration> = events.asSharedFlow()
    fun emit(celebration: Celebration) { events.tryEmit(celebration) }
}

/**
 * Confetti over whatever is on screen, then a banner naming the milestone for ~3 s. Place it
 * last in the root layout; it never takes touches except the banner's own tap-to-dismiss.
 * With animations off: the banner alone.
 */
@Composable
fun CelebrationHost(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var current by remember { mutableStateOf<Celebration?>(null) }
    var confettiKey by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        CelebrationBus.flow.collect { c ->
            current = c
            com.example.timeapp.data.Haptics.perform(com.example.timeapp.data.HapticEvent.MILESTONE)
            if (!reduced) confettiKey++
            delay(3_200)
            if (current === c) current = null
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (confettiKey > 0) Confetti(key = confettiKey)
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val c = current
            Row(
                modifier = Modifier
                    .padding(Dimens.s16)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.inverseSurface, Shapes.lg)
                    .clickable { current = null }
                    .padding(Dimens.s14),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (c is Celebration.GoalReached) Icons.Rounded.Savings else Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = TimeThemeTokens.status.warn,
                )
                Text(
                    c?.message ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(start = Dimens.s12),
                )
            }
        }
    }
}

private class Piece(val x: Float, val drift: Float, val delay: Float, val spin: Float, val color: Int, val round: Boolean, val size: Float)

/** ~48 pieces falling for 1.4 s from the top edge; one-shot per [key]. */
@Composable
private fun Confetti(key: Int) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val palette = listOf(colors.acc, status.okInk, status.warnInk, colors.accDim, colors.acc)
    val pieces = remember(key) {
        val rnd = Random(key)
        List(48) {
            Piece(
                x = rnd.nextFloat(), drift = (rnd.nextFloat() - 0.5f) * 0.3f, delay = rnd.nextFloat() * 0.25f,
                spin = (rnd.nextFloat() - 0.5f) * 900f, color = rnd.nextInt(palette.size), round = rnd.nextInt(4) == 0,
                size = 6f + rnd.nextFloat() * 5f,
            )
        }
    }
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(1400, easing = LinearEasing)) }
    val p = progress.value
    if (p >= 1f) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        pieces.forEach { piece ->
            val t = ((p - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val y = -20f + size.height * (t * t * 0.9f + t * 0.25f)
            val x = size.width * (piece.x + piece.drift * t)
            val alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
            val color = palette[piece.color].copy(alpha = alpha)
            val s = piece.size.dp.toPx()
            rotate(piece.spin * t, Offset(x, y)) {
                if (piece.round) drawCircle(color, s / 2f, Offset(x, y))
                else drawRect(color, Offset(x - s / 2f, y - s), Size(s * 0.6f, s * 1.4f))
            }
        }
    }
}

/** Keeps a finished row on screen while its check plays, then calls [onDone]. */
suspend fun afterCheckAnimation(reduced: Boolean, onDone: () -> Unit) {
    if (!reduced) delay(CHECK_ANIMATION_MS)
    onDone()
}
