package com.appkraft.turbo64.library

import android.content.Context

/**
 * The name a game card shows, best first:
 * 1. the full official name, looked up offline by the header checksum (CRC1) in
 *    `assets/n64names.tsv` (from the Mupen64Plus ROM catalogue);
 * 2. the name written inside the game (20 characters, often upper case), tidied;
 * 3. the file name, with web codes such as `%20` turned back into spaces.
 * So a file called "N642FSuper20Mario2064.z64" still shows as "Super Mario 64".
 */
object GameNames {
    private const val ASSET = "n64names.tsv"
    @Volatile private var table: Map<String, String>? = null

    /** The official name for a header CRC1, or null when the game is not in the list. */
    fun official(context: Context, crc1: Long): String? {
        val names = table ?: synchronized(this) {
            table ?: runCatching { context.applicationContext.assets.open(ASSET).bufferedReader().use { parse(it.readLines()) } }
                .getOrDefault(emptyMap()).also { table = it }
        }
        return names[crcKey(crc1)]
    }

    /** CRC1 of a game from its library id ("CODE-1234ABCD"), or null for ids not made from a header. */
    fun crcFromId(id: String): Long? =
        id.takeUnless { it.startsWith("F-") }?.substringAfterLast('-', "")?.takeIf { it.length == 8 }?.toLongOrNull(16)

    /** The card name for a game, following the order above. */
    fun title(context: Context, info: N64Info?, fileName: String): String =
        choose(info?.let { official(context, it.crc1) }, info?.internalName.orEmpty(), fileName)

    // ---- Pure rules (unit tested) ----------------------------------------------------------

    fun crcKey(crc1: Long): String = "%08X".format(crc1 and 0xFFFFFFFFL)

    fun parse(lines: List<String>): Map<String, String> = lines.asSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 && it[0].length == 8 }?.let { it[0].uppercase() to it[1].trim() } }
        .filter { it.second.isNotEmpty() }
        .toMap()

    fun choose(official: String?, internalName: String, fileName: String): String {
        official?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        headerTitle(internalName).takeIf { it.count(Char::isLetter) >= 3 }?.let { return it }
        return N64Header.titleFromFileName(decodeFileName(fileName)).trim().ifBlank { "N64 game" }
    }

    /** "SUPER MARIO 64" → "Super Mario 64"; acronyms, numbers and Roman numerals stay as they are. */
    fun headerTitle(internalName: String): String {
        val name = internalName.trim().replace(Regex("\\s{2,}"), " ")
        if (name.isEmpty()) return ""
        // Mixed case was written by the publisher on purpose; keep it.
        if (name.any(Char::isLowerCase)) return name
        val keep = setOf("WCW", "NWO", "WWF", "NBA", "NFL", "NHL", "FIFA", "USA", "GT", "F1", "CD", "DX", "EX", "NASCAR", "MLB", "PGA", "RC", "TV")
        val roman = Regex("^(I|II|III|IV|V|VI|VII|VIII|IX|X)$")
        val small = setOf("OF", "THE", "AND", "TO", "IN", "ON", "A", "AN", "FOR", "VS")
        return name.split(' ').mapIndexed { index, word ->
            val bare = word.trim(':', '-', '!', '.', ',')
            when {
                bare.any(Char::isDigit) || bare in keep || roman.matches(bare) -> word
                index > 0 && bare in small -> word.lowercase()
                else -> word.lowercase().split('-').joinToString("-") { part -> part.replaceFirstChar(Char::uppercase) }
            }
        }.joinToString(" ")
    }

    /**
     * Turns web codes back into characters: "Super%20Mario%2064" → "Super Mario 64". Some
     * downloads lose the "%" and leave "N642FSuper20Mario2064"; those runs are fixed as well.
     */
    fun decodeFileName(name: String): String {
        var text = name
        if ('%' in text) text = Regex("%([0-9A-Fa-f]{2})").replace(text) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
        // Lost "%": only when the name has no spaces at all and shows at least two such codes.
        if (' ' !in text) {
            val codes = Regex("(?<=[A-Za-z0-9])(20|2F|5F)(?=[A-Za-z0-9])")
            if (codes.findAll(text).count() >= 2) {
                text = codes.replace(text) { if (it.value == "2F") "/" else " " }
            }
        }
        // A folder part from an encoded path ("N64/Super Mario 64") is not part of the name.
        return text.substringAfterLast('/').trim()
    }
}
