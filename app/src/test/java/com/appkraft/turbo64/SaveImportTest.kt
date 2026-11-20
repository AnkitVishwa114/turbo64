package com.appkraft.turbo64

import com.appkraft.turbo64.library.SaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveImportTest {
    @Test fun wholeSaveReplacesEverything() {
        val srm = ByteArray(SaveStore.SAVE_SIZE) { 7 }
        assertTrue(SaveStore.mergeForeignSave(null, "game.srm", srm).all { it == 7.toByte() })
    }

    @Test(expected = IllegalArgumentException::class) fun wrongSizedWholeSaveIsRefused() {
        SaveStore.mergeForeignSave(null, "game.srm", ByteArray(100))
    }

    @Test fun eachSaveTypeLandsInItsOwnPart() {
        val eep = SaveStore.mergeForeignSave(null, "a.eep", ByteArray(512) { 1 })
        assertEquals(1, eep[0].toInt()); assertEquals(1, eep[511].toInt()); assertEquals(0xFF.toByte(), eep[512])
        val sra = SaveStore.mergeForeignSave(eep, "a.SRA", ByteArray(SaveStore.SRAM_SIZE) { 2 })
        val sramStart = SaveStore.EEPROM_SIZE + SaveStore.MEMPAK_SIZE
        assertEquals(2, sra[sramStart].toInt())
        assertEquals("the earlier EEPROM data is kept", 1, sra[0].toInt())
        val fla = SaveStore.mergeForeignSave(sra, "a.fla", ByteArray(SaveStore.FLASH_SIZE) { 3 })
        assertEquals(3, fla[sramStart + SaveStore.SRAM_SIZE].toInt()); assertEquals(3, fla.last().toInt())
        val mpk = SaveStore.mergeForeignSave(fla, "a.mpk", ByteArray(0x8000) { 4 })
        assertEquals(4, mpk[SaveStore.EEPROM_SIZE].toInt())
        assertEquals(SaveStore.SAVE_SIZE, mpk.size)
    }

    @Test(expected = IllegalArgumentException::class) fun unknownFilesAreRefused() {
        SaveStore.mergeForeignSave(null, "notes.txt", ByteArray(10))
    }

    @Test(expected = IllegalArgumentException::class) fun oversizedPartsAreRefused() {
        SaveStore.mergeForeignSave(null, "a.eep", ByteArray(SaveStore.EEPROM_SIZE + 1))
    }
}
