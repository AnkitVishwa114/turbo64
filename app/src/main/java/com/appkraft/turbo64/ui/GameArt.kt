package com.appkraft.turbo64.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appkraft.turbo64.library.CoverTrim
import com.appkraft.turbo64.library.GameEntry
import com.appkraft.turbo64.library.GameStore
import com.appkraft.turbo64.library.SaveStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** A game's picture (its last screen, once it has been played) and its colour, cached per game and play. */
private data class Art(val cover: ImageBitmap?, val color: Color)

private val artCache = ConcurrentHashMap<String, Art>()

/** The cache key changes when the game was played again, so the card shows the newest screen. */
private fun artKey(game: GameEntry) = "${game.id}:${game.lastPlayed}"

/** Forget cached art (after a rescan or when saves were deleted). */
fun clearArtCache() = artCache.clear()

// Deeper shades of the four brand colours: solid plates that white letters read well on.
private val brandColors = listOf(Color(0xFFB8404C), Color(0xFF1F8F66), Color(0xFF2F5FC4), Color(0xFFB98A1E))

/** One of the four brand colours, always the same for the same title. */
internal fun gameColor(title: String): Color = brandColors[(title.lowercase().hashCode() and 0x7FFFFFFF) % brandColors.size]

/** Up to two letters for a game without a picture yet: "Castle Quest 64" → "CQ". */
internal fun gameInitials(title: String): String {
    val words = title.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() && !it.all(Char::isDigit) }
    val letters = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> title.trim().take(2)
    }
    return letters.uppercase().ifBlank { "64" }
}

// The cube's three faces on the launcher icon's 120 grid; the cube spans x 25..95, y 20..100.
private val cubeTop = floatArrayOf(60f, 20f, 95f, 40f, 60f, 60f, 25f, 40f)
private val cubeLeft = floatArrayOf(25f, 40f, 60f, 60f, 60f, 100f, 25f, 80f)
private val cubeRight = floatArrayOf(60f, 60f, 95f, 40f, 95f, 80f, 60f, 100f)

private fun cubePath(points: FloatArray, scale: Float, dx: Float, dy: Float) = Path().apply {
    moveTo(points[0] * scale + dx, points[1] * scale + dy)
    for (i in 2 until points.size step 2) lineTo(points[i] * scale + dx, points[i + 1] * scale + dy)
    close()
}

/** The Turbo64 cube in white, its faces lit from the top: the picture of a game not played yet. */
@Composable
private fun WhiteCube(modifier: Modifier) {
    Canvas(modifier) {
        // Fit the 70 × 80 cube inside the box, centred.
        val scale = minOf(size.width / 70f, size.height / 80f)
        val dx = (size.width - 70f * scale) / 2f - 25f * scale
        val dy = (size.height - 80f * scale) / 2f - 20f * scale
        drawPath(cubePath(cubeLeft, scale, dx, dy), Color.White.copy(alpha = 0.58f))
        drawPath(cubePath(cubeRight, scale, dx, dy), Color.White.copy(alpha = 0.34f))
        drawPath(cubePath(cubeTop, scale, dx, dy), Color.White.copy(alpha = 0.94f))
    }
}

@Composable
private fun rememberArt(game: GameEntry): Art? {
    val context = LocalContext.current.applicationContext
    val key = artKey(game)
    // Keyed state: when the same spot shows another game (the home hero), the old game's
    // picture must not stay. produceState kept its first value across keys, which did exactly that.
    val state = remember(key) { mutableStateOf(artCache[key]) }
    LaunchedEffect(key) {
        if (state.value == null) state.value = withContext(Dispatchers.IO) {
            artCache.getOrPut(key) {
                // Covers saved before bars were cut off are trimmed here, so every card is edge to edge.
                val cover = SaveStore(context).cover(game.id)?.let { file ->
                    runCatching {
                        // A black picture saved by an older version is shown as no picture.
                        BitmapFactory.decodeFile(file.absolutePath)?.let(CoverTrim::trim)?.takeUnless(CoverTrim::mostlyDark)?.asImageBitmap()
                    }.getOrNull()
                }
                Art(cover, gameColor(game.title))
            }
        }
    }
    return state.value
}

/** The game's colour, for accents around it (sheet header, hero card). */
@Composable
fun rememberGameColor(game: GameEntry, @Suppress("UNUSED_PARAMETER") store: GameStore): Color = gameColor(game.title)

/**
 * Game artwork: the last screen of the game once it has been played, otherwise a plate in the
 * game's colour with the Turbo64 cube in white. No box art is downloaded or bundled.
 */
@Composable
fun GameArt(game: GameEntry, @Suppress("UNUSED_PARAMETER") store: GameStore, modifier: Modifier = Modifier, corner: Dp = 14.dp, iconScale: Float = 0.62f) {
    val wasCached = remember(game.id) { artCache.containsKey(artKey(game)) }
    val art = rememberArt(game)
    val shape = RoundedCornerShape(corner)
    if (art == null) {
        LoadingPlaceholder(modifier.clip(shape))
        return
    }
    val shown = remember(game.id) { Animatable(if (wasCached) 1f else 0f) }
    LaunchedEffect(game.id) { shown.animateTo(1f, tween(220)) }
    // A picture fills the whole shape with nothing around it; a game without one gets a solid plate.
    BoxWithConstraints(modifier.clip(shape).background(if (art.cover != null) ArtworkBackground else art.color), contentAlignment = Alignment.Center) {
        if (art.cover != null) {
            Image(art.cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value })
        } else {
            WhiteCube(Modifier.size(minOf(maxWidth, maxHeight) * iconScale * 0.62f).graphicsLayer { alpha = shown.value })
        }
    }
}
