package com.example.timeapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.MarkerMoney
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * The spot illustrations (Phase 20B, from the "T!ME — icon and illustrations" canvas) for the
 * welcome tour, empty states and help. Drawn in code on a 160×120 grid from the theme's own
 * tokens, so they follow every colourway, dark mode and pure black — a bitmap couldn't.
 * Decorative: hidden from TalkBack (the text beside them says what they show).
 */
enum class Illustration { PLAN, FOCUS, MONEY, HABITS, IDEAS, LINKS, PRIVATE, WELCOME }

private class IlloPalette(
    val soft: Color, val main: Color, val light: Color, val card: Color,
    val line: Color, val ok: Color, val amber: Color, val ink: Color,
)

@Composable
fun IllustrationImage(kind: Illustration, modifier: Modifier = Modifier, width: Dp = 200.dp) {
    val c = TimeThemeTokens.colors
    val p = IlloPalette(
        soft = c.accS, main = c.acc, light = c.accC, card = c.card, line = c.edge2,
        ok = TimeThemeTokens.status.okInk, amber = MarkerMoney, ink = c.accInk,
    )
    Canvas(modifier.width(width).aspectRatio(160f / 120f).clearAndSetSemantics { }) {
        scale(size.width / 160f, pivot = Offset.Zero) {
            when (kind) {
                Illustration.PLAN -> plan(p)
                Illustration.FOCUS -> focus(p)
                Illustration.MONEY -> money(p)
                Illustration.HABITS -> habits(p)
                Illustration.IDEAS -> ideas(p)
                Illustration.LINKS -> links(p)
                Illustration.PRIVATE -> privacy(p)
                Illustration.WELCOME -> welcome(p)
            }
        }
    }
}

// ——— primitives on the 160×120 grid ———

private fun DrawScope.blob(p: IlloPalette, cy: Float = 62f) = drawCircle(p.soft, 50f, Offset(80f, cy))

private fun DrawScope.box(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.boxLine(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float, width: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r), style = Stroke(width))

private fun DrawScope.ring(color: Color, cx: Float, cy: Float, r: Float, width: Float) =
    drawCircle(color, r, Offset(cx, cy), style = Stroke(width))

private fun DrawScope.polyline(color: Color, width: Float, vararg pts: Float) {
    val path = Path().apply {
        moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.segment(color: Color, width: Float, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)

// ——— the eight pictures ———

private fun DrawScope.plan(p: IlloPalette) {
    blob(p)
    box(p.card, 42f, 24f, 76f, 74f, 10f)
    boxLine(p.line, 42f, 24f, 76f, 74f, 10f, 2f)
    // header: rounded top corners only
    val header = Path().apply {
        moveTo(42f, 34f)
        arcTo(Rect(42f, 24f, 62f, 44f), 180f, 90f, false)
        lineTo(108f, 24f)
        arcTo(Rect(98f, 24f, 118f, 44f), 270f, 90f, false)
        lineTo(118f, 40f); lineTo(42f, 40f); close()
    }
    drawPath(header, p.main)
    drawCircle(p.ok, 6f, Offset(56f, 54f))
    polyline(p.card, 2f, 53f, 54f, 55.2f, 56.2f, 59.2f, 51.8f)
    box(p.line, 68f, 51f, 38f, 6f, 3f)
    ring(p.main, 56f, 70f, 5f, 2f); box(p.line, 68f, 67f, 30f, 6f, 3f)
    ring(p.main, 56f, 86f, 5f, 2f); box(p.line, 68f, 83f, 34f, 6f, 3f)
}

private fun DrawScope.focus(p: IlloPalette) {
    blob(p, 64f)
    box(p.main, 73f, 16f, 14f, 9f, 3f)
    drawCircle(p.card, 34f, Offset(80f, 64f))
    ring(p.light, 80f, 64f, 28f, 6f)
    drawArc(p.main, -90f, 240f, false, Offset(52f, 36f), Size(56f, 56f), style = Stroke(6f, cap = StrokeCap.Round))
    segment(p.ink, 4f, 80f, 64f, 92f, 52f)
    drawCircle(p.ink, 4f, Offset(80f, 64f))
}

private fun DrawScope.money(p: IlloPalette) {
    blob(p, 64f)
    box(p.card, 52f, 20f, 44f, 48f, 6f)
    boxLine(p.line, 52f, 20f, 44f, 48f, 6f, 2f)
    box(p.line, 60f, 30f, 28f, 4f, 2f)
    box(p.line, 60f, 39f, 20f, 4f, 2f)
    box(p.main, 34f, 50f, 92f, 52f, 12f)
    box(p.light, 98f, 64f, 34f, 22f, 8f)
    drawCircle(p.main, 4f, Offset(110f, 75f))
    drawCircle(p.amber, 14f, Offset(122f, 36f))
    ring(p.card, 122f, 36f, 8f, 2.5f)
}

private fun DrawScope.habits(p: IlloPalette) {
    blob(p)
    drawCircle(p.ok, 18f, Offset(80f, 40f))
    polyline(p.card, 4f, 71f, 40f, 77f, 46f, 88f, 34f)
    for (i in 0 until 5) box(p.ok, 26f + i * 16f, 72f, 12f, 12f, 3f)
    box(p.main, 106f, 72f, 12f, 12f, 3f)
    boxLine(p.light, 123f, 73f, 10f, 10f, 2.5f, 2f)
}

private fun DrawScope.ideas(p: IlloPalette) {
    blob(p)
    segment(p.amber, 4f, 80f, 18f, 80f, 24f)
    segment(p.amber, 4f, 52f, 26f, 56f, 31f)
    segment(p.amber, 4f, 108f, 26f, 104f, 31f)
    segment(p.amber, 4f, 44f, 50f, 50f, 50f)
    segment(p.amber, 4f, 116f, 50f, 110f, 50f)
    drawCircle(p.amber, 24f, Offset(80f, 54f))
    drawCircle(p.card.copy(alpha = 0.5f), 6f, Offset(72f, 46f))
    box(p.light, 70f, 74f, 20f, 8f, 2f)
    box(p.main, 72f, 84f, 16f, 8f, 3f)
}

private fun DrawScope.links(p: IlloPalette) {
    blob(p)
    rotate(-45f, pivot = Offset(80f, 62f)) {
        boxLine(p.main, 46f, 50f, 40f, 24f, 12f, 8f)
        boxLine(p.amber, 74f, 50f, 40f, 24f, 12f, 8f)
    }
}

private fun DrawScope.privacy(p: IlloPalette) {
    blob(p)
    box(p.card, 54f, 14f, 52f, 94f, 10f)
    boxLine(p.line, 54f, 14f, 52f, 94f, 10f, 2f)
    box(p.line, 72f, 20f, 16f, 3f, 1.5f)
    val shield = Path().apply {
        moveTo(80f, 38f); lineTo(100f, 45f); lineTo(100f, 60f)
        cubicTo(100f, 73f, 91f, 81f, 80f, 85f)
        cubicTo(69f, 81f, 60f, 73f, 60f, 60f)
        lineTo(60f, 45f); close()
    }
    drawPath(shield, p.main)
    drawCircle(p.card, 5f, Offset(80f, 58f))
    box(p.card, 78f, 60f, 4f, 10f, 2f)
}

private fun DrawScope.welcome(p: IlloPalette) {
    blob(p)
    segment(p.amber, 3.5f, 60f, 20f, 60f, 25f)
    segment(p.amber, 3.5f, 60f, 67f, 60f, 72f)
    segment(p.amber, 3.5f, 34f, 46f, 39f, 46f)
    segment(p.amber, 3.5f, 81f, 46f, 86f, 46f)
    segment(p.amber, 3.5f, 42f, 28f, 45.5f, 31.5f)
    segment(p.amber, 3.5f, 74.5f, 60.5f, 78f, 64f)
    segment(p.amber, 3.5f, 42f, 64f, 45.5f, 60.5f)
    segment(p.amber, 3.5f, 74.5f, 31.5f, 78f, 28f)
    drawCircle(p.amber, 14f, Offset(60f, 46f))
    box(p.card, 62f, 56f, 62f, 40f, 10f)
    boxLine(p.line, 62f, 56f, 62f, 40f, 10f, 2f)
    drawCircle(p.ok, 8f, Offset(78f, 76f))
    polyline(p.card, 2.5f, 74f, 76f, 77f, 79f, 82f, 73f)
    box(p.line, 92f, 70f, 24f, 5f, 2.5f)
    box(p.line, 92f, 79f, 16f, 5f, 2.5f)
}
