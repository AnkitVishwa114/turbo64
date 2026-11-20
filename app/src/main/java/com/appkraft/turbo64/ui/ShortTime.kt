package com.appkraft.turbo64.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Short time texts that fit small places (stat tiles, card lines, the hero row) without being
 * cut off: "< 1 min", "12 min", "2 h 5 min"; "Just now", "5 min ago", "3 h ago", "Yesterday".
 */
object ShortTime {
    fun played(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }

    fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ((now - at) / 60_000).coerceAtLeast(0)
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            days < 2 -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(at))
        }
    }
}
