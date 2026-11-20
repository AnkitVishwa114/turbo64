package com.appkraft.turbo64

import com.appkraft.turbo64.library.PickOutcome
import com.appkraft.turbo64.library.PickResult
import com.appkraft.turbo64.library.PickRules
import com.appkraft.turbo64.library.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PickRulesTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() } + ByteArray(4)

    @Test fun sniffsByContentWhateverTheName() {
        assertEquals(SourceKind.ZIP, PickRules.sniff(bytes(0x50, 0x4B, 0x03, 0x04), ""))
        assertEquals(SourceKind.SEVEN_Z, PickRules.sniff(bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C), "game"))
        assertEquals(SourceKind.GAME, PickRules.sniff(bytes(0x80, 0x37, 0x12, 0x40), "document:1234"))
        assertEquals(SourceKind.GAME, PickRules.sniff(bytes(0x37, 0x80, 0x40, 0x12), "Mario.zip"))
        assertEquals(SourceKind.GAME, PickRules.sniff(bytes(0x40, 0x12, 0x37, 0x80), ""))
    }

    @Test fun unknownContentIsNotAGameEvenWithAGameName() {
        assertEquals(SourceKind.UNKNOWN, PickRules.sniff(bytes(0x25, 0x50, 0x44, 0x46), "photo.jpg"))
        // A start that is readable but not N64 does not trust the name.
        assertEquals(SourceKind.UNKNOWN, PickRules.sniff(bytes(0x00, 0x00, 0x00, 0x00), "notes.txt"))
    }

    @Test fun emptyStartFallsBackToTheName() {
        assertEquals(SourceKind.ZIP, PickRules.sniff(ByteArray(0), "Zelda.ZIP"))
        assertEquals(SourceKind.GAME, PickRules.sniff(null, "Zelda.n64"))
        assertEquals(SourceKind.UNKNOWN, PickRules.sniff(ByteArray(2), "readme"))
    }

    @Test fun importTitleUsesTheFileNameThenTheGameName() {
        assertEquals("The Legend of Zelda - Ocarina of Time", PickRules.importTitle("Legend of Zelda, The - Ocarina of Time (USA).z64", "ZELDA"))
        assertEquals("SUPER MARIO 64", PickRules.importTitle("", "SUPER MARIO 64"))
        assertEquals("SUPER MARIO 64", PickRules.importTitle("   ", "SUPER MARIO 64"))
        assertEquals("N64 game", PickRules.importTitle("", ""))
    }

    @Test fun singleOutcomeMessages() {
        assertEquals("Mario added", PickRules.summary(listOf(PickOutcome(PickResult.ADDED, "Mario"))))
        assertEquals("Mario is already in your library", PickRules.summary(listOf(PickOutcome(PickResult.ALREADY_THERE, "Mario"))))
        assertEquals("That file is not an N64 game", PickRules.summary(listOf(PickOutcome(PickResult.NOT_A_GAME))))
        assertEquals("No space", PickRules.summary(listOf(PickOutcome(PickResult.FAILED, reason = "No space"))))
    }

    @Test fun mixedOutcomesAreCounted() {
        val outcomes = listOf(
            PickOutcome(PickResult.ADDED, "A"), PickOutcome(PickResult.ADDED, "B"),
            PickOutcome(PickResult.ALREADY_THERE, "C"), PickOutcome(PickResult.NOT_A_GAME),
            PickOutcome(PickResult.FAILED, reason = "x"), PickOutcome(PickResult.FAILED, reason = "y")
        )
        assertEquals("2 games added. 1 already in your library. 1 not N64 game. 2 could not be added", PickRules.summary(outcomes))
    }
}
