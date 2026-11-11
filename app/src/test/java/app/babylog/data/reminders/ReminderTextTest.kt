package app.babylog.data.reminders

import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderMode
import app.babylog.domain.TimeFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words on a reminder, for every activity and both modes.
 *
 * Cheap and worth it: the notification is the only part of this feature a parent sees without opening the app,
 * and a wording change that reads badly ("since the last diaper") or leaks a medicine name is invisible to a
 * build. The Health rule is asserted again in `ReminderNotificationsTest` against the real catalogue; here it is
 * asserted as a property of the text builder for all six activities.
 */
class ReminderTextTest {

    private val zone: ZoneId = ZoneId.of("America/Port_of_Spain")
    private val anchor = at("2026-10-24", "09:00")
    private val now = at("2026-10-24", "12:05")

    private fun at(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private val afterLast = ReminderConfig(enabled = true, mode = ReminderMode.AFTER_LAST, intervalMinutes = 180)
    private val fixed = ReminderConfig(enabled = true, mode = ReminderMode.FIXED_TIMES, timesOfDay = listOf(12 * 60))

    @Test
    fun `the title names the child first, then the activity`() {
        assertEquals("Second Twin \u00b7 Bottle feed", ReminderText.title("Second Twin", EventType.BOTTLE))
        assertEquals("First Twin \u00b7 Diaper", ReminderText.title("First Twin", EventType.DIAPER))
    }

    @Test
    fun `an interval reminder says how long and since when`() {
        // Degraded on purpose: an alarm with NO armed time keeps exactly the line it had before Phase 30, where
        // the clock was the last log's.
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, null, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("3h 5m since the last feed \u00b7 9:00 AM", text)
    }

    // ---- Phase 30: the due time, and how overdue it is ---------------------

    @Test
    fun `an interval reminder leads with the time it was due`() {
        val due = at("2026-10-24", "12:05")
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, due, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("Due 12:05 PM \u00b7 3h 5m since the last feed", text)
    }

    @Test
    fun `a late interval reminder says how overdue it is, after the due time`() {
        val due = at("2026-10-24", "12:00")
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, due, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("Due 12:00 PM, 5m ago \u00b7 3h 5m since the last feed", text)
    }

    @Test
    fun `a fixed-time reminder that rang late says how late`() {
        val due = at("2026-10-24", "12:00")
        val text = ReminderText.text(fixed, EventType.DIAPER, anchor, due, now, zone, TimeFormat.TWELVE_HOUR)
        // A tail rather than an attachment: `Reminder for 12:00 PM` is already a complete sentence.
        assertEquals("Reminder for 12:00 PM \u00b7 5m ago", text)
    }

    @Test
    fun `a fixed-time reminder that rang on time says nothing about lateness`() {
        val text = ReminderText.text(fixed, EventType.DIAPER, anchor, now, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("Reminder for 12:05 PM", text)
    }

    @Test
    fun `lateness under a minute prints nothing rather than zero minutes`() {
        // `elapsedCoarse` floors to whole minutes, so without the guard an alarm that rang on time reads
        // `0m ago`, which is a bug the parent sees on the notification that arrived exactly as promised.
        val justNow = now - 30_000L
        val interval = ReminderText.text(afterLast, EventType.BOTTLE, anchor, justNow, now, zone, TimeFormat.TWELVE_HOUR)
        val fixedTime = ReminderText.text(fixed, EventType.DIAPER, anchor, justNow, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("Due 12:04 PM \u00b7 3h 5m since the last feed", interval)
        assertEquals("Reminder for 12:04 PM", fixedTime)
    }

    @Test
    fun `a clock change that puts the due time ahead claims no lateness`() {
        // Same rule as the future-anchor branch: never claim an interval that has not elapsed.
        val ahead = now + 60_000L
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, ahead, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("Due 12:06 PM \u00b7 3h 5m since the last feed", text)
    }

    @Test
    fun `an interval with no log still names when it was due`() {
        // A due time is a second FACT, so it does not turn the honest line back into an instruction.
        val due = at("2026-10-24", "12:00")
        val text = ReminderText.text(afterLast, EventType.DIAPER, null, due, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("Due 12:00 PM, 5m ago \u00b7 No nappy logged yet", text)
    }

    @Test
    fun `the due time is shown in the parent's clock format`() {
        val due = at("2026-10-24", "12:00")
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, due, now, zone, TimeFormat.TWENTY_FOUR_HOUR)
        assertEquals("Due 12:00, 5m ago \u00b7 3h 5m since the last feed", text)
    }

    @Test
    fun `the same text in 24-hour form`() {
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, null, now, zone, TimeFormat.TWENTY_FOUR_HOUR)
        assertEquals("3h 5m since the last feed \u00b7 09:00", text)
    }

    @Test
    fun `every activity has its own noun`() {
        assertEquals("feed", ReminderText.noun(EventType.BOTTLE))
        assertEquals("nappy", ReminderText.noun(EventType.DIAPER))
        assertEquals("solids", ReminderText.noun(EventType.SOLIDS))
        assertEquals("weigh-in", ReminderText.noun(EventType.GROWTH))
        assertEquals("health entry", ReminderText.noun(EventType.HEALTH))
        assertEquals("sleep", ReminderText.noun(EventType.SLEEP))
    }

    @Test
    fun `a fixed-time reminder says which time it is for`() {
        assertEquals(
            "Reminder for 12:05 PM",
            ReminderText.text(fixed, EventType.DIAPER, anchor, null, now, zone, TimeFormat.TWELVE_HOUR),
        )
    }

    @Test
    fun `an interval reminder for an activity that has never been logged states the fact`() {
        // Phase 14 deferred this wording as A3 and kept it, because the old line was honest about there being no
        // interval. It was not honest about the word: `Time to log solid` / `Time to log diaper` came from
        // `displayName()` lowercased rather than from `noun()`, so it also called a nappy a diaper.
        val text = ReminderText.text(afterLast, EventType.SOLIDS, anchorMs = null, armedAtMs = null, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("No solids logged yet", text)
    }

    @Test
    fun `the never-logged line uses the app's own word for each reminder activity`() {
        // The four activities that can carry a reminder (`REMINDER_TYPES`), not all six: Growth and Sleep have
        // no reminder, so their words cannot reach a notification.
        val expected = mapOf(
            EventType.BOTTLE to "No feed logged yet",
            EventType.DIAPER to "No nappy logged yet",
            EventType.SOLIDS to "No solids logged yet",
            EventType.HEALTH to "No health entry logged yet",
        )
        expected.forEach { (type, line) ->
            val text = ReminderText.text(afterLast, type, anchorMs = null, armedAtMs = null, now, zone, TimeFormat.TWELVE_HOUR)
            assertEquals(line, text)
            assertTrue("$type must not claim an interval it cannot measure: $text", !text.contains("since"))
            assertTrue("$type must not reuse the old instruction: $text", !text.startsWith("Time to log"))
        }
    }

    @Test
    fun `an anchor later than now claims no interval, because none has elapsed`() {
        // Only reachable through a clock change, and saying "No nappy logged yet" there would be false.
        val later = at("2026-10-24", "13:00")
        val text = ReminderText.text(afterLast, EventType.DIAPER, later, null, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("Logged for 1:00 PM", text)
    }

    @Test
    fun `elapsed drops the seconds a card timer keeps`() {
        assertEquals("45m", ReminderText.elapsedFor(45 * 60_000L))
        assertEquals("3h", ReminderText.elapsedFor(180 * 60_000L))
        assertEquals("3h 5m", ReminderText.elapsedFor((3 * 60 + 5) * 60_000L))
        assertEquals("1d 2h", ReminderText.elapsedFor((26 * 60) * 60_000L))
    }

    @Test
    fun `every activity and both modes produce a sentence, and health never mentions a medicine`() {
        EventType.entries.forEach { type ->
            // Both shapes: an alarm with no armed time, and one that knows when it was due.
            listOf(null, now).forEach { armedAt ->
                listOf(afterLast, fixed).forEach { config ->
                    val text = ReminderText.text(config, type, anchor, armedAt, now, zone, TimeFormat.TWELVE_HOUR)
                    assertTrue("$type/$config produced an empty line", text.isNotBlank())
                    assertTrue("$type/$config should not be shouted", !text.contains("!"))
                    if (type == EventType.HEALTH) {
                        // The activity, not a medicine: this is the rule AGENTS.md sets, restated at the text layer.
                        assertTrue("a health reminder must not name a medicine: $text", !text.contains("Calpol"))
                        assertTrue("a health reminder must not name a medicine: $text", !text.contains("ml"))
                    }
                }
            }
        }
    }
}
