package com.appkraft.turbo64.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Snappy = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Launch screen: the cube builds itself (plate, body, four tiles, "64"), the name rises in
 * under it and four small dots hop while the app gets ready.
 */
@Composable
fun BrandSplash(modifier: Modifier = Modifier) {
    val name = remember { Animatable(0f) }
    val dots = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { kotlinx.coroutines.delay(820); name.animateTo(1f, tween(520, easing = Snappy)) }
        launch { kotlinx.coroutines.delay(1350); dots.animateTo(1f, tween(350)) }
    }
    Box(modifier.fillMaxSize().background(Background)) {
        Column(Modifier.align(Alignment.Center).offset(y = (-24).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CubeBuild(104.dp)
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.graphicsLayer { alpha = name.value; translationY = (1f - name.value) * 14.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Turbo", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont, letterSpacing = (-0.5).sp)
                    Text("64", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = AppFont, letterSpacing = (-0.5).sp)
                }
            }
        }
        BrandDots(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 52.dp).graphicsLayer { alpha = dots.value })
    }
}

/** Filled part of a progress bar with a light sweep running across it, like a charging meter. */
@Composable
internal fun ShineFill(fraction: Float, color: Color = Accent) {
    val shine = rememberInfiniteTransition(label = "shine")
    val x by shine.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
    Box(
        Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color)
            .drawWithCache {
                val band = size.width.coerceAtLeast(40f) * 0.45f
                onDrawWithContent {
                    drawContent()
                    val start = size.width * x - band
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), start, start + band * 2))
                }
            }
    )
}

private val risen = HashSet<Any>()

/**
 * Cards rise and fade in the first time they appear, a little staggered by [index]. Items seen
 * before (scrolling back, returning to a tab) show at once, so nothing replays and feels slow.
 */
@Composable
fun Modifier.riseIn(key: Any, index: Int): Modifier {
    val first = remember(key) { risen.add(key) }
    val shown = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (shown.value < 1f) {
            kotlinx.coroutines.delay((index % 8) * 45L)
            shown.animateTo(1f, tween(380, easing = Snappy))
        }
    }
    return this.graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 16.dp.toPx()
    }
}
