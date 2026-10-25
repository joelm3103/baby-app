package app.babylog.data.reminders

import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderMode
import app.babylog.domain.TimeFormat
import app.babylog.domain.clockTime
import app.babylog.domain.displayName
import app.babylog.domain.elapsedCoarse
import java.time.ZoneId

/**
 * What a reminder notification says.
 *
 * Separated from the posting code so the words can be tested without a NotificationManager — and so the one
 * rule that must never be broken is visible in one place:
 *
 * **A health reminder names the ACTIVITY and nothing else.** It never says a medicine, never says a dose, and
 * never says "time for Calpol". `AGENTS.md` forbids the app proposing a dose in any form, and a reminder is a
 * form: a parent woken at 3am by "Calpol" would read it as the app telling them to give some. So Health gets a
 * deliberately plain line, and `ReminderTextTest` pins it against every medicine in the bundled catalogue.
 *
 * [format] is passed in rather than read from the Compose `LocalTimeFormat`: a notification is posted from a
 * BroadcastReceiver, where there is no composition to read.
 */
object ReminderText {

    /** `First Twin · Bottle feed`. The child first, because two notifications can arrive at once. */
    fun title(childName: String, type: EventType): String = "$childName \u00b7 ${type.displayName()}"

    /**
     * `3h 5m since the last feed · 9:41 PM`, or `Reminder for 7:00 AM`.
     *
     * The elapsed time is measured from the anchor the rule actually used, so a reminder delayed by the night
     * window cannot be misread as a fresh one: at 06:00 it says seven hours, not "time to feed again".
     */
    fun text(
        config: ReminderConfig,
        type: EventType,
        anchorMs: Long?,
        now: Long,
        zone: ZoneId,
        format: TimeFormat,
    ): String = when (config.mode) {
        ReminderMode.AFTER_LAST -> {
            val since = anchorMs?.let { now - it }?.takeIf { it > 0 }
            if (since == null || anchorMs == null) {
                "Time to log ${type.displayName().lowercase()}"
            } else {
                "${elapsedFor(since)} since the last ${noun(type)} \u00b7 ${clockTime(anchorMs, zone, format)}"
            }
        }
        ReminderMode.FIXED_TIMES -> "Reminder for ${clockTime(now, zone, format)}"
    }

    /** `3h 5m` — the shared coarse formatter, so the card and the notification cannot disagree. */
    fun elapsedFor(ms: Long): String = elapsedCoarse(ms)

    /**
     * The activity as a sentence would use it: `feed`, `nappy`, `solids`, `weigh-in`, `health entry`, `sleep`.
     *
     * Not [EventType.displayName] lowercased — "since the last diaper" is not how a parent says it, and the
     * Health one is deliberately vague for the reason in the class comment.
     */
    fun noun(type: EventType): String = when (type) {
        EventType.BOTTLE -> "feed"
        EventType.DIAPER -> "nappy"
        EventType.SOLIDS -> "solids"
        EventType.GROWTH -> "weigh-in"
        EventType.HEALTH -> "health entry"
        EventType.SLEEP -> "sleep"
    }
}
