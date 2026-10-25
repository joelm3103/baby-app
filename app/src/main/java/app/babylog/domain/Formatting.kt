package app.babylog.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatting is pure so it can be unit-tested without an emulator, and every function
 * takes `now`/`zone` explicitly rather than reading the clock or the device default —
 * a test that depends on the wall clock is a test that fails at midnight.
 */

private const val MINUTE = 60_000L
private const val HOUR = 60L * MINUTE
private const val DAY = 24L * HOUR

/** The app's most-read string. Answers "when was the last one?" before anything else. */
fun relativeTime(now: Long, then: Long): String {
    val diff = (now - then).coerceAtLeast(0L)
    return when {
        diff < MINUTE -> "just now"
        diff < HOUR -> "${diff / MINUTE}m ago"
        diff < DAY -> {
            val hours = diff / HOUR
            val minutes = (diff % HOUR) / MINUTE
            if (minutes == 0L) "${hours}h ago" else "${hours}h ${minutes}m ago"
        }
        else -> "${diff / DAY}d ago"
    }
}

/** Count-up duration for a running timer, or the total for a finished one. */
fun elapsed(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        // A running timer that has not reached a minute must not read "0m 45s".
        else -> "${seconds}s"
    }
}

private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * A COARSE elapsed span: `45m` · `3h` · `3h 5m` · `2d 3h`.
 *
 * The sibling of [elapsed], which is second-accurate because a running timer needs it. A countdown on a
 * reminder card and the "since the last feed" line in a notification are neither: seconds there are noise
 * that changes every second and makes the text twitch. One implementation, used by both.
 */
fun elapsedCoarse(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 && minutes % 60 == 0L -> "${hours}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

/** Absolute clock time for timeline rows, in the display zone. */
enum class TimeFormat { TWENTY_FOUR_HOUR, TWELVE_HOUR }

fun clockTime(instantMs: Long, zone: ZoneId, format: TimeFormat = TimeFormat.TWELVE_HOUR): String =
    Instant.ofEpochMilli(instantMs).atZone(zone).toLocalTime()
        .format(DateTimeFormatter.ofPattern(if (format == TimeFormat.TWELVE_HOUR) "h:mm a" else "HH:mm", Locale.ENGLISH))

/**
 * The instant for [hour]:[minute] **on the calendar day of [instantMs]**, in [zone].
 *
 * Used when the time row in a sheet is edited: the day must not shift, and doing this in UTC instead of
 * the display zone would move an 11:30pm feed to the wrong date. Explicit `zone` for the same reason
 * every other formatter here takes one — a function that reads the device default cannot be tested.
 */
fun timeOfDayOn(instantMs: Long, hour: Int, minute: Int, zone: ZoneId): Long =
    Instant.ofEpochMilli(instantMs).atZone(zone).toLocalDate()
        .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

/**
 * The companion of [timeOfDayOn]: the instant with the TIME OF DAY of [instantMs] but on [epochDay], in
 * [zone].
 *
 * The two must not interfere — editing a sheet's date keeps the clock time, editing the time keeps the
 * date — which is why both go through `LocalDate`/`LocalTime` in the display zone rather than doing
 * millisecond arithmetic. A slip here files a night feed on the wrong day and looks entirely plausible.
 */
fun dateOn(instantMs: Long, epochDay: Long, zone: ZoneId): Long =
    LocalDate.ofEpochDay(epochDay)
        .atTime(Instant.ofEpochMilli(instantMs).atZone(zone).toLocalTime())
        .atZone(zone).toInstant().toEpochMilli()

/** "Today" / "Yesterday" / "Wed 12 Mar" for a day divider. */
fun dayLabel(instantMs: Long, zone: ZoneId, now: Long): String {
    val date = Instant.ofEpochMilli(instantMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormatter)
    }
}

/** A day's events, newest first. */
data class DayGroup(val label: String, val events: List<BabyEvent>)

/**
 * Groups newest-day-first, and within a day newest-first. Deleted events are dropped;
 * the caller is expected to have filtered them already, but the filter is cheap insurance.
 */
fun groupByDay(events: List<BabyEvent>, zone: ZoneId, now: Long): List<DayGroup> =
    events.asSequence()
        .filterNot { it.deleted }
        .sortedByDescending { it.startedAt }
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (_, dayEvents) ->
            DayGroup(
                label = dayLabel(dayEvents.first().startedAt, zone, now),
                events = dayEvents,
            )
        }
