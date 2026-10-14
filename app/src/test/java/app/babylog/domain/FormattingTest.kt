package app.babylog.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    private val n = 1_000_000_000_000L // arbitrary fixed "now"

    // --- relativeTime -------------------------------------------------------

    @Test
    fun `under a minute reads just now`() {
        assertEquals("just now", relativeTime(now = 10_000L, then = 10_000L))
        assertEquals("just now", relativeTime(now = 10_000L, then = 9_000L))
    }

    @Test
    fun `minutes are shown as m ago`() {
        assertEquals("7m ago", relativeTime(now = n, then = n - 7 * 60_000L))
    }

    @Test
    fun `hours and minutes together`() {
        assertEquals("2h 10m ago", relativeTime(now = n, then = n - (2 * 60 + 10) * 60_000L))
    }

    @Test
    fun `whole hours omit the minutes`() {
        assertEquals("3h ago", relativeTime(now = n, then = n - 3 * 3_600_000L))
    }

    @Test
    fun `a day or more is shown in days`() {
        assertEquals("2d ago", relativeTime(now = n, then = n - 2 * 86_400_000L))
    }

    @Test
    fun `a future timestamp does not produce a negative`() {
        assertEquals("just now", relativeTime(now = n, then = n + 60_000L))
    }

    // --- elapsed -----------------------------------------------------------

    @Test
    fun `elapsed includes hours when there are any`() {
        assertEquals("2h 12m 6s", elapsed(2 * 3_600_000L + 12 * 60_000L + 6_000L))
    }

    @Test
    fun `elapsed omits hours under an hour`() {
        assertEquals("12m 6s", elapsed(12 * 60_000L + 6_000L))
    }

    @Test
    fun `elapsed under a minute is seconds only`() {
        assertEquals("45s", elapsed(45_000L))
        assertEquals("5s", elapsed(5_000L))
    }

    // --- day labels and grouping -------------------------------------------

    @Test
    fun `timeOfDayOn keeps the local day and sets the wall clock time`() {
        val zone = ZoneId.of("Asia/Tokyo")
        // 23:30Z on 14 Nov is 08:30 on 15 Nov in Tokyo, so the day under test is the 15th.
        val original = java.time.Instant.parse("2023-11-14T23:30:00Z").toEpochMilli()

        val replaced = timeOfDayOn(original, hour = 3, minute = 15, zone = zone)

        val zdt = java.time.Instant.ofEpochMilli(replaced).atZone(zone)
        assertEquals(3, zdt.hour)
        assertEquals(15, zdt.minute)
        assertEquals(java.time.LocalDate.of(2023, 11, 15), zdt.toLocalDate())
        assertEquals("2023-11-14T18:15:00Z", java.time.Instant.ofEpochMilli(replaced).toString())
    }

    @Test
    fun `dateOn keeps the time of day and moves only the date`() {
        val zone = ZoneId.of("Asia/Tokyo")
        // 23:30Z on 14 Nov is 08:30 on 15 Nov in Tokyo, so the time to preserve is 08:30 local.
        val original = java.time.Instant.parse("2023-11-14T23:30:00Z").toEpochMilli()
        val target = java.time.LocalDate.of(2023, 11, 20)

        val moved = dateOn(original, target.toEpochDay(), zone)

        val zdt = java.time.Instant.ofEpochMilli(moved).atZone(zone)
        assertEquals(java.time.LocalDate.of(2023, 11, 20), zdt.toLocalDate())
        assertEquals(8, zdt.hour)
        assertEquals(30, zdt.minute)
        assertEquals("2023-11-19T23:30:00Z", java.time.Instant.ofEpochMilli(moved).toString())
    }

    @Test
    fun `a date change and a time change compose without fighting`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val original = java.time.Instant.parse("2023-11-14T23:30:00Z").toEpochMilli()

        // Date first, then time: the end state must be 03:15 on the 20th, local.
        val dated = dateOn(original, java.time.LocalDate.of(2023, 11, 20).toEpochDay(), zone)
        val timed = timeOfDayOn(dated, hour = 3, minute = 15, zone = zone)

        val zdt = java.time.Instant.ofEpochMilli(timed).atZone(zone)
        assertEquals(java.time.LocalDate.of(2023, 11, 20), zdt.toLocalDate())
        assertEquals(3, zdt.hour)
        assertEquals(15, zdt.minute)
    }

    @Test
    fun `day label names today and yesterday`() {
        val zone = ZoneId.of("UTC")
        // 2023-11-14T12:00:00Z was a Tuesday; two days earlier was Sunday 12 Nov.
        val today = java.time.Instant.parse("2023-11-14T12:00:00Z").toEpochMilli()
        val day = 86_400_000L
        assertEquals("Today", dayLabel(today, zone, now = today))
        assertEquals("Yesterday", dayLabel(today - day, zone, now = today))
        assertEquals("Sun 12 Nov", dayLabel(today - 2 * day, zone, now = today))
    }

    @Test
    fun `groups newest day first and newest event first within a day`() {
        val zone = ZoneId.of("UTC")
        val day = 86_400_000L
        val now = 1_700_000_000_000L
        val events = listOf(
            event(id = "old", at = now - 2 * day),
            event(id = "newest", at = now),
            event(id = "middle", at = now - 5 * 60_000L),
            event(id = "yesterday", at = now - day),
        )
        val groups = groupByDay(events, zone, now)
        assertEquals(3, groups.size)
        assertEquals(listOf("newest", "middle"), groups[0].events.map { it.id })
        assertEquals(listOf("yesterday"), groups[1].events.map { it.id })
        assertEquals(listOf("old"), groups[2].events.map { it.id })
    }

    @Test
    fun `deleted events are excluded from groups`() {
        val zone = ZoneId.of("UTC")
        val now = 1_700_000_000_000L
        val groups = groupByDay(
            listOf(event(id = "gone", at = now, deleted = true)),
            zone, now,
        )
        assertEquals(0, groups.size)
    }

    private fun event(id: String, at: Long, deleted: Boolean = false) = BabyEvent(
        id = id,
        childId = "c1",
        type = EventType.SLEEP,
        startedAt = at,
        createdBy = "u1",
        updatedAt = at,
        deleted = deleted,
    )
}
