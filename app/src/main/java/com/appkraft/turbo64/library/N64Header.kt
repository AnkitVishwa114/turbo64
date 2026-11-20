package com.appkraft.turbo64.library

/** How the bytes of an N64 game file are ordered. The emulator reads all three; the app only needs to tell them apart. */
enum class ByteOrder64 { BIG, BYTE_SWAPPED, LITTLE }

/**
 * What an N64 game file says about itself. The header gives the game code and two checksums
 * (together a stable id for saves across phones and file names) and the region.
 */
data class N64Info(
    val gameCode: String,
    val crc1: Long,
    val crc2: Long,
    val internalName: String,
    val region: String,
    val order: ByteOrder64
) {
    /** Stable id for this game's saves: same game → same id on every phone and file name. */
    val stableId: String get() = "${gameCode.ifBlank { "NONE" }}-%08X".format(crc1 and 0xFFFFFFFFL)
}

object N64Header {
    const val HEADER_SIZE = 0x40
    /** Boot code ends at 0x1000 and the checksum covers the next megabyte; a shorter file cannot start. */
    const val MIN_SIZE = 0x101000L

    /** File extensions the app recognises as N64 games or archives holding one. */
    val gameExtensions = setOf("z64", "n64", "v64", "u64", "rom", "bin")
    val archiveExtensions = setOf("zip", "7z")
    val allExtensions = gameExtensions + archiveExtensions

    /** Byte order from the first four bytes, or null when this is not an N64 game. */
    fun order(bytes: ByteArray): ByteOrder64? {
        if (bytes.size < 4) return null
        val b = IntArray(4) { bytes[it].toInt() and 0xFF }
        fun magic(first: Int, second: Int, third: Int, fourth: Int) =
            first == 0x80 && (second == 0x37 || second == 0x27) && (third == 0x12 || third == 0x07) && fourth == 0x40
        return when {
            magic(b[0], b[1], b[2], b[3]) -> ByteOrder64.BIG
            magic(b[1], b[0], b[3], b[2]) -> ByteOrder64.BYTE_SWAPPED
            magic(b[3], b[2], b[1], b[0]) -> ByteOrder64.LITTLE
            else -> null
        }
    }

    /** A copy of [bytes] (length a multiple of 4) in big-endian order, the order the header is documented in. */
    fun toBigEndian(bytes: ByteArray, order: ByteOrder64): ByteArray {
        val out = bytes.copyOf(bytes.size - bytes.size % 4)
        when (order) {
            ByteOrder64.BIG -> Unit
            ByteOrder64.BYTE_SWAPPED -> for (i in out.indices step 2) { val t = out[i]; out[i] = out[i + 1]; out[i + 1] = t }
            ByteOrder64.LITTLE -> for (i in out.indices step 4) {
                val a = out[i]; val b = out[i + 1]
                out[i] = out[i + 3]; out[i + 1] = out[i + 2]; out[i + 2] = b; out[i + 3] = a
            }
        }
        return out
    }

    /** Parses the first 0x40 bytes of a game file; null when they are not an N64 header. */
    fun parseHeader(header: ByteArray): N64Info? {
        if (header.size < HEADER_SIZE) return null
        val order = order(header) ?: return null
        val big = toBigEndian(header.copyOf(HEADER_SIZE), order)
        fun word(offset: Int): Long =
            ((big[offset].toLong() and 0xFF) shl 24) or ((big[offset + 1].toLong() and 0xFF) shl 16) or
                ((big[offset + 2].toLong() and 0xFF) shl 8) or (big[offset + 3].toLong() and 0xFF)
        val name = String(big, 0x20, 20, Charsets.US_ASCII).filter { it.code in 32..126 }.trim()
        val code = String(big, 0x3B, 4, Charsets.US_ASCII).filter { it.isLetterOrDigit() }
        return N64Info(gameCode = code, crc1 = word(0x10), crc2 = word(0x14), internalName = name,
            region = region(big[0x3E].toInt().toChar()), order = order)
    }

    /** Short region label from the header's country letter. */
    fun region(letter: Char): String = when (letter) {
        'E' -> "USA"
        'J' -> "Japan"
        'P', 'X', 'Y', 'D', 'F', 'I', 'S', 'H', 'W', 'L' -> "Europe"
        'U' -> "Australia"
        'B' -> "Brazil"
        'K' -> "Korea"
        'C' -> "China"
        'A' -> "World"
        else -> ""
    }

    /**
     * Why this file cannot be played, or null when it looks fine. Checked before the emulator
     * opens anything: a damaged or cut-off file crashes the engine instead of showing an error.
     */
    fun problem(header: ByteArray?, size: Long): String? = when {
        header == null || header.size < HEADER_SIZE -> "This file is too small to be an N64 game."
        order(header) == null -> "This file is not an N64 game, or it is damaged."
        size < MIN_SIZE -> "This game file is incomplete. Copy or download it again, then rescan."
        else -> null
    }

    /** A readable title from a file name: drops the extension, region tags and dump markers. */
    fun titleFromFileName(name: String): String {
        val base = name.substringBeforeLast('.')
            .replace(Regex("""^\d{4}\s*-\s*"""), "")
            .replace(Regex("""\s*[\[(][^\])]*[\])]"""), "")
            .replace('_', ' ')
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        // "Legend of Zelda, The - Ocarina of Time" → "The Legend of Zelda - Ocarina of Time"
        val article = Regex("""^(.*?), (The|A|An)\b(.*)$""").find(base)
        val fixed = if (article != null) "${article.groupValues[2]} ${article.groupValues[1]}${article.groupValues[3]}" else base
        return fixed.ifBlank { name }
    }
}
