package com.appkraft.turbo64.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.appkraft.turbo64.R

/** The four brand colours, in the order they sit on the cube's top: back, right, front, left. */
internal val BrandRed = Color(0xFFF04A5A)
internal val BrandBlue = Color(0xFF3D7BFF)
internal val BrandYellow = Color(0xFFF8C43A)
internal val BrandGreen = Color(0xFF2CC98A)
internal val BrandQuads = listOf(BrandRed, BrandBlue, BrandYellow, BrandGreen)

private val Plate = Color(0xFFE9EDF4)
private val FaceLeft = Color(0xFF1D2533)
private val FaceRight = Color(0xFF121823)
private val Overshoot = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)

// The cube on a 120 × 120 grid, the same geometry as the launcher icon.
private val quadPoints = listOf(
    floatArrayOf(60f, 20f, 77.5f, 30f, 60f, 40f, 42.5f, 30f),
    floatArrayOf(77.5f, 30f, 95f, 40f, 77.5f, 50f, 60f, 40f),
    floatArrayOf(60f, 40f, 77.5f, 50f, 60f, 60f, 42.5f, 50f),
    floatArrayOf(42.5f, 30f, 60f, 40f, 42.5f, 50f, 25f, 40f)
)

private fun polygon(points: FloatArray) = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    close()
}

/**
 * The Turbo64 cube, drawn (not a picture) so each part can move: the plate, the cube body, the
 * four coloured tiles on top and the "64". Every value is 0..1; at 1 it matches the launcher icon.
 */
@Composable
fun CubeMark(
    size: Dp, modifier: Modifier = Modifier,
    plate: Float = 1f, body: Float = 1f, number: Float = 1f,
    tile: (Int) -> Float = { 1f }, tileGlow: (Int) -> Float = { 1f }
) {
    val context = LocalContext.current
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.outfit) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            // Outfit is a variable font whose default weight is thin; ask for bold.
            fontVariationSettings = "'wght' 700"
            textAlign = Paint.Align.CENTER
            textSize = 25f
        }
    }
    Canvas(modifier.size(size)) {
        val k = this.size.width / 120f
        scale(plate.coerceIn(0f, 1.2f)) {
            drawRoundRect(Plate, cornerRadius = CornerRadius(27f * k))
        }
        scale(k, k, pivot = Offset.Zero) {
            if (body > 0f) translate(top = (1f - body) * 14f) {
                drawPath(polygon(floatArrayOf(25f, 40f, 60f, 60f, 60f, 100f, 25f, 80f)), FaceLeft, alpha = body.coerceIn(0f, 1f))
                drawPath(polygon(floatArrayOf(60f, 60f, 95f, 40f, 95f, 80f, 60f, 100f)), FaceRight, alpha = body.coerceIn(0f, 1f))
                // Before its tile lands, the top is one dark face, so the cube is never open.
                drawPath(polygon(floatArrayOf(60f, 20f, 95f, 40f, 60f, 60f, 25f, 40f)), Color(0xFF2A3446), alpha = body.coerceIn(0f, 1f))
            }
            quadPoints.forEachIndexed { index, points ->
                val grow = tile(index)
                if (grow <= 0f) return@forEachIndexed
                val cx = (points[0] + points[4]) / 2f
                val cy = (points[1] + points[5]) / 2f
                scale(grow.coerceIn(0f, 1.15f), pivot = Offset(cx, cy)) {
                    drawPath(polygon(points), BrandQuads[index], alpha = tileGlow(index).coerceIn(0f, 1f))
                }
            }
            if (number > 0f) drawNumber(paint, number.coerceIn(0f, 1f))
        }
    }
}

/** "64" on the left face, slanted to follow it. */
private fun DrawScope.drawNumber(paint: Paint, alpha: Float) = drawIntoCanvas { canvas ->
    val native = canvas.nativeCanvas
    native.save()
    native.skew(0f, 0.571f)
    paint.color = android.graphics.Color.argb((alpha * 255).toInt(), 0xF5, 0xF7, 0xFA)
    native.drawText("64", 42.5f, 54.5f, paint)
    native.restore()
}

/** Splash build-up: the plate lands, the cube rises, the four tiles drop in one by one, then "64" appears. */
@Composable
fun CubeBuild(size: Dp, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1700, easing = LinearEasing)) }
    val p = progress.value
    fun stage(from: Float, to: Float, easing: CubicBezierEasing = Overshoot) = easing.transform(((p - from) / (to - from)).coerceIn(0f, 1f))
    CubeMark(
        size, modifier,
        plate = stage(0f, 0.28f),
        body = stage(0.16f, 0.46f, CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        number = stage(0.82f, 1f, CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        tile = { index -> stage(0.40f + index * 0.1f, 0.62f + index * 0.1f) }
    )
}

/** Loading: the finished cube with its tiles lighting up in turn. Used wherever the app waits. */
@Composable
fun BrandSpinner(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "cube")
    val turn by loop.animateFloat(0f, 4f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "turn")
    CubeMark(size, modifier, tileGlow = { index ->
        // Each tile is brightest when the light passes it and dims as it moves on.
        val distance = ((index - turn) % 4f + 4f) % 4f
        0.4f + 0.6f * (1f - (minOf(distance, 4f - distance) / 2f)).coerceIn(0f, 1f)
    })
}

/** Four dots in the brand colours that hop in turn: the small loader under the splash wordmark. */
@Composable
fun BrandDots(modifier: Modifier = Modifier, dot: Dp = 7.dp) {
    val loop = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dot)) {
        BrandQuads.forEachIndexed { index, color ->
            val lift by loop.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(520, delayMillis = index * 110, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)), RepeatMode.Reverse),
                label = "dot$index"
            )
            Canvas(Modifier.size(dot).graphicsLayer { translationY = -lift * dot.toPx() * 0.9f; alpha = 0.55f + 0.45f * lift }) {
                drawCircle(color)
            }
        }
    }
}
