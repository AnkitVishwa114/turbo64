package com.appkraft.turbo64.library

import android.content.Context

/**
 * Play time written by the player itself, every time the game pauses or closes. The library
 * reads and clears it when it is shown again, so "last played" and play time are right even if
 * the library screen was rebuilt or the app was closed while a game ran.
 */
object PlaySessions {
    private const val PREFS = "play_sessions"

    /** Adds [playedMs] of play to [id], played just now. */
    @Synchronized
    fun record(context: Context, id: String, playedMs: Long) {
        if (playedMs <= 0 || id.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getString(id, null)?.substringAfter(',')?.toLongOrNull() ?: 0L
        prefs.edit().putString(id, "${System.currentTimeMillis()},${before + playedMs}").commit()
    }

    /** Every game played since the last call: id to (when, how long). Clears the list. */
    @Synchronized
    fun drain(context: Context): Map<String, Pair<Long, Long>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all.mapNotNull { (id, value) ->
            val parts = (value as? String)?.split(',') ?: return@mapNotNull null
            val at = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val ms = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            id to (at to ms)
        }.toMap()
        if (prefs.all.isNotEmpty()) prefs.edit().clear().commit()
        return all
    }
}
