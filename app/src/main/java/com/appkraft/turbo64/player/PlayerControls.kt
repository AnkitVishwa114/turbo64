package com.appkraft.turbo64.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appkraft.turbo64.ui.Haptics
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Look of the on-screen pad, read by every control. */
internal class PadStyle(val opacity: Float, val haptics: Boolean)

/** Brand colours of the pad: A blue, B green, C yellow, Z and Start neutral. */
internal object PadColors {
    val Fill = Color(0xFF222C33)
    val Edge = Color.White.copy(alpha = 0.22f)
    val Blue = Color(0xFF3D7BFF)
    val Green = Color(0xFF2CC98A)
    val Yellow = Color(0xFFF8C43A)
    val Red = Color(0xFFF04A5A)
    val Ink = Color(0xFF11181D)
}

/** One round button inside a [ButtonCluster]; x, y and radius are fractions of the cluster's width. */
internal data class PadButton(val id: Int, val label: String, val x: Float, val y: Float, val radius: Float, val color: Color, val icon: Int = 0)

/**
 * Several round buttons as one touch area. Every finger is tracked on its own, and a thumb
 * resting between two neighbours presses both, so A + B or two C buttons work with one thumb.
 */
@Composable
internal fun ButtonCluster(
    buttons: List<PadButton>, style: PadStyle, description: String, modifier: Modifier = Modifier,
    labelSize: TextUnit = 18.sp, onActive: (Set<Int>) -> Unit
) {
    var active by remember { mutableStateOf(emptySet<Int>()) }
    val report by rememberUpdatedState(onActive)
    val haptics by rememberUpdatedState(style.haptics)
    DisposableEffect(Unit) { onDispose { report(emptySet()) } }
    Box(modifier
        .graphicsLayer { alpha = if (active.isEmpty()) style.opacity else maxOf(style.opacity, 0.92f) }
        .semantics { contentDescription = description }
        .pointerInput(buttons) {
            val fingers = mutableMapOf<PointerId, Set<Int>>()
            fun hits(x: Float, y: Float): Set<Int> {
                val near = buttons.map { it to hypot(x - it.x * size.width, y - it.y * size.height) / (it.radius * size.width) }.sortedBy { it.second }
                val (first, nearest) = near[0]
                // The touch area is larger than the drawn button, so a slightly off thumb still presses.
                if (nearest > 1.75f) return emptySet()
                val second = near.getOrNull(1) ?: return setOf(first.id)
                return if (second.second < 1.5f && second.second - nearest < 0.5f) setOf(first.id, second.first.id) else setOf(first.id)
            }
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (change.pressed) fingers[change.id] = hits(change.position.x, change.position.y) else fingers.remove(change.id)
                    }
                    val all = fingers.values.flatten().toSet()
                    if (all != active) {
                        if (haptics && (all - active).isNotEmpty()) Haptics.gameTap()
                        active = all
                        report(all)
                    }
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            buttons.forEach { button ->
                val center = Offset(button.x * size.width, button.y * size.height)
                val radius = button.radius * size.width
                val on = button.id in active
                // A soft halo in the button's colour while it is held.
                if (on) drawCircle(button.color.copy(alpha = 0.22f), radius * 1.28f, center)
                drawCircle(if (on) button.color else PadColors.Fill, radius, center)
                drawCircle(button.color.copy(alpha = if (on) 1f else 0.85f), radius, center, style = Stroke(1.75.dp.toPx()))
                if (button.icon != 0) {
                    // A small arrow for the four C buttons.
                    val tip = radius * 0.42f
                    val (dx, dy) = when (button.icon) { 1 -> 0f to -1f; 2 -> 0f to 1f; 3 -> -1f to 0f; else -> 1f to 0f }
                    val path = Path().apply {
                        moveTo(center.x + dx * tip, center.y + dy * tip)
                        lineTo(center.x - dx * tip * 0.6f + dy * tip, center.y - dy * tip * 0.6f + dx * tip)
                        lineTo(center.x - dx * tip * 0.6f - dy * tip, center.y - dy * tip * 0.6f - dx * tip)
                        close()
                    }
                    drawPath(path, if (on) PadColors.Ink else button.color)
                }
            }
        }
        buttons.filter { it.icon == 0 }.forEach { button ->
            Text(button.label, color = if (button.id in active) PadColors.Ink else Color.White, fontSize = labelSize, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(BiasAlignment(button.x * 2 - 1, button.y * 2 - 1)))
        }
    }
}

/**
 * The analog stick. It floats: the base moves under the thumb when the touch starts, so the
 * thumb never has to find the centre. Output is -1..1 on both axes, y positive downwards, with
 * a small dead zone and an N64-style range (full tilt reaches 1 on the axes and the diagonals).
 */
@Composable
internal fun AnalogStick(style: PadStyle, sensitivity: Float, modifier: Modifier = Modifier, onMove: (Float, Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var base by remember { mutableStateOf<Offset?>(null) }
    val report by rememberUpdatedState(onMove)
    val gain by rememberUpdatedState(sensitivity)
    val haptics by rememberUpdatedState(style.haptics)
    DisposableEffect(Unit) { onDispose { report(0f, 0f) } }
    Box(modifier
        .graphicsLayer { alpha = if (base == null) style.opacity else maxOf(style.opacity, 0.92f) }
        .semantics { contentDescription = "Analog stick" }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val reach = size.width * 0.34f
                // Keep the floating base inside the control so the knob stays on screen.
                val origin = Offset(down.position.x.coerceIn(reach, size.width - reach), down.position.y.coerceIn(reach, size.height - reach))
                base = origin
                var atEdge = false
                fun update(x: Float, y: Float) {
                    var dx = (x - origin.x) / reach
                    var dy = (y - origin.y) / reach
                    val length = hypot(dx, dy)
                    if (length > 1f) { dx /= length; dy /= length }
                    knob = Offset(dx * reach, dy * reach)
                    val (outX, outY) = stickOutput(dx, dy, gain)
                    report(outX, outY)
                    val edge = length >= 1f
                    if (edge && !atEdge && haptics) Haptics.tick()
                    atEdge = edge
                }
                update(down.position.x, down.position.y)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer?.pressed != true) break
                        update(pointer.position.x, pointer.position.y)
                    }
                } finally {
                    base = null
                    knob = Offset.Zero
                    report(0f, 0f)
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val reach = size.width * 0.34f
            val center = base ?: Offset(size.width / 2f, size.height / 2f)
            if (base != null) drawCircle(Color.White.copy(alpha = 0.08f), reach * 1.5f, center)
            drawCircle(PadColors.Fill.copy(alpha = 0.75f), reach * 1.28f, center)
            drawCircle(PadColors.Edge, reach * 1.28f, center, style = Stroke(1.75.dp.toPx()))
            drawCircle(Color.White.copy(alpha = if (base == null) 0.82f else 1f), reach * 0.62f, center + knob)
            drawCircle(PadColors.Ink.copy(alpha = 0.18f), reach * 0.3f, center + knob)
        }
    }
}

/** Dead zone, sensitivity and the N64 stick's octagonal range, as pure maths so it can be tested. */
internal fun stickOutput(dx: Float, dy: Float, sensitivity: Float): Pair<Float, Float> {
    val length = hypot(dx, dy)
    val deadZone = 0.1f
    if (length < deadZone) return 0f to 0f
    // Past the dead zone the output starts above the engine's own dead zone, so a light push already walks.
    val scaled = (0.2f + (length - deadZone) / (1f - deadZone) * 0.8f) * sensitivity
    var x = dx / length * scaled
    var y = dy / length * scaled
    // On a real pad the gate lets diagonals reach further than a circle; lift them the same way.
    val diagonal = 2f * minOf(abs(dx), abs(dy)) / length.coerceAtLeast(0.0001f)
    val lift = 1f + 0.18f * (diagonal / 1.4142f).coerceIn(0f, 1f)
    x *= lift; y *= lift
    return x.coerceIn(-1f, 1f) to y.coerceIn(-1f, 1f)
}

/** A single button that stays down while held, or flips on each tap when [toggle] is set (Z in shooters). */
@Composable
internal fun HoldButton(
    label: String, style: PadStyle, modifier: Modifier = Modifier, shape: Shape = CircleShape, color: Color = Color.White,
    toggle: Boolean = false, icon: ImageVector? = null, onPress: (Boolean) -> Unit
) {
    var held by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (held) 0.92f else 1f, spring(dampingRatio = 0.6f, stiffness = 1600f), label = "press")
    val report by rememberUpdatedState(onPress)
    val haptics by rememberUpdatedState(style.haptics)
    val latch by rememberUpdatedState(toggle)
    DisposableEffect(Unit) { onDispose { report(false) } }
    Box(modifier
        .graphicsLayer { scaleX = press; scaleY = press; alpha = if (held) maxOf(style.opacity, 0.92f) else style.opacity }
        .clip(shape)
        .background(if (held) color else PadColors.Fill)
        .border(1.75.dp, if (held) color else color.copy(alpha = if (color == Color.White) 0.22f else 0.85f), shape)
        .semantics {
            contentDescription = "Game button $label"
            role = Role.Button
            onClick { report(true); report(false); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (haptics) Haptics.gameTap()
                if (latch) {
                    held = !held
                    report(held)
                    do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                } else {
                    held = true; report(true)
                    try { do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed }) }
                    finally { held = false; report(false) }
                }
            }
        }, contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (held) PadColors.Ink else Color.White, modifier = Modifier.size(15.dp))
        else Text(label, color = if (held) PadColors.Ink else Color.White, fontSize = if (label.length > 1) 10.5.sp else 17.sp,
            fontWeight = FontWeight.Bold, letterSpacing = if (label.length > 1) 0.8.sp else 0.sp)
    }
}

/** The four-way pad. Reports the pressed direction key codes; diagonals press two. */
@Composable
internal fun DPad(style: PadStyle, modifier: Modifier = Modifier, onActive: (Set<Int>) -> Unit) {
    var active by remember { mutableStateOf(emptySet<Int>()) }
    val report by rememberUpdatedState(onActive)
    val haptics by rememberUpdatedState(style.haptics)
    DisposableEffect(Unit) { onDispose { report(emptySet()) } }
    Box(modifier.graphicsLayer { alpha = if (active.isEmpty()) style.opacity else maxOf(style.opacity, 0.92f) }
        .semantics { contentDescription = "Direction pad" }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun update(x: Float, y: Float) {
                    val next = dpadDirections(x / size.width - 0.5f, y / size.height - 0.5f)
                    if (next != active) {
                        if (haptics && next.isNotEmpty()) Haptics.gameTap()
                        active = next
                        report(next)
                    }
                }
                update(down.position.x, down.position.y)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer?.pressed != true) break
                        update(pointer.position.x, pointer.position.y)
                    }
                } finally {
                    active = emptySet()
                    report(emptySet())
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val arm = size.minDimension / 3f
            val radius = CornerRadius(arm * 0.3f)
            drawRoundRect(PadColors.Fill, topLeft = Offset(arm, 0f), size = Size(arm, size.height), cornerRadius = radius)
            drawRoundRect(PadColors.Fill, topLeft = Offset(0f, arm), size = Size(size.width, arm), cornerRadius = radius)
            val stroke = Stroke(width = 1.75.dp.toPx())
            drawRoundRect(PadColors.Edge, topLeft = Offset(arm, 0f), size = Size(arm, size.height), cornerRadius = radius, style = stroke)
            drawRoundRect(PadColors.Edge, topLeft = Offset(0f, arm), size = Size(size.width, arm), cornerRadius = radius, style = stroke)
            drawRect(PadColors.Fill, topLeft = Offset(arm + 2.dp.toPx(), arm + 2.dp.toPx()), size = Size(arm - 4.dp.toPx(), arm - 4.dp.toPx()))
            val highlight = Color.White.copy(alpha = 0.9f)
            if (KeyEvent.KEYCODE_DPAD_UP in active) drawRoundRect(highlight, Offset(arm, 0f), Size(arm, arm), radius)
            if (KeyEvent.KEYCODE_DPAD_DOWN in active) drawRoundRect(highlight, Offset(arm, arm * 2), Size(arm, arm), radius)
            if (KeyEvent.KEYCODE_DPAD_LEFT in active) drawRoundRect(highlight, Offset(0f, arm), Size(arm, arm), radius)
            if (KeyEvent.KEYCODE_DPAD_RIGHT in active) drawRoundRect(highlight, Offset(arm * 2, arm), Size(arm, arm), radius)
            val c = size.width / 2f
            val tip = arm * 0.22f
            fun arrow(cx: Float, cy: Float, dx: Float, dy: Float, on: Boolean) {
                val path = Path().apply {
                    moveTo(cx + dx * tip, cy + dy * tip)
                    lineTo(cx - dx * tip * 0.6f + dy * tip, cy - dy * tip * 0.6f + dx * tip)
                    lineTo(cx - dx * tip * 0.6f - dy * tip, cy - dy * tip * 0.6f - dx * tip)
                    close()
                }
                drawPath(path, if (on) PadColors.Ink else Color.White.copy(alpha = 0.75f))
            }
            arrow(c, arm * 0.5f, 0f, -1f, KeyEvent.KEYCODE_DPAD_UP in active)
            arrow(c, arm * 2.5f, 0f, 1f, KeyEvent.KEYCODE_DPAD_DOWN in active)
            arrow(arm * 0.5f, c, -1f, 0f, KeyEvent.KEYCODE_DPAD_LEFT in active)
            arrow(arm * 2.5f, c, 1f, 0f, KeyEvent.KEYCODE_DPAD_RIGHT in active)
        }
    }
}

/** Directions for a touch at ([dx], [dy]) from the pad's centre (each -0.5..0.5). Cardinals get 60°, diagonals 30°. */
internal fun dpadDirections(dx: Float, dy: Float): Set<Int> {
    if (hypot(dx, dy) <= 0.08f) return emptySet()
    val angle = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360
    fun near(center: Double, half: Double) = abs((angle - center + 540) % 360 - 180) <= half
    return when {
        near(0.0, 30.0) -> setOf(KeyEvent.KEYCODE_DPAD_RIGHT)
        near(90.0, 30.0) -> setOf(KeyEvent.KEYCODE_DPAD_DOWN)
        near(180.0, 30.0) -> setOf(KeyEvent.KEYCODE_DPAD_LEFT)
        near(270.0, 30.0) -> setOf(KeyEvent.KEYCODE_DPAD_UP)
        near(45.0, 15.0) -> setOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN)
        near(135.0, 15.0) -> setOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        near(225.0, 15.0) -> setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP)
        else -> setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
    }
}

/** The stick position the engine reads for a set of pressed C buttons (they are its right stick). */
internal fun cVector(pressed: Set<Int>): Pair<Float, Float> {
    val x = (if (C_RIGHT in pressed) 1f else 0f) - (if (C_LEFT in pressed) 1f else 0f)
    val y = (if (C_DOWN in pressed) 1f else 0f) - (if (C_UP in pressed) 1f else 0f)
    return x to y
}

internal const val C_UP = 1
internal const val C_DOWN = 2
internal const val C_LEFT = 3
internal const val C_RIGHT = 4

/** A small round action over the game: menu, quick save, rotate and so on. */
@Composable
internal fun GlassIcon(icon: ImageVector, label: String, style: PadStyle, active: Boolean = false, badge: String? = null, size: Dp = 38.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).graphicsLayer { alpha = maxOf(style.opacity, 0.6f) }.clip(CircleShape)
            .background(if (active) PadColors.Yellow else Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0f else 0.16f), CircleShape)
            .clickable(role = Role.Button, onClickLabel = label) {
                if (style.haptics) Haptics.gameTap()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (badge != null) Text(badge, color = PadColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        else Icon(icon, contentDescription = label, tint = if (active) PadColors.Ink else Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
