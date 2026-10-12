package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isRunning` is what decides whether a card shows a live count-up timer. Getting it wrong put a
 * timer on all six dashboard cards, because a point event has no end time either.
 */
class EventStatusTest {

    private val t0 = 1_760_000_000_000L

    @Test
    fun `only sleep is an interval`() {
        assertEquals(
            listOf(EventType.SLEEP),
            EventType.entries.filter { it.hasDuration },
        )
        assertTrue(EventType.SLEEP.hasDuration)
    }

    @Test
    fun `a point event with no end time is not running`() {
        val types = EventType.entries.filter { it != EventType.SLEEP }
        for (type in types) {
            assertFalse("$type must never read as running", event(type, endedAt = null).isRunning)
        }
    }

    @Test
    fun `an unfinished sleep is running`() {
        assertTrue(event(EventType.SLEEP, endedAt = null).isRunning)
    }

    @Test
    fun `a finished sleep is not running`() {
        assertFalse(event(EventType.SLEEP, endedAt = t0 + 60_000).isRunning)
    }

    @Test
    fun `duration is only available once an end time exists`() {
        assertEquals(null, event(EventType.SLEEP, endedAt = null).durationMs)
        assertEquals(60_000L, event(EventType.SLEEP, endedAt = t0 + 60_000).durationMs)
    }

    private fun event(type: EventType, endedAt: Long?) = BabyEvent(
        id = "e", childId = "first-twin", type = type, startedAt = t0,
        endedAt = endedAt, createdBy = "test", updatedAt = t0,
    )
}
