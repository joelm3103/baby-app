package app.babylog.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rules a reminder fires by, tested on the JVM.
 *
 * Every case here is a decision that would otherwise be discovered on a phone: a fire time inside the night
 * window, a day the parent switched off, an anchor so old the reminder is already overdue, twins whose feeds
 * are half an hour apart. The scheduling layer below only asks this file "when?", so if these are right the
 * alarm is right.
 */
class RemindersTest {

    private val zone: ZoneId = ZoneId.of("America/Port_of_Spain")
    private val night = NightWindow(startHour = 18, endHour = 6)

    private fun at(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun local(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString()

    private fun afterLast(
        intervalMinutes: Int = 180,
        skipNight: Boolean = false,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        enabled: Boolean = true,
    ) = ReminderConfig(
        enabled = enabled,
        mode = ReminderMode.AFTER_LAST,
        intervalMinutes = intervalMinutes,
        skipNight = skipNight,
        days = days,
    )

    // ---- the four nulls ---------------------------------------------------

    @Test
    fun `a disabled rule never fires`() {
        val config = afterLast(enabled = false)
        assertNull(nextReminderAt(config, at("2026-10-24", "09:00"), at("2026-10-24", "12:30"), zone, night))
    }

    @Test
    fun `an activity that child has never had logged has no anchor to measure from`() {
        assertNull(nextReminderAt(afterLast(), anchorMs = null, now = at("2026-10-24", "12:00"), zone = zone, night = night))
    }

    @Test
    fun `an overdue reminder rings a minute later rather than instantly`() {
        // This used to return null - "being in the app IS the reminder" - which sounded tidy and meant a reminder
        // stopped reminding entirely once its time had passed. It is due; it rings a minute from now, once, and the
        // next log re-anchors it.
        val anchor = at("2026-10-24", "08:00")
        val now = at("2026-10-24", "20:00")

        val next = nextReminderAt(afterLast(), anchor, now, zone, night)

        assertEquals(now + OVERDUE_RING_DELAY_MS, next)
    }

    @Test
    fun `an interval that lands on an unselected day is not fired`() {
        // Saturday 22:00 + 3h is Sunday 01:00; Sunday is off.
        val anchor = at("2026-10-24", "22:00")
        val now = at("2026-10-24", "22:05")
        val weekdaysOnly = DayOfWeek.entries.toSet() - DayOfWeek.SUNDAY
        assertNull(nextReminderAt(afterLast(days = weekdaysOnly), anchor, now, zone, night))
    }

    // ---- AFTER_LAST -------------------------------------------------------

    @Test
    fun `three hours after the last feed is the next reminder`() {
        val anchor = at("2026-10-24", "09:00")
        val next = nextReminderAt(afterLast(), anchor, at("2026-10-24", "09:30"), zone, night)
        assertEquals("2026-10-24T12:00", local(next!!))
    }

    @Test
    fun `each child is anchored on its own last log`() {
        // The point of a per-child reminder: First Twin's 09:00 feed must not reset Second Twin's 08:00 one.
        val first = nextReminderAt(afterLast(), at("2026-10-24", "09:00"), at("2026-10-24", "09:10"), zone, night)
        val second = nextReminderAt(afterLast(), at("2026-10-24", "08:00"), at("2026-10-24", "09:10"), zone, night)
        assertEquals("2026-10-24T12:00", local(first!!))
        assertEquals("2026-10-24T11:00", local(second!!))
    }

    @Test
    fun `the interval presets include the awkward half hour`() {
        val anchor = at("2026-10-24", "09:00")
        val next = nextReminderAt(afterLast(intervalMinutes = 90), anchor, at("2026-10-24", "09:30"), zone, night)
        assertEquals("2026-10-24T10:30", local(next!!))
    }

    // ---- the night window -------------------------------------------------

    @Test
    fun `a fire time inside the night window waits for the window to end`() {
        // 20:00 + 3h = 23:00, which is inside 18:00-06:00, so it moves to 06:00 tomorrow.
        val anchor = at("2026-10-24", "20:00")
        val next = nextReminderAt(afterLast(skipNight = true), anchor, at("2026-10-24", "20:30"), zone, night)
        assertEquals("2026-10-25T06:00", local(next!!))
    }

    @Test
    fun `a fire time after the window starts but before midnight waits for tomorrow morning`() {
        val anchor = at("2026-10-24", "19:00")
        val next = nextReminderAt(afterLast(skipNight = true), anchor, at("2026-10-24", "19:30"), zone, night)
        assertEquals("2026-10-25T06:00", local(next!!))
    }

    @Test
    fun `a daytime fire time is left alone`() {
        val anchor = at("2026-10-24", "09:00")
        val next = nextReminderAt(afterLast(skipNight = true), anchor, at("2026-10-24", "09:30"), zone, night)
        assertEquals("2026-10-24T12:00", local(next!!))
    }

    @Test
    fun `without the switch the night is not skipped`() {
        val anchor = at("2026-10-24", "20:00")
        val next = nextReminderAt(afterLast(skipNight = false), anchor, at("2026-10-24", "20:30"), zone, night)
        assertEquals("2026-10-24T23:00", local(next!!))
    }

    @Test
    fun `a window with the same start and end means no night at all`() {
        // The Day & night screen allows 06:00-06:00 to mean "no night", and the reminder must read it the same
        // way Summary does rather than treating every hour as night.
        val noNight = NightWindow(startHour = 6, endHour = 6)
        val anchor = at("2026-10-24", "20:00")
        val next = nextReminderAt(afterLast(skipNight = true), anchor, at("2026-10-24", "20:30"), zone, noNight)
        assertEquals("2026-10-24T23:00", local(next!!))
    }

    // ---- FIXED_TIMES ------------------------------------------------------

    private fun fixedTimes(
        times: List<Int>,
        skipNight: Boolean = false,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        enabled: Boolean = true,
    ) = ReminderConfig(
        enabled = enabled,
        mode = ReminderMode.FIXED_TIMES,
        timesOfDay = times,
        skipNight = skipNight,
        days = days,
    )

    @Test
    fun `the next fixed time is the next one today`() {
        val config = fixedTimes(listOf(7 * 60, 10 * 60, 13 * 60))
        val next = nextReminderAt(config, anchorMs = null, now = at("2026-10-24", "08:30"), zone = zone, night = night)
        assertEquals("2026-10-24T10:00", local(next!!))
    }

    @Test
    fun `after the last time of day it rolls to tomorrow's first`() {
        val config = fixedTimes(listOf(7 * 60, 10 * 60, 13 * 60))
        val next = nextReminderAt(config, anchorMs = null, now = at("2026-10-24", "14:00"), zone = zone, night = night)
        assertEquals("2026-10-25T07:00", local(next!!))
    }

    @Test
    fun `a time equal to the current minute is not re-armed`() {
        // Otherwise the alarm fires, recomputes to the same instant, and loops.
        val config = fixedTimes(listOf(10 * 60, 13 * 60))
        val next = nextReminderAt(config, anchorMs = null, now = at("2026-10-24", "10:00"), zone = zone, night = night)
        assertEquals("2026-10-24T13:00", local(next!!))
    }

    @Test
    fun `fixed times walk forward to the next selected day`() {
        // Friday 24 Oct 2026, 14:00, Monday-only rule -> Monday 26 Oct 07:00. Filtering the result instead of
        // walking would have left the whole of Monday unarmed.
        val mondayOnly = setOf(DayOfWeek.MONDAY)
        val config = fixedTimes(listOf(7 * 60), days = mondayOnly)
        val next = nextReminderAt(config, null, at("2026-10-23", "14:00"), zone, night)
        assertEquals("2026-10-26T07:00", local(next!!))
    }

    @Test
    fun `a fixed time inside the night window moves to the window's end`() {
        val config = fixedTimes(listOf(2 * 60), skipNight = true)
        val next = nextReminderAt(config, null, at("2026-10-24", "21:00"), zone, night)
        assertEquals("2026-10-25T06:00", local(next!!))
    }

    @Test
    fun `fixed times need at least one time`() {
        assertNull(nextReminderAt(fixedTimes(emptyList()), null, at("2026-10-24", "10:00"), zone, night))
    }

    // ---- the card's reminder chips ----------------------------------------

    /** The chip's "now": 09:30, so 13:00 and 14:30 are future times rather than "due now". */
    private val chipNow = at("2026-10-25", "09:30")

    private fun arm(minutes: Int, at: String) = ReminderArm(minutes, this.at("2026-10-25", at))

    @Test
    fun `a chip says the interval and when it will ring`() {
        assertEquals(
            "Every 3h (1:00 PM)",
            reminderChipText(afterLast(), at("2026-10-25", "13:00"), zone, TimeFormat.TWELVE_HOUR, chipNow),
        )
        assertEquals(
            "Every 3h (13:00)",
            reminderChipText(afterLast(), at("2026-10-25", "13:00"), zone, TimeFormat.TWENTY_FOUR_HOUR, chipNow),
        )
    }

    @Test
    fun `a reminder that is already due says so instead of showing a past time`() {
        assertEquals(
            "Every 3h (due now)",
            reminderChipText(afterLast(), chipNow, zone, TimeFormat.TWELVE_HOUR, chipNow),
        )
    }

    @Test
    fun `a fixed-time chip says the time, not an interval`() {
        // `At 1:00 PM` rather than `Every 1:00 PM`: a fixed-time rule has no interval to name.
        val config = ReminderConfig(enabled = true, mode = ReminderMode.FIXED_TIMES, timesOfDay = listOf(13 * 60))

        assertEquals(
            "At 1:00 PM",
            reminderChipText(config, at("2026-10-25", "13:00"), zone, TimeFormat.TWELVE_HOUR, chipNow),
        )
    }

    @Test
    fun `a fixed time that has just come round reads as due`() {
        val config = ReminderConfig(enabled = true, mode = ReminderMode.FIXED_TIMES, timesOfDay = listOf(13 * 60))

        assertEquals(
            "Due now",
            reminderChipText(config, chipNow, zone, TimeFormat.TWELVE_HOUR, chipNow),
        )
    }

    @Test
    fun `an overdue interval rings a minute from now rather than vanishing`() {
        // The one case that matters most: nobody has logged this activity for hours. It used to return null - no
        // alarm and no chip - which is a reminder that quietly stops reminding.
        val config = ReminderConfig(enabled = true, intervalMinutes = 180)
        val now = at("2026-10-25", "12:00")

        val next = nextReminderAt(config, at("2026-10-25", "06:00"), now, zone, night)

        assertEquals(now + OVERDUE_RING_DELAY_MS, next)
    }

    @Test
    fun `a rule with no logged anchor rings once from when it was set`() {
        // Found on the phone: a Bottle feed reminder for a child with no bottles yet had no anchor, so it never
        // armed and the chip had no time to show. The rule's own timestamp is the fallback.
        val setAt = at("2026-10-25", "09:00")
        val config = ReminderConfig(enabled = true, intervalMinutes = 180, anchorAt = setAt)

        val next = nextReminderAt(config, anchorMs = null, now = at("2026-10-25", "09:30"), zone = zone, night = night)

        assertEquals("2026-10-25T12:00", local(next!!))
    }

    @Test
    fun `a logged anchor still wins over the rule's timestamp`() {
        val config = ReminderConfig(
            enabled = true,
            intervalMinutes = 180,
            anchorAt = at("2026-10-25", "06:00"),
        )

        val next = nextReminderAt(
            config,
            anchorMs = at("2026-10-25", "09:00"),
            now = at("2026-10-25", "09:30"),
            zone = zone,
            night = night,
        )

        assertEquals("2026-10-25T12:00", local(next!!))
    }

    @Test
    fun `a clock label follows the chosen time format`() {
        assertEquals("7:00 AM", clockLabel(7 * 60))
        assertEquals("07:00", clockLabel(7 * 60, TimeFormat.TWENTY_FOUR_HOUR))
        assertEquals("11:00 PM", clockLabel(23 * 60))
        assertEquals("23:00", clockLabel(23 * 60, TimeFormat.TWENTY_FOUR_HOUR))
        assertEquals("12:00 PM", clockLabel(12 * 60))
        assertEquals("12:00 AM", clockLabel(0))
    }

    @Test
    fun `interval labels read as a parent would say them`() {
        assertEquals("30m", intervalLabel(30))
        assertEquals("3h", intervalLabel(180))
        assertEquals("1h 30m", intervalLabel(90))
    }

    @Test
    fun `only the four loggable activities can carry a reminder`() {
        assertEquals(
            listOf(EventType.BOTTLE, EventType.SOLIDS, EventType.DIAPER, EventType.HEALTH),
            REMINDER_TYPES,
        )
    }

    // ---- the Activity screen's "Upcoming reminders" card ------------------

    private val first = Child(
        id = "first-twin", name = "First Twin", birthDate = LocalDate.of(2026, 1, 1),
        sex = Sex.GIRL, gestationalAgeDays = 210, useAdjustedAge = true, sortOrder = 0,
    )
    private val second = first.copy(id = "second-twin", name = "Second Twin", sortOrder = 1)

    private fun upcoming(
        configs: Map<ReminderKey, ReminderConfig>,
        anchors: Map<Pair<EventType, String>, Long> = emptyMap(),
        at: String = "2026-10-24T09:30",
        children: List<Child> = listOf(first, second),
    ) = upcomingReminders(
        configs = configs,
        children = children,
        anchorFor = { type, childId -> anchors[type to childId] },
        now = java.time.LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli(),
        zone = zone,
        night = night,
    )

    private fun every3h() = ReminderConfig(enabled = true, intervalMinutes = 180)

    @Test
    fun `nothing armed means no card at all`() {
        assertEquals(emptyList<UpcomingReminder>(), upcoming(emptyMap()))
        assertEquals(emptyList<UpcomingReminder>(), upcoming(mapOf(ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig())))
    }

    @Test
    fun `twins on the same rule are one row`() {
        val rows = upcoming(
            configs = mapOf(
                ReminderKey(EventType.BOTTLE, first.id) to every3h(),
                ReminderKey(EventType.BOTTLE, second.id) to every3h(),
            ),
            anchors = mapOf(
                (EventType.BOTTLE to first.id) to at("2026-10-24", "09:00"),
                (EventType.BOTTLE to second.id) to at("2026-10-24", "09:00"),
            ),
        )

        assertEquals(1, rows.size)
        assertEquals(EventType.BOTTLE, rows.single().type)
        // No child named: "a feed is due at 12:00" is one fact, not two.
        assertNull(rows.single().childName)
        assertEquals("2026-10-24T12:00", local(rows.single().at))
    }

    @Test
    fun `twins on different rules are one row each, and each says whose`() {
        val rows = upcoming(
            configs = mapOf(
                ReminderKey(EventType.BOTTLE, first.id) to every3h(),
                ReminderKey(EventType.BOTTLE, second.id) to every3h(),
            ),
            anchors = mapOf(
                (EventType.BOTTLE to first.id) to at("2026-10-24", "09:00"),
                (EventType.BOTTLE to second.id) to at("2026-10-24", "08:00"),
            ),
        )

        assertEquals(listOf("2026-10-24T11:00", "2026-10-24T12:00"), rows.map { local(it.at) })
        assertEquals(listOf("Second Twin", "First Twin"), rows.map { it.childName })
    }

    @Test
    fun `a rule only one child has names that child`() {
        // Found on the phone: with a Solids rule for First Twin alone, the card read a bare "Solids", which
        // reads as "both children will be reminded" when only one will.
        val rows = upcoming(
            configs = mapOf(ReminderKey(EventType.SOLIDS, first.id) to every3h()),
            anchors = mapOf((EventType.SOLIDS to first.id) to at("2026-10-24", "09:00")),
        )

        assertEquals(1, rows.size)
        assertEquals("First Twin", rows.single().childName)
        assertEquals(first.id, rows.single().childId)
    }

    @Test
    fun `rows are soonest first across activities`() {
        val rows = upcoming(
            configs = mapOf(
                ReminderKey(EventType.DIAPER, first.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf(13 * 60),
                ),
                ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf(10 * 60),
                ),
                ReminderKey(EventType.SOLIDS, first.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf(12 * 60),
                ),
            ),
            children = listOf(first),
        )

        assertEquals(
            listOf(EventType.BOTTLE, EventType.SOLIDS, EventType.DIAPER),
            rows.map { it.type },
        )
    }

    @Test
    fun `the card stops at five rows rather than hiding one silently`() {
        // Five is the cap, and a sixth reminder means the card is full rather than the app keeping a secret.
        val configs = REMINDER_TYPES.flatMap { type ->
            listOf(
                ReminderKey(type, first.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf((10 + REMINDER_TYPES.indexOf(type)) * 60),
                ),
                ReminderKey(type, second.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf((11 + REMINDER_TYPES.indexOf(type)) * 60),
                ),
            )
        }.toMap()

        val rows = upcoming(configs = configs)

        assertEquals(DEFAULT_UPCOMING_LIMIT, rows.size)
    }

    @Test
    fun `growth and sleep never appear on the card`() {
        // They have no reminder rows in Settings, so they cannot have one here either.
        val rows = upcoming(
            configs = mapOf(
                ReminderKey(EventType.GROWTH, first.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf(10 * 60),
                ),
                ReminderKey(EventType.SLEEP, first.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf(10 * 60),
                ),
            ),
            children = listOf(first),
        )

        assertEquals(emptyList<UpcomingReminder>(), rows)
    }

}
