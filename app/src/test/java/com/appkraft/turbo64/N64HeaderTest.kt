package com.appkraft.turbo64

import com.appkraft.turbo64.library.ByteOrder64
import com.appkraft.turbo64.library.N64Header
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class N64HeaderTest {
    /** A big-endian header: magic, checksums, name, code "NSME". */
    private fun header(): ByteArray {
        val bytes = ByteArray(N64Header.HEADER_SIZE)
        byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40).copyInto(bytes, 0)
        byteArrayOf(0x63, 0x5A, 0x2B, 0xFF.toByte()).copyInto(bytes, 0x10)
        byteArrayOf(0x8B.toByte(), 0x02, 0x23, 0x26).copyInto(bytes, 0x14)
        "CASTLE QUEST 64     ".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x20)
        "NSME".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x3B)
        return bytes
    }

    private fun swapPairs(bytes: ByteArray) = bytes.copyOf().also { for (i in it.indices step 2) { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t } }
    private fun reverseWords(bytes: ByteArray) = bytes.copyOf().also { for (i in it.indices step 4) it.reverse(i, i + 4) }

    @Test fun detectsAllThreeByteOrders() {
        assertEquals(ByteOrder64.BIG, N64Header.order(header()))
        assertEquals(ByteOrder64.BYTE_SWAPPED, N64Header.order(swapPairs(header())))
        assertEquals(ByteOrder64.LITTLE, N64Header.order(reverseWords(header())))
        assertNull(N64Header.order(ByteArray(64)))
        assertNull(N64Header.order(ByteArray(2)))
    }

    @Test fun convertsEveryOrderBackToBigEndian() {
        assertArrayEquals(header(), N64Header.toBigEndian(swapPairs(header()), ByteOrder64.BYTE_SWAPPED))
        assertArrayEquals(header(), N64Header.toBigEndian(reverseWords(header()), ByteOrder64.LITTLE))
        assertArrayEquals(header(), N64Header.toBigEndian(header(), ByteOrder64.BIG))
    }

    @Test fun readsTheSameInfoFromEveryOrder() {
        for (bytes in listOf(header(), swapPairs(header()), reverseWords(header()))) {
            val info = N64Header.parseHeader(bytes)
            assertNotNull(info)
            assertEquals("NSME", info!!.gameCode)
            assertEquals("CASTLE QUEST 64", info.internalName)
            assertEquals("USA", info.region)
            assertEquals(0x635A2BFFL, info.crc1)
            assertEquals("NSME-635A2BFF", info.stableId)
        }
    }

    @Test fun rejectsFilesThatAreNotGames() {
        assertNull(N64Header.parseHeader(ByteArray(N64Header.HEADER_SIZE)))
        assertNull(N64Header.parseHeader(ByteArray(10)))
        assertNull(N64Header.parseHeader("PK\u0003\u0004".toByteArray() + ByteArray(60)))
    }

    @Test fun reportsDamagedAndCutOffFiles() {
        assertNotNull(N64Header.problem(null, 0))
        assertNotNull(N64Header.problem(ByteArray(10), 10))
        assertNotNull(N64Header.problem(ByteArray(N64Header.HEADER_SIZE), 8L * 1024 * 1024))
        assertNotNull(N64Header.problem(header(), 500_000))
        assertNull(N64Header.problem(header(), 8L * 1024 * 1024))
        assertNull(N64Header.problem(swapPairs(header()), N64Header.MIN_SIZE))
    }

    @Test fun regionLetters() {
        assertEquals("Japan", N64Header.region('J'))
        assertEquals("Europe", N64Header.region('P'))
        assertEquals("", N64Header.region('?'))
    }

    @Test fun titleFromFileNameDropsTagsAndFixesArticles() {
        assertEquals("Super Game", N64Header.titleFromFileName("Super Game (USA) [!].z64"))
        assertEquals("My Game", N64Header.titleFromFileName("My_Game.zip"))
        assertEquals("The Legend of Heroes - Time Quest", N64Header.titleFromFileName("Legend of Heroes, The - Time Quest (Europe).n64"))
    }
}
