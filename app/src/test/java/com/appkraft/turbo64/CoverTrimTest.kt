package com.appkraft.turbo64

import com.appkraft.turbo64.library.CoverTrim
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CoverTrimTest {
    private val black = 0xFF000000.toInt()
    private val green = 0xFF2CC98A.toInt()

    /** A [width] × [height] screen, black, with a lit picture from [left],[top] to [right],[bottom]. */
    private fun screen(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        if (x in left until right && y in top until bottom) green else black
    }

    @Test fun cutsSideBarsOfALandscapeScreen() {
        // 4:3 picture centred in a 16:9-ish view.
        assertArrayEquals(intArrayOf(80, 0, 400, 240), CoverTrim.bounds(screen(480, 240, 80, 0, 400, 240), 480, 240))
    }

    @Test fun cutsTopAndBottomBarsOfAPortraitScreen() {
        assertArrayEquals(intArrayOf(0, 100, 240, 280), CoverTrim.bounds(screen(240, 400, 0, 100, 240, 280), 240, 400))
    }

    @Test fun keepsAPictureWithoutBars() {
        assertArrayEquals(intArrayOf(0, 0, 320, 240), CoverTrim.bounds(screen(320, 240, 0, 0, 320, 240), 320, 240))
    }

    @Test fun keepsADarkSceneWhole() {
        // Almost all black with a small light spot: not bars, so nothing is cut.
        assertArrayEquals(intArrayOf(0, 0, 480, 240), CoverTrim.bounds(screen(480, 240, 220, 100, 260, 140), 480, 240))
        assertArrayEquals(intArrayOf(0, 0, 480, 240), CoverTrim.bounds(IntArray(480 * 240) { black }, 480, 240))
    }

    @Test fun ignoresAOnePixelDarkEdge() {
        assertArrayEquals(intArrayOf(0, 0, 320, 240), CoverTrim.bounds(screen(320, 240, 1, 0, 319, 240), 320, 240))
    }
}
