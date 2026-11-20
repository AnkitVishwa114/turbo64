package com.appkraft.turbo64.library

/** What a picked file turned out to be. */
enum class PickResult { ADDED, ALREADY_THERE, NOT_A_GAME, FAILED }

/** The result of adding one picked file, with the game title or the reason it was not added. */
data class PickOutcome(val result: PickResult, val title: String = "", val reason: String? = null)

/** What kind of file the first bytes show, whatever the file is called. */
enum class SourceKind { GAME, ZIP, SEVEN_Z, UNKNOWN }

/**
 * Rules for files picked one by one. They do not touch Android, so unit tests cover them:
 * telling the file kind from its first bytes (some file managers give no name), the title
 * of a game copied into the app, and the message shown after adding.
 */
object PickRules {
    /** Bytes needed by [sniff]. */
    const val SNIFF_SIZE = 8

    fun sniff(first: ByteArray?, name: String): SourceKind {
        if (first != null && first.size >= 6) {
            val b = IntArray(6) { first[it].toInt() and 0xFF }
            if (b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04) return SourceKind.ZIP
            if (b[0] == 0x37 && b[1] == 0x7A && b[2] == 0xBC && b[3] == 0xAF && b[4] == 0x27 && b[5] == 0x1C) return SourceKind.SEVEN_Z
            if (first.size >= 4 && N64Header.order(first.copyOf(4)) != null) return SourceKind.GAME
        }
        // Empty or unreadable start: fall back to the name, the reading step reports what is wrong.
        return when (name.substringAfterLast('.', "").lowercase()) {
            "zip" -> SourceKind.ZIP
            "7z" -> SourceKind.SEVEN_Z
            in N64Header.gameExtensions -> SourceKind.GAME
            else -> SourceKind.UNKNOWN
        }
    }

    /** Title of a game copied into the app: from the file name when there is one, else the game's own name. */
    fun importTitle(displayName: String, internalName: String): String {
        val fromName = displayName.trim().takeIf { it.isNotEmpty() }?.let(N64Header::titleFromFileName).orEmpty()
        return fromName.ifBlank { internalName.trim() }.ifBlank { "N64 game" }
    }

    /** One short line for the snackbar after adding files. */
    fun summary(outcomes: List<PickOutcome>): String {
        if (outcomes.isEmpty()) return "Nothing was added"
        if (outcomes.size == 1) {
            val only = outcomes.first()
            return when (only.result) {
                PickResult.ADDED -> "${only.title} added"
                PickResult.ALREADY_THERE -> "${only.title} is already in your library"
                PickResult.NOT_A_GAME -> "That file is not an N64 game"
                PickResult.FAILED -> only.reason ?: "That file could not be added. Please try again."
            }
        }
        val added = outcomes.count { it.result == PickResult.ADDED }
        val already = outcomes.count { it.result == PickResult.ALREADY_THERE }
        val notGames = outcomes.count { it.result == PickResult.NOT_A_GAME }
        val failed = outcomes.filter { it.result == PickResult.FAILED }
        val parts = buildList {
            if (added > 0) add("$added game${if (added == 1) "" else "s"} added")
            if (already > 0) add("$already already in your library")
            if (notGames > 0) add("$notGames not N64 game${if (notGames == 1) "" else "s"}")
            if (failed.size == 1) add(failed.first().reason ?: "1 could not be added")
            else if (failed.size > 1) add("${failed.size} could not be added")
        }
        return parts.joinToString(". ")
    }
}
