package com.appkraft.turbo64

import com.appkraft.turbo64.ui.ShortTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortTimeTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test fun playTimeIsShort() {
        assertEquals("< 1 min", ShortTime.played(30_000))
        assertEquals("12 min", ShortTime.played(12 * minute))
        assertEquals("2 h", ShortTime.played(120 * minute))
        assertEquals("2 h 5 min", ShortTime.played(125 * minute))
    }

    @Test fun agoIsShort() {
        assertEquals("Just now", ShortTime.ago(now - 20_000, now))
        assertEquals("5 min ago", ShortTime.ago(now - 5 * minute, now))
        assertEquals("3 h ago", ShortTime.ago(now - 180 * minute, now))
        assertEquals("Yesterday", ShortTime.ago(now - 30 * 60 * minute, now))
        assertEquals("4 days ago", ShortTime.ago(now - 4 * 24 * 60 * minute, now))
        assertEquals("Just now", ShortTime.ago(now + minute, now)) // clock moved back
    }

    @Test fun everyTextFitsATile() {
        val samples = listOf(0L, 5, 59, 60, 61, 600, 6000).map { ShortTime.played(it * minute) } +
            listOf(0L, 5, 59, 60, 600, 1500, 3000, 9000, 90000).map { ShortTime.ago(now - it * minute, now) }
        samples.forEach { assertTrue("$it is too long", it.length <= 11) }
    }
}
