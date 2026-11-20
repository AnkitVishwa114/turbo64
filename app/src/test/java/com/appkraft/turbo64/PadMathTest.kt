package com.appkraft.turbo64

import android.view.KeyEvent
import com.appkraft.turbo64.player.C_DOWN
import com.appkraft.turbo64.player.C_LEFT
import com.appkraft.turbo64.player.C_RIGHT
import com.appkraft.turbo64.player.C_UP
import com.appkraft.turbo64.player.cVector
import com.appkraft.turbo64.player.dpadDirections
import com.appkraft.turbo64.player.stickOutput
import com.appkraft.turbo64.ui.gameInitials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadMathTest {
    @Test fun stickRestsInsideTheDeadZone() {
        assertEquals(0f to 0f, stickOutput(0f, 0f, 1f))
        assertEquals(0f to 0f, stickOutput(0.05f, -0.05f, 1f))
    }

    @Test fun lightPushAlreadyMovesAndFullPushReachesTheEdge() {
        val (light, _) = stickOutput(0.15f, 0f, 1f)
        assertTrue("a light push must pass the engine's own dead zone", light > 0.18f)
        assertEquals(1f, stickOutput(1f, 0f, 1f).first, 0.001f)
        assertEquals(-1f, stickOutput(0f, -1f, 1f).second, 0.001f)
    }

    @Test fun diagonalsReachFurtherThanACircleButNeverPastTheRange() {
        val (x, y) = stickOutput(0.7071f, 0.7071f, 1f)
        assertTrue(x > 0.75f && y > 0.75f)
        val (hx, hy) = stickOutput(0.7071f, 0.7071f, 1.3f)
        assertTrue(hx <= 1f && hy <= 1f)
    }

    @Test fun sensitivityScalesTheOutput() {
        assertTrue(stickOutput(0.5f, 0f, 0.7f).first < stickOutput(0.5f, 0f, 1.3f).first)
    }

    @Test fun dpadCardinalsAndDiagonals() {
        assertEquals(emptySet<Int>(), dpadDirections(0f, 0f))
        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_RIGHT), dpadDirections(0.4f, 0f))
        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_UP), dpadDirections(0f, -0.4f))
        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT), dpadDirections(-0.3f, 0.3f))
    }

    @Test fun cButtonsBecomeTheRightStick() {
        assertEquals(0f to 0f, cVector(emptySet()))
        assertEquals(0f to -1f, cVector(setOf(C_UP)))
        assertEquals(1f to 1f, cVector(setOf(C_RIGHT, C_DOWN)))
        assertEquals("opposite buttons cancel out", 0f to 0f, cVector(setOf(C_LEFT, C_RIGHT)))
    }

    @Test fun initialsForGamesWithoutAPicture() {
        assertEquals("CQ", gameInitials("Castle Quest 64"))
        assertEquals("MA", gameInitials("Mario"))
        assertEquals("64", gameInitials(""))
    }
}
