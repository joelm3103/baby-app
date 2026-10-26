package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two range rows, and the windows behind them.
 *
 * The labels are three characters or fewer because they share a row with six filter chips, which is a
 * design decision — but a short label is only safe if it agrees with the window it selects. `2W` over a
 * 30-day chart would be wrong by two days, silently, on the one control whose job is to say what you are
 * looking at. So: every label is checked against its own `days`, and the two rows are checked to be
 * different, because they are different windows and the app must not pretend otherwise.
 */
class RangeLabelTest {

    @Test
    fun summaryLabelsMatchTheirWindows() {
        assertEquals(
            listOf("D" to 1, "1W" to 7, "2W" to 14),
            HistoryRange.entries.map { it.label to it.days },
        )
    }

    @Test
    fun trendsLabelsMatchTheirWindows() {
        // A month here is the 30-day window this chip draws, not a calendar month.
        assertEquals(
            listOf("1W" to 7, "2W" to 14, "1M" to 30),
            TrendRange.entries.map { it.label to it.days },
        )
    }

    @Test
    fun theTwoRowsAreNotTheSameRow() {
        assertEquals(
            "Summary offers today; a chart of today is one point",
            setOf(1, 7, 14),
            HistoryRange.entries.map { it.days }.toSet(),
        )
        assertEquals(setOf(7, 14, 30), TrendRange.entries.map { it.days }.toSet())
    }

    @Test
    fun everyShortLabelHasSomethingAPersonCanSay() {
        // `2W` read aloud is not "2 weeks". The chips carry this as their contentDescription.
        val rows = HistoryRange.entries.map { it.label to it.spoken } +
            TrendRange.entries.map { it.label to it.spoken }
        rows.forEach { (label, spoken) ->
            assertTrue("$label needs a spoken form longer than the abbreviation", spoken.length > label.length)
        }
    }

    @Test
    fun labelsAreShortEnoughForOneRow() {
        // Three chips on a 411dp phone: five characters each is the budget that fits without a scroll.
        val labels = HistoryRange.entries.map { it.label } + TrendRange.entries.map { it.label }
        labels.forEach {
            assertTrue("`$it` is too long for a range chip", it.length <= 3)
        }
    }
}
