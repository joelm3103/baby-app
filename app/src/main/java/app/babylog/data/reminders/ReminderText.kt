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

    private const val MINUTE_MS = 60_000L

    /** `First Twin · Bottle feed`. The child first, because two notifications can arrive at once. */
    fun title(childName: String, type: EventType): String = "$childName \u00b7 ${type.displayName()}"

    /**
     * `Due 9:41 PM · 3h 5m since the last feed`, `Due 9:41 PM, 20m ago · 3h 5m since the last feed`, or
     * `Reminder for 7:00 AM · 12m ago`.
     *
     * **Lateness first, and measured from the time the alarm was armed for** (Phase 30). [armedAtMs] is what the
     * parent was told to expect, carried in the alarm's own intent, so it does not drift when the log grows between
     * arming and firing; a recomputation would also have to guess which fixed time had just passed. The two halves
     * answer two questions in the order they matter at 3am: when was this meant to happen, and how long has the
     * child actually gone.
     *
     * Lateness is a TAIL for a fixed time (`Reminder for 7:00 AM · 12m ago`, because that sentence is already
     * complete) and attaches to the due time for an interval (`Due 9:41 PM, 20m ago`, because `Due 9:41 PM` is a
     * fragment that needs its comma).
     *
     * The elapsed time is measured from the anchor the rule actually used, so a reminder delayed by the night
     * window cannot be misread as a fresh one: at 06:00 it says seven hours, not "time to feed again".
     *
     * An interval with **no log to measure from** states that fact rather than instructing: `Due 9:41 PM ·
     * No solids logged yet`. It used to read `Time to log solids` — and, through [EventType.displayName]
     * lowercased, `Time to log diaper`, which is not a sentence and names the wrong word for what the rest of the
     * app calls a nappy. A due time is a second fact, never an instruction.
     *
     * Three guards, each of which was a way to print a lie:
     *
     * - **Under a minute prints nothing.** [elapsedCoarse] floors to whole minutes, so an alarm that rang on time
     *   would otherwise read `0m ago`.
     * - **A negative lateness prints nothing.** A backwards clock change can put the armed time after now, and this
     *   refuses to claim a negative interval for the same reason the future-anchor branch does.
     * - **No armed time degrades to the old text exactly.** An alarm armed by an older build carries no such extra,
     *   and it must not lose the line it already had: `3h 5m since the last feed · 9:41 PM`.
     */
    fun text(
        config: ReminderConfig,
        type: EventType,
        anchorMs: Long?,
        armedAtMs: Long?,
        now: Long,
        zone: ZoneId,
        format: TimeFormat,
    ): String = when (config.mode) {
        ReminderMode.AFTER_LAST -> {
            val since = anchorMs?.let { now - it }?.takeIf { it > 0 }
            val due = armedAtMs?.let { dueHead(it, now, zone, format) }
            when {
                anchorMs == null -> join(due, "No ${noun(type)} logged yet")
                // An anchor LATER than now is only reachable through a clock change. Saying "no nappy logged
                // yet" would then be false, so this states what the log holds instead of claiming an interval
                // that has not elapsed.
                since == null -> join(due, "Logged for ${clockTime(anchorMs, zone, format)}")
                else -> {
                    val elapsed = "${elapsedFor(since)} since the last ${noun(type)}"
                    // With no armed time the clock on this line is the LAST LOG's, exactly as it was before
                    // Phase 30 — the degraded path is the old text, not a half-new one.
                    if (due == null) join(elapsed, clockTime(anchorMs, zone, format)) else join(due, elapsed)
                }
            }
        }
        ReminderMode.FIXED_TIMES -> join(
            "Reminder for ${clockTime(armedAtMs ?: now, zone, format)}",
            lateness(armedAtMs, now),
        )
    }

    /** `Due 9:41 PM`, or `Due 9:41 PM, 20m ago` when it rang late. */
    private fun dueHead(armedAtMs: Long, now: Long, zone: ZoneId, format: TimeFormat): String {
        val due = "Due ${clockTime(armedAtMs, zone, format)}"
        return lateness(armedAtMs, now)?.let { "$due, $it" } ?: due
    }

    /** `12m ago`, or null when this was not late enough to be worth a word. See the three guards above. */
    private fun lateness(armedAtMs: Long?, now: Long): String? {
        val late = now - (armedAtMs ?: return null)
        return if (late >= MINUTE_MS) "${elapsedFor(late)} ago" else null
    }

    /** The one joiner, so a missing half never leaves a dangling separator. */
    private fun join(head: String?, tail: String?): String =
        listOfNotNull(head, tail).filter { it.isNotBlank() }.joinToString(" \u00b7 ")

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
