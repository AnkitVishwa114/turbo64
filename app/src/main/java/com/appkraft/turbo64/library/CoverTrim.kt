package com.appkraft.turbo64.library

import android.graphics.Bitmap

/**
 * The game screen is drawn inside a wider (or taller) view, so a capture has black bars around
 * the picture. Cards and save slots should show only the picture, edge to edge, so the bars are
 * cut off. Pure pixel logic in [bounds], so unit tests cover it.
 */
object CoverTrim {
    /** A pixel counts as bar when every channel is at or below this. */
    private const val DARK = 18

    /**
     * Picture area as left, top, right (exclusive), bottom (exclusive). Only plain dark rows and
     * columns at the edges are cut, and nothing is cut when the rest would be too small or oddly
     * shaped (a dark scene, not bars).
     */
    fun bounds(pixels: IntArray, width: Int, height: Int): IntArray {
        val all = intArrayOf(0, 0, width, height)
        if (width < 8 || height < 8 || pixels.size < width * height) return all
        fun dark(color: Int): Boolean =
            (color shr 16 and 0xFF) <= DARK && (color shr 8 and 0xFF) <= DARK && (color and 0xFF) <= DARK
        // A column or row is bar when (almost) all of its sampled pixels are dark.
        fun columnIsBar(x: Int): Boolean {
            var light = 0
            for (y in 0 until height step 2) if (!dark(pixels[y * width + x])) light++
            return light <= height / 100
        }
        fun rowIsBar(y: Int): Boolean {
            var light = 0
            for (x in 0 until width step 2) if (!dark(pixels[y * width + x])) light++
            return light <= width / 100
        }
        var left = 0
        while (left < width / 2 && columnIsBar(left)) left++
        var right = width
        while (right > left + 1 && right > width / 2 && columnIsBar(right - 1)) right--
        var top = 0
        while (top < height / 2 && rowIsBar(top)) top++
        var bottom = height
        while (bottom > top + 1 && bottom > height / 2 && rowIsBar(bottom - 1)) bottom--

        val w = right - left
        val h = bottom - top
        if (w < width / 3 || h < height / 3) return all // mostly dark: a dark scene, keep it whole
        val ratio = w.toFloat() / h
        if (ratio < 0.9f || ratio > 2.4f) return all
        // Bars are a few pixels at least; a one-pixel dark edge is just the picture.
        return intArrayOf(
            if (left >= 3) left else 0, if (top >= 3) top else 0,
            if (width - right >= 3) right else width, if (height - bottom >= 3) bottom else height
        )
    }

    /**
     * True when almost the whole picture is black: a loading or fade screen. Such a frame is
     * not kept as the library picture, so a card never turns into a black box.
     */
    fun mostlyDark(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return true
        var dark = 0
        var seen = 0
        for (i in pixels.indices step 7) {
            val color = pixels[i]
            seen++
            if ((color shr 16 and 0xFF) <= 28 && (color shr 8 and 0xFF) <= 28 && (color and 0xFF) <= 28) dark++
        }
        return dark >= seen * 0.94
    }

    fun mostlyDark(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return mostlyDark(pixels)
    }

    /** The picture without its bars; the same bitmap when there is nothing to cut. */
    fun trim(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val (left, top, right, bottom) = bounds(pixels, width, height)
        if (left == 0 && top == 0 && right == width && bottom == height) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}
