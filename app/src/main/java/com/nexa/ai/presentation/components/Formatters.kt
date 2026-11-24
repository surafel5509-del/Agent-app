package com.nexa.ai.presentation.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** "14:05" for message timestamps, empty when the timestamp is unknown. */
fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }.getOrDefault("")
}

/** Compact "now / 5m / 2h / 3d / 12 Mar" label for the history drawer. */
fun formatRelativeTime(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return ""
    val difference = nowMillis - millis
    return when {
        difference < TimeUnit.MINUTES.toMillis(1) -> "now"
        difference < TimeUnit.HOURS.toMillis(1) ->
            "${TimeUnit.MILLISECONDS.toMinutes(difference)}m"
        difference < TimeUnit.DAYS.toMillis(1) ->
            "${TimeUnit.MILLISECONDS.toHours(difference)}h"
        difference < TimeUnit.DAYS.toMillis(7) ->
            "${TimeUnit.MILLISECONDS.toDays(difference)}d"
        else -> runCatching {
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormatter)
        }.getOrDefault("")
    }
}

/** "0:42" / "12:05" for the agent-working timer. */
fun formatElapsed(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remainder = safe % 60
    return String.format(Locale.US, "%d:%02d", minutes, remainder)
}

/** Shortens multi-line text for list previews. */
fun previewOf(text: String, maxLength: Int = 120): String {
    val single = text.replace('\n', ' ').trim()
    return if (single.length <= maxLength) single else single.take(maxLength).trimEnd() + "…"
}
