package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ordering rules a Both-mode save depends on. Two rows written by one save share a timestamp, so
 * only the tie-break separates them — and it must not depend on the order the rows came back from the
 * database.
 */
class EventOrderTest {

    private val t0 = 1_760_000_000_000L
    private val children = listOf("first-twin", "second-twin")

    @Test
    fun `newest first, regardless of the input order`() {
        val events = listOf(bottle("old", "first-twin", t0), bottle("new", "first-twin", t0 + 1000))

        assertEquals(listOf("new", "old"), events.sortedNewestFirst(children).map { it.id })
        assertEquals(listOf("new", "old"), events.reversed().sortedNewestFirst(children).map { it.id })
    }

    @Test
    fun `identically timestamped rows keep the configured child order, whichever way they arrive`() {
        // Exactly what one Both-mode save writes: same instant, two children.
        val first-twin = bottle("first-twin-row", "first-twin", t0)
        val second-twin = bottle("second-twin-row", "second-twin", t0)

        val asDaoReturned = listOf(first-twin, second-twin)
        val asInserted = listOf(second-twin, first-twin)

        assertEquals(listOf("first-twin-row", "second-twin-row"), asDaoReturned.sortedNewestFirst(children).map { it.id })
        assertEquals(listOf("first-twin-row", "second-twin-row"), asInserted.sortedNewestFirst(children).map { it.id })
    }

    @Test
    fun `a child missing from the configured order still sorts deterministically`() {
        // An archived child's old rows can still come back from the DAO.
        val archived = bottle("archived", "zoe", t0)
        val known = bottle("known", "second-twin", t0)

        val once = listOf(archived, known).sortedNewestFirst(children).map { it.id }
        val again = listOf(known, archived).sortedNewestFirst(children).map { it.id }

        assertEquals(once, again)
        assertEquals(listOf("known", "archived"), once)
    }

    @Test
    fun `a different child order is honoured`() {
        val first-twin = bottle("first-twin-row", "first-twin", t0)
        val second-twin = bottle("second-twin-row", "second-twin", t0)

        assertEquals(
            listOf("second-twin-row", "first-twin-row"),
            listOf(first-twin, second-twin).sortedNewestFirst(listOf("second-twin", "first-twin")).map { it.id },
        )
    }

    @Test
    fun `timestamps still win over the child order`() {
        val laterSecond = bottle("later-second", "second-twin", t0 + 5000)
        val earlierFirst = bottle("earlier-first", "first-twin", t0)

        assertEquals(
            listOf("later-second", "earlier-first"),
            listOf(earlierFirst, laterSecond).sortedNewestFirst(children).map { it.id },
        )
    }

    private fun bottle(id: String, childId: String, at: Long) = BabyEvent(
        id = id,
        childId = childId,
        type = EventType.BOTTLE,
        startedAt = at,
        createdBy = "u1",
        updatedAt = at,
    )
}
