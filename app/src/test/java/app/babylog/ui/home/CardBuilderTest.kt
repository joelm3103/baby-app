package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBuilderTest {

    private val t0 = 1_760_000_000_000L

    // A zone far from UTC (UTC+9), so "today" is not UTC's day. Same convention as TotalsTest: the day
    // boundary is the whole reason this filtering is Kotlin and not SQL.
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = at(23, 0)

    @Test
    fun `there are always six cards in a fixed order, even with no data`() {
        val cards = buildCards(emptyList())
        assertEquals(6, cards.size)
        assertEquals(
            listOf(
                EventType.BOTTLE, EventType.DIAPER, EventType.SOLIDS,
                EventType.GROWTH, EventType.HEALTH, EventType.SLEEP,
            ),
            cards.map { it.type },
        )
        // Sleep is last AND present - it was explicitly requested despite being rarely used.
        assertEquals(EventType.SLEEP, cards.last().type)
        assertTrue(cards.all { it.last == null && it.entries.isEmpty() && it.running == null })
    }

    @Test
    fun `an empty card is not an absent card`() {
        // The screen renders six cards on a fresh install; it must not render an empty state instead.
        assertEquals(6, buildCards(emptyList()).size)
    }

    @Test
    fun `last is the newest of that type and entries keeps the whole history, newest first`() {
        val events = (0 until 8).map { i ->
            event("b$i", EventType.BOTTLE, at = t0 + i * 1000L)
        }.sortedByDescending { it.startedAt } // as the DAO returns them

        val bottle = buildCards(events).first { it.type == EventType.BOTTLE }
        assertEquals("b7", bottle.last?.id)
        // The full list, not a capped slice: the card draws today's window of it and "See all" draws
        // the rest, and both read from this one list.
        assertEquals((7 downTo 0).map { "b$it" }, bottle.entries.map { it.id })
    }

    @Test
    fun `cards do not leak events between types`() {
        val events = listOf(
            event("bottle", EventType.BOTTLE, at = t0 + 5000),
            event("diaper", EventType.DIAPER, at = t0 + 4000),
        )
        val cards = buildCards(events)
        assertEquals("bottle", cards.first { it.type == EventType.BOTTLE }.last?.id)
        assertEquals("diaper", cards.first { it.type == EventType.DIAPER }.last?.id)
        assertNull(cards.first { it.type == EventType.SOLIDS }.last)
        assertTrue(cards.first { it.type == EventType.SOLIDS }.entries.isEmpty())
    }

    @Test
    fun `input order does not change the result`() {
        // The DAO happens to return newest-first; buildCards must not depend on that. This test
        // deliberately feeds the older event first.
        val newestFirst = listOf(
            event("new", EventType.BOTTLE, at = t0 + 1000),
            event("old", EventType.BOTTLE, at = t0),
        )
        assertEquals(
            buildCards(newestFirst).map { it.last?.id },
            buildCards(newestFirst.reversed()).map { it.last?.id },
        )
        assertEquals("new", buildCards(newestFirst.reversed()).first { it.type == EventType.BOTTLE }.last?.id)
    }

    @Test
    fun `a Both-mode pair sharing a timestamp keeps the configured child order either way round`() {
        // One tap in Both mode writes two rows with an identical startedAt. Without the tie-break the
        // two rows can swap places, which looks like the app editing the log on its own.
        val first-twin = event("first-twin-row", EventType.BOTTLE, at = t0, child = "first-twin")
        val second-twin = event("second-twin-row", EventType.BOTTLE, at = t0, child = "second-twin")
        val children = listOf("first-twin", "second-twin")

        val once = buildCards(listOf(first-twin, second-twin), children).first { it.type == EventType.BOTTLE }
        val again = buildCards(listOf(second-twin, first-twin), children).first { it.type == EventType.BOTTLE }

        assertEquals(listOf("first-twin-row", "second-twin-row"), once.entries.map { it.id })
        assertEquals(once.entries.map { it.id }, again.entries.map { it.id })
    }

    @Test
    fun `a running sleep timer is surfaced so the card can show a stop button`() {
        val events = listOf(
            // deliberately NOT in newest-first order, to prove the sort happens inside
            event("finished", EventType.SLEEP, at = t0, endedAt = t0 + 60_000),
            event("running", EventType.SLEEP, at = t0 + 1000, endedAt = null),
        )
        val sleep = buildCards(events).first { it.type == EventType.SLEEP }
        assertEquals("running", sleep.running?.id)
        assertEquals("running", sleep.last?.id)
    }

    @Test
    fun `no card reports running when nothing is running`() {
        val events = listOf(event("done", EventType.SLEEP, at = t0, endedAt = t0 + 1000))
        assertTrue(buildCards(events).all { it.running == null })
    }

    // ---- the card's row list -------------------------------------------------

    @Test
    fun `rows are today's entries, newest first, and include the entry the big number shows`() {
        // The bug in the field: the headline swallowed the newest feed and the rows skipped it.
        val entries = listOf(
            event("15:22", EventType.BOTTLE, at = at(15, 22)),
            event("14:14", EventType.BOTTLE, at = at(14, 14)),
            event("13:09", EventType.BOTTLE, at = at(13, 9)),
            event("yesterday", EventType.BOTTLE, at = at(23, 30, dayOffset = -1)),
        ).sortedNewest()

        val rows = cardRows(entries, now, zone)

        assertEquals(listOf("15:22", "14:14", "13:09"), rows.shown.map { it.id })
    }

    @Test
    fun `four entries today and one yesterday shows the four, not five`() {
        val entries = listOf(
            event("a", EventType.BOTTLE, at = at(9, 0)),
            event("b", EventType.BOTTLE, at = at(12, 0)),
            event("c", EventType.BOTTLE, at = at(15, 0)),
            event("d", EventType.BOTTLE, at = at(18, 0)),
            event("yesterday", EventType.BOTTLE, at = at(20, 0, dayOffset = -1)),
        ).sortedNewest()

        val rows = cardRows(entries, now, zone)

        assertEquals(listOf("d", "c", "b", "a"), rows.shown.map { it.id })
        // Yesterday's feed is reachable - but only through See all.
        assertTrue(rows.hasMore)
    }

    @Test
    fun `today is capped at five rows and yesterday never pads the count`() {
        val todayEntries = (0 until 7).map { i ->
            event("today-$i", EventType.BOTTLE, at = at(8 + i, 0))
        }
        val entries = (todayEntries + event("yesterday", EventType.BOTTLE, at = at(22, 0, dayOffset = -1)))
            .sortedNewest()

        val rows = cardRows(entries, now, zone)

        assertEquals(5, rows.shown.size)
        assertEquals(listOf("today-6", "today-5", "today-4", "today-3", "today-2"), rows.shown.map { it.id })
        assertTrue(rows.shown.none { it.id == "yesterday" })
        assertTrue(rows.hasMore)
    }

    @Test
    fun `everything on the card still offers See all`() {
        val entries = listOf(
            event("a", EventType.BOTTLE, at = at(9, 0)),
            event("b", EventType.BOTTLE, at = at(12, 0)),
            event("c", EventType.BOTTLE, at = at(15, 0)),
        ).sortedNewest()

        val rows = cardRows(entries, now, zone)

        assertEquals(3, rows.shown.size)
        assertFalse("nothing is off-card", rows.hasMore)
        // The owner's rule: the link is the route to the full list of that activity, so it is there
        // whenever the activity has an entry - not only when a row is missing from the card.
        assertTrue(showsFooter(entries.size))
    }

    @Test
    fun `the Growth card offers the chart as soon as there is a measurement`() {
        // The defect this pins, found on the S23 after saving the first 5.4 kg: the footer rule was
        // "an entry is not already on the card", so with both rows visible there was NO link, and the
        // measurement history could not be opened at all until a row aged out of today's list.
        // The owner's rule now: one entry is enough, for every card.
        val entries = listOf(
            event("first-twin", EventType.GROWTH, at = at(11, 9)),
            event("second-twin", EventType.GROWTH, at = at(11, 9)),
        ).sortedNewest()

        val rows = cardRows(entries, now, zone)

        assertEquals(2, rows.shown.size)
        assertFalse("both rows are on the card, so the old list rule said no", rows.hasMore)
        assertTrue("one entry is enough for every card now", showsFooter(entries.size))
    }

    @Test
    fun `the Growth card with no measurements has nothing to list`() {
        val rows = cardRows(emptyList(), now, zone)
        assertFalse(showsFooter(0))
        assertTrue(rows.shown.isEmpty())
    }

    @Test
    fun `every card shows See all as soon as it has one entry`() {
        // One rule for all six activities, because the link is the route to the full list of that
        // activity and a card with data always has one.
        val todayOnly = listOf(event("a", EventType.DIAPER, at = at(9, 0))).sortedNewest()

        assertTrue(showsFooter(todayOnly.size))
        assertTrue(showsFooter(3))
        assertFalse(showsFooter(0))
    }

    @Test
    fun `a card whose only entries are older shows no rows but still offers See all`() {
        val entries = listOf(
            event("yesterday", EventType.BOTTLE, at = at(20, 0, dayOffset = -1)),
            event("last-week", EventType.BOTTLE, at = at(20, 0, dayOffset = -7)),
        ).sortedNewest()

        val rows = cardRows(entries, now, zone)

        assertTrue(rows.shown.isEmpty())
        assertTrue(rows.hasMore)
    }

    @Test
    fun `an entry just before local midnight is yesterday, not today`() {
        // 23:59 local is 14:59 UTC on the same date; a UTC day boundary would file it as today.
        val justBeforeMidnight = event("late", EventType.BOTTLE, at = at(23, 59, dayOffset = -1))
        val justAfterMidnight = event("early", EventType.BOTTLE, at = at(0, 1))

        val rows = cardRows(listOf(justBeforeMidnight, justAfterMidnight).sortedNewest(), now, zone)

        assertEquals(listOf("early"), rows.shown.map { it.id })
        assertTrue(rows.hasMore)
    }

    private fun List<BabyEvent>.sortedNewest() = sortedByDescending { it.startedAt }

    private fun at(hour: Int, minute: Int, dayOffset: Long = 0): Long =
        today.plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun event(
        id: String,
        type: EventType,
        at: Long,
        endedAt: Long? = null,
        child: String = "first-twin",
    ) = BabyEvent(
        id = id,
        childId = child,
        type = type,
        startedAt = at,
        endedAt = endedAt,
        createdBy = "u1",
        updatedAt = at,
    )
}
