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
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, now, zone, TimeFormat.TWELVE_HOUR)
        assertEquals("3h 5m since the last feed \u00b7 9:00 AM", text)
    }

    @Test
    fun `the same text in 24-hour form`() {
        val text = ReminderText.text(afterLast, EventType.BOTTLE, anchor, now, zone, TimeFormat.TWENTY_FOUR_HOUR)
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
            ReminderText.text(fixed, EventType.DIAPER, anchor, now, zone, TimeFormat.TWELVE_HOUR),
        )
    }

    @Test
    fun `an interval reminder for an activity that has never been logged states the fact`() {
        // Phase 14 deferred this wording as A3 and kept it, because the old line was honest about there being no
        // interval. It was not honest about the word: `Time to log solid` / `Time to log diaper` came from
        // `displayName()` lowercased rather than from `noun()`, so it also called a nappy a diaper.
        val text = ReminderText.text(afterLast, EventType.SOLIDS, anchorMs = null, now, zone, TimeFormat.TWELVE_HOUR)
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
            val text = ReminderText.text(afterLast, type, anchorMs = null, now, zone, TimeFormat.TWELVE_HOUR)
            assertEquals(line, text)
            assertTrue("$type must not claim an interval it cannot measure: $text", !text.contains("since"))
            assertTrue("$type must not reuse the old instruction: $text", !text.startsWith("Time to log"))
        }
    }

    @Test
    fun `an anchor later than now claims no interval, because none has elapsed`() {
        // Only reachable through a clock change, and saying "No nappy logged yet" there would be false.
        val later = at("2026-10-24", "13:00")
        val text = ReminderText.text(afterLast, EventType.DIAPER, later, now, zone, TimeFormat.TWELVE_HOUR)
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
            listOf(afterLast, fixed).forEach { config ->
                val text = ReminderText.text(config, type, anchor, now, zone, TimeFormat.TWELVE_HOUR)
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
