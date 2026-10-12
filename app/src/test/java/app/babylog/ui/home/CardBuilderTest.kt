package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBuilderTest {

    private val t0 = 1_760_000_000_000L

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
        assertTrue(cards.all { it.last == null && it.recent.isEmpty() && it.running == null })
    }

    @Test
    fun `an empty card is not an absent card`() {
        // The screen renders six cards on a fresh install; it must not render an empty state instead.
        assertEquals(6, buildCards(emptyList()).size)
    }

    @Test
    fun `last is the newest of that type and recent is capped newest first`() {
        val events = (0 until 5).map { i ->
            event("b$i", EventType.BOTTLE, at = t0 + i * 1000L)
        }.sortedByDescending { it.startedAt } // as the DAO returns them

        val bottle = buildCards(events).first { it.type == EventType.BOTTLE }
        assertEquals("b4", bottle.last?.id)
        assertEquals(listOf("b4", "b3", "b2"), bottle.recent.map { it.id })
        assertEquals(CARD_RECENT_LIMIT, bottle.recent.size)
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

    private fun event(id: String, type: EventType, at: Long, endedAt: Long? = null) = BabyEvent(
        id = id,
        childId = "first-twin",
        type = type,
        startedAt = at,
        endedAt = endedAt,
        createdBy = "u1",
        updatedAt = at,
    )
}
