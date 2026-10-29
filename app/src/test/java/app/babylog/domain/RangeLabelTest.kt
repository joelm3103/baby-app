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
        // A month here is the 30-day window this chip draws, not a calendar month; `1Y` is 52 whole weeks
        // and `All` has no fixed width at all, which is why its `days` is null rather than a guess.
        assertEquals(
            listOf("1W" to 7, "2W" to 14, "1M" to 30, "1Y" to 364, "All" to null),
            TrendRange.entries.map { it.label to it.days },
        )
    }

    @Test
    fun eachRangeNamesTheBucketItDraws() {
        // The caption under a chart counts buckets. If the label and the bucket disagreed, the screen would
        // describe a different chart from the one it drew -- "52 weeks" over day buckets, say.
        assertEquals(
            listOf(
                "1W" to TrendBucket.DAY,
                "2W" to TrendBucket.DAY,
                "1M" to TrendBucket.DAY,
                "1Y" to TrendBucket.WEEK,
                "All" to TrendBucket.MONTH,
            ),
            TrendRange.entries.map { it.label to it.bucket },
        )
    }

    @Test
    fun theTwoRowsAreNotTheSameRow() {
        assertEquals(
            "Summary offers today; a chart of today is one point",
            setOf(1, 7, 14),
            HistoryRange.entries.map { it.days }.toSet(),
        )
        assertEquals(setOf(7, 14, 30, 364, null), TrendRange.entries.map { it.days }.toSet())
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
        // Five chips on a 411dp phone: three characters each is the budget that wraps to one row.
        val labels = HistoryRange.entries.map { it.label } + TrendRange.entries.map { it.label }
        labels.forEach {
            assertTrue("`$it` is too long for a range chip", it.length <= 3)
        }
    }
}
