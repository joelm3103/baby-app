package app.babylog.ui.history

import app.babylog.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Summary filter row's single bulk chip. It is the only control in the app whose MEANING depends on
 * the state it is already in, so the rule is pinned here rather than left to an `if` inside a composable
 * that no JVM test can reach.
 */
class HistoryFilterTest {

    @Test
    fun `everything shown offers None`() {
        val action = bulkFilterAction(hidden = emptySet())
        assertEquals("None", action.label)
        assertFalse(action.showAll)
    }

    @Test
    fun `a partial selection offers All, not None`() {
        // The rule that would be tempting to get wrong: one chip off is not "nothing is filtered", and
        // from here "show every activity" has to stay one tap away.
        val action = bulkFilterAction(hidden = setOf(EventType.DIAPER))
        assertEquals("All", action.label)
        assertTrue(action.showAll)
    }

    @Test
    fun `everything hidden offers All`() {
        val action = bulkFilterAction(hidden = EventType.entries.toSet())
        assertEquals("All", action.label)
        assertTrue(action.showAll)
    }
}
