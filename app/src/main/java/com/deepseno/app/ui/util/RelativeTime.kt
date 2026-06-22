package com.enmooy.deepseno.ui.util

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Format an ISO-8601 timestamp as a short "x minutes/hours/days ago" string.
 * Returns null if the input can't be parsed — caller should hide the row.
 */
object RelativeTime {
    fun ago(iso: String, isZh: Boolean = Locale.getDefault().language == "zh"): String? {
        val instant = parse(iso) ?: return null
        val now = Instant.now()
        val seconds = ChronoUnit.SECONDS.between(instant, now).coerceAtLeast(0)
        val zh = isZh
        return when {
            seconds < 60 -> if (zh) "刚刚" else "just now"
            seconds < 3600 -> {
                val m = seconds / 60
                if (zh) "$m 分钟前" else "${m}m ago"
            }
            seconds < 86400 -> {
                val h = seconds / 3600
                if (zh) "$h 小时前" else "${h}h ago"
            }
            seconds < 604800 -> {
                val d = seconds / 86400
                if (zh) "$d 天前" else "${d}d ago"
            }
            else -> {
                val w = seconds / 604800
                if (zh) "$w 周前" else "${w}w ago"
            }
        }
    }

    private fun parse(iso: String): Instant? {
        return try {
            Instant.parse(iso)
        } catch (_: Throwable) {
            // Some servers return TZ offset without 'Z' — try OffsetDateTime fallback.
            try {
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(iso, Instant::from)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
