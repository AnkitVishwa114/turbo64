package com.appkraft.turbo64

import com.appkraft.turbo64.library.CoverTrim
import com.appkraft.turbo64.library.GameNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameNamesTest {
    @Test fun officialNameWinsOverAnyFileName() {
        assertEquals("Super Mario 64", GameNames.choose("Super Mario 64", "SUPER MARIO 64", "N642FSuper20Mario2064.z64"))
    }

    @Test fun headerNameIsTidied() {
        assertEquals("Super Mario 64", GameNames.choose(null, "SUPER MARIO 64", "x.z64"))
        assertEquals("The Legend of Zelda", GameNames.headerTitle("THE LEGEND OF ZELDA"))
        assertEquals("WCW vs. NWO Revenge", GameNames.headerTitle("WCW vs. NWO Revenge"))
        assertEquals("Mario Kart 64", GameNames.headerTitle("MARIO KART 64"))
        assertEquals("Banjo-Kazooie", GameNames.headerTitle("BANJO-KAZOOIE"))
        assertEquals("Turok II", GameNames.headerTitle("TUROK II"))
    }

    @Test fun brokenFileNamesAreDecoded() {
        assertEquals("Super Mario 64", GameNames.decodeFileName("N642FSuper20Mario2064"))
        assertEquals("Cruisn World", GameNames.decodeFileName("N642FCruisn20World"))
        assertEquals("Super Mario 64.z64", GameNames.decodeFileName("Super%20Mario%2064.z64"))
        // Normal names are left alone.
        assertEquals("Mario Kart 64 (USA).z64", GameNames.decodeFileName("Mario Kart 64 (USA).z64"))
        assertEquals("F-Zero X.z64", GameNames.decodeFileName("F-Zero X.z64"))
    }

    @Test fun fileNameIsTheLastResort() {
        assertEquals("Super Mario 64", GameNames.choose(null, "", "N642FSuper20Mario2064.z64"))
        assertEquals("N64 game", GameNames.choose(null, "", ""))
    }

    @Test fun listParsingAndIds() {
        val table = GameNames.parse(listOf("# comment", "635A2BFF\tSuper Mario 64", "bad line", "0A5D8F83\t"))
        assertEquals(mapOf("635A2BFF" to "Super Mario 64"), table)
        assertEquals(0x635A2BFFL, GameNames.crcFromId("NSME-635A2BFF"))
        assertNull(GameNames.crcFromId("F-1234abcd5678"))
        assertEquals("635A2BFF", GameNames.crcKey(0x635A2BFFL))
    }

    @Test fun blackFramesAreNotPictures() {
        assertTrue(CoverTrim.mostlyDark(IntArray(4800) { 0xFF000000.toInt() }))
        assertFalse(CoverTrim.mostlyDark(IntArray(4800) { if (it % 3 == 0) 0xFF3D7BFF.toInt() else 0xFF000000.toInt() }))
    }
}
