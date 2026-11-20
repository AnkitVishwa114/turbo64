package com.appkraft.turbo64

import com.appkraft.turbo64.player.Cheat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheatTest {
    @Test fun acceptsPastedCodesWithAnySpacing() {
        assertEquals("8133B176 0064+8033B21E 0008", Cheat.normalise("8133b176 0064\n8033B21E  0008"))
        assertEquals("8133B176 0064", Cheat.normalise("8133B1760064"))
        assertEquals("8133B176 0064+8033B21E 0008", Cheat.normalise("8133B176 0064+8033B21E 0008"))
    }

    @Test fun rejectsBrokenCodes() {
        assertNull(Cheat.normalise(""))
        assertNull(Cheat.normalise("1234 5678"))
        assertNull(Cheat.normalise("hello"))
        assertNull(Cheat.normalise("8133B176 00640"))
    }
}
