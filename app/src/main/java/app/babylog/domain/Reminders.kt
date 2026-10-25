package app.babylog.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a reminder for one activity and one child should fire.
 *
 * Pure on purpose. Every other layer in this feature — the DataStore config, the AlarmManager wrapper, the
 * notification — only asks this file one question: "given this rule, this child's last log, and now, when is
 * the next reminder?". Keeping the answer here means the awkward cases (a fire time inside the night
 * window, a day that is not selected, an anchor that is already overdue) are tested on the JVM rather than
 * discovered on a phone at 3am.
 */
enum class ReminderMode {
    /** `N` after that child's last logged event of the activity — "3h after the last feed". */
    AFTER_LAST,

    /** At fixed clock times, several per day. */
    FIXED_TIMES,
}

/**
 * The activities that can carry a reminder.
 *
 * FOUR of the six, deliberately. Growth is a periodic weigh-in and Sleep is a state with its own running
 * timer; neither is something a parent forgets to record, so a reminder for either would be a notification
 * with nothing to act on. Bottle feed, Solids, Diaper and Health are the things that get missed at 3am.
 *
 * The one list the engine, the Settings rows and the summary all read, so an activity cannot have a row in
 * Settings that the scheduler ignores, or an alarm that has no row.
 */
val REMINDER_TYPES: List<EventType> = listOf(
    EventType.BOTTLE,
    EventType.SOLIDS,
    EventType.DIAPER,
    EventType.HEALTH,
)

/** Which activity, for which child. The pair is the identity of a reminder. */
data class ReminderKey(val type: EventType, val childId: String)

/**
 * One reminder rule. Defaults are what an untouched reminder looks like: OFF, three hours after the last
 * log, quiet inside the night window left alone, every day.
 *
 * [intervalMinutes] is minutes rather than hours because 1h30 is a real answer, and [timesOfDay] is minutes
 * since local midnight so the list sorts and dedupes without parsing strings.
 */
data class ReminderConfig(
    val enabled: Boolean = false,
    val mode: ReminderMode = ReminderMode.AFTER_LAST,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val timesOfDay: List<Int> = emptyList(),
    val skipNight: Boolean = false,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /**
     * When the rule was set, used as the anchor when that child has never had the activity logged.
     *
     * Without it, "every 3h after the last feed" on a child with no feeds yet had NO anchor: the alarm was never
     * armed, the card showed a rule with no time, and the reminder could not fire until a feed was logged by hand.
     * Found on the phone: a Bottle feed reminder that said `Every 3h` and would never ring.
     */
    val anchorAt: Long? = null,
) {
    companion object {
        /** 3h, the reference app's own default for a feed reminder. */
        const val DEFAULT_INTERVAL_MINUTES = 180

        /** The intervals a parent can pick. A preset list, not a keyboard: nothing typed at 3am. */
        val INTERVAL_CHOICES: List<Int> =
            listOf(30, 60, 90, 120, 150, 180, 210, 240, 300, 360)
    }
}

/**
 * The next moment this reminder should fire, or null when it should not fire at all.
 *
 * Null is a real answer in four different situations, and they are deliberately not collapsed:
 *
 *  - the rule is **disabled**;
 *  - it is `AFTER_LAST` and [anchorMs] is null — that child has never had this activity logged, and a
 *    reminder with nothing to measure from would have to invent an anchor;
 *  - the fire time is **overdue** (`<= now`). A phone that was off for eight hours must not buzz the instant
 *    the app opens: being in the app IS the reminder. The next log re-anchors it;
 *  - it is `AFTER_LAST` and the resulting local day is not one of [ReminderConfig.days]. An interval that
 *    lands on a day the parent switched off is simply not fired; the next log re-anchors it. `FIXED_TIMES`
 *    never hits this, because it walks forward to a selected day in the first place.
 *
 * [night] is the same `NightWindow` Summary uses for its day/night split, so "stay quiet overnight" needs no
 * new setting and cannot disagree with the rest of the app about when night is.
 */
fun nextReminderAt(
    config: ReminderConfig,
    anchorMs: Long?,
    now: Long,
    zone: ZoneId,
    night: NightWindow,
): Long? {
    if (!config.enabled) return null
    val base = when (config.mode) {
        ReminderMode.AFTER_LAST -> {
            // The child's own last log wins; the moment the rule was set is the fallback, so a reminder for an
            // activity nobody has logged yet still fires once.
            val anchor = anchorMs ?: config.anchorAt ?: return null
            anchor + config.intervalMinutes * 60_000L
        }
        ReminderMode.FIXED_TIMES -> nextFixedTime(config.timesOfDay, config.days, now, zone) ?: return null
    }

    var at = if (config.skipNight && isNight(base, zone, night)) endOfNight(base, zone, night) else base
    // An interval that is already due is DUE, not cancelled. Returning null here used to make the reminder vanish -
    // no alarm and no chip - in the one situation that matters most: nobody has logged this activity for hours. It
    // rings a minute from now, once, and the next log re-anchors it.
    if (at <= now && config.mode == ReminderMode.AFTER_LAST) {
        at = now + OVERDUE_RING_DELAY_MS
    }
    if (at <= now) return null
    if (config.mode == ReminderMode.AFTER_LAST &&
        Instant.ofEpochMilli(at).atZone(zone).dayOfWeek !in config.days
    ) {
        return null
    }
    return at
}

/**
 * The first chosen time-of-day strictly after [now], on a day in [days], looking at most a week ahead.
 *
 * Strictly after, not "at or after": a reminder armed for exactly the current minute would fire immediately
 * and then be recomputed into the same instant, which is how a notification loop starts.
 *
 * The day walk is what makes "repeat on specific days" mean anything for fixed times. Filtering the result
 * afterwards would leave the alarm unarmed for the whole of a selected day unless the app happened to be
 * opened, which is a reminder that silently does not exist.
 */
private fun nextFixedTime(
    timesOfDay: List<Int>,
    days: Set<DayOfWeek>,
    now: Long,
    zone: ZoneId,
): Long? {
    val times = timesOfDay.filter { it in 0 until MINUTES_PER_DAY }.distinct().sorted()
    if (times.isEmpty() || days.isEmpty()) return null
    val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
    val nowMinute = nowZoned.hour * 60 + nowZoned.minute

    for (ahead in 0..7) {
        val day = nowZoned.toLocalDate().plusDays(ahead.toLong())
        if (day.dayOfWeek !in days) continue
        val minute = if (ahead == 0) times.firstOrNull { it > nowMinute } ?: continue else times.first()
        return day.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
    }
    return null
}

/**
 * The end of the night window that contains [at] — 06:00 for the default 18:00-06:00.
 *
 * A reminder that would land at 02:00 waits until the window ends rather than being dropped: the parent still
 * wants to know at breakfast, and the notification says how long it has actually been (`7h since the last
 * feed`), so a delayed reminder cannot be read as a fresh one.
 */
private fun endOfNight(at: Long, zone: ZoneId, night: NightWindow): Long {
    val zoned = Instant.ofEpochMilli(at).atZone(zone)
    // The window wraps midnight, so a fire time at or after the start belongs to tomorrow's end.
    val day = if (zoned.hour >= night.startHour) zoned.toLocalDate().plusDays(1) else zoned.toLocalDate()
    return day.atTime(LocalTime.of(night.endHour, 0)).atZone(zone).toInstant().toEpochMilli()
}

private const val MINUTES_PER_DAY = 24 * 60

/** `3h` · `1h 30m` · `30m`. */
fun intervalLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

/**
 * Minutes since midnight as a clock time, in the parent's chosen format: `7:00 AM` or `07:00`.
 *
 * It used to be hardcoded 24-hour with the reasoning that "a rule is a clock time, not a display
 * preference" — which the phone disproved: the picker and the notification said `11:00 PM` while the row
 * and the Settings summary said `23:00`, in one sheet. Phase 13.1's preference exists precisely so the app
 * never shows two clock conventions at once.
 */
fun clockLabel(minutesSinceMidnight: Int, format: TimeFormat = TimeFormat.TWELVE_HOUR): String =
    LocalTime.of((minutesSinceMidnight / 60) % 24, minutesSinceMidnight % 60)
        .format(
            java.time.format.DateTimeFormatter.ofPattern(
                if (format == TimeFormat.TWELVE_HOUR) "h:mm a" else "HH:mm",
                java.util.Locale.ENGLISH,
            ),
        )

/**
 * One row of the Activity screen's "Upcoming reminders" card.
 *
 * [childName] is set ONLY when the twins' next times for this activity differ. Two children on the same rule
 * are one fact ("a nappy is due at 1:00"), and printing it twice would make the card look like double
 * bookkeeping; two children on different rules are two facts, and the row has to say whose.
 */
data class UpcomingReminder(
    val type: EventType,
    val at: Long,
    val childId: String? = null,
    val childName: String? = null,
)

/**
 * What the card shows: the next few armed reminders, soonest first.
 *
 * The same [nextReminderAt] the scheduler uses, so the card cannot promise a reminder the alarm is not going
 * to make. Nothing is invented here — a rule that is off, has no anchor, or has landed on a day it does not
 * run simply contributes no row.
 */
fun upcomingReminders(
    configs: Map<ReminderKey, ReminderConfig>,
    children: List<Child>,
    anchorFor: (EventType, String) -> Long?,
    now: Long,
    zone: ZoneId,
    night: NightWindow,
    limit: Int = DEFAULT_UPCOMING_LIMIT,
): List<UpcomingReminder> {
    if (children.isEmpty()) return emptyList()
    val rows = mutableListOf<UpcomingReminder>()

    REMINDER_TYPES.forEach { type ->
        val perChild = children.mapNotNull { child ->
            val config = configs[ReminderKey(type, child.id)] ?: return@mapNotNull null
            val at = nextReminderAt(config, anchorFor(type, child.id), now, zone, night) ?: return@mapNotNull null
            Triple(child, config, at)
        }
        if (perChild.isEmpty()) return@forEach
        // Three cases, and the third is the one the phone caught:
        //  - both children on the same rule  -> one row, no name ("a nappy is due at 8:00");
        //  - both on different rules          -> one row each, each named;
        //  - only ONE child has a rule        -> one row that MUST be named, because an unnamed row reads as
        //    "this applies to both" and the other twin is not going to be reminded at all.
        val sameTime = perChild.map { it.third }.distinct().size == 1
        if (sameTime && perChild.size == children.size) {
            rows += UpcomingReminder(type = type, at = perChild.first().third)
        } else {
            perChild.forEach { (child, _, at) ->
                rows += UpcomingReminder(type = type, at = at, childId = child.id, childName = child.name)
            }
        }
    }

    return rows.sortedBy { it.at }.take(limit)
}

/**
 * How many rows the card shows before it stops.
 *
 * Five, not three: the mockup showed three because three were armed, and hiding a fourth reminder behind a
 * card that claims to list them would be the app keeping a secret from the parent.
 */
const val DEFAULT_UPCOMING_LIMIT = 5

/** How soon an already-due interval reminder rings: a minute, so it is not a surprise mid-tap. */
const val OVERDUE_RING_DELAY_MS = 60_000L

/** One armed reminder: the interval, and the moment it will ring. */
data class ReminderArm(val intervalMinutes: Int, val at: Long)

/**
 * One chip on a card: whose reminder it is, and what it says.
 *
 * A LIST rather than one line, because with both children on screen each child's reminder is its own fact: First
 * Twin's 1:00 PM and Second Twin's 2:30 PM are two chips, each with that child's badge. Squashing them into
 * `3h (1:00 PM), 4h (2:30 PM)` made the parent read a sentence to find out which twin was which.
 *
 * [childId] is null when only one child is on screen: the card is already about that child, and a badge on every row
 * of a single-child card says nothing (the same rule the entry rows follow).
 */
data class ReminderChip(val childId: String?, val text: String)

/**
 * The words on one chip: `Every 3h (1:00 PM)`, or `Every 3h (due now)` once the target has passed.
 *
 * The TIME is the point — a chip that says only `Every 3h` tells the parent the rule but not when they will be
 * interrupted, which is what they actually want to know.
 */
fun reminderChipText(
    arm: ReminderArm,
    zone: ZoneId,
    format: TimeFormat,
    now: Long,
): String {
    val when_ = if (arm.at - now <= OVERDUE_RING_DELAY_MS * 2) "due now" else clockTime(arm.at, zone, format)
    return "Every ${intervalLabel(arm.intervalMinutes)} ($when_)"
}
