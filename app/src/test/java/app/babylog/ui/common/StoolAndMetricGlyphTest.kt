package app.babylog.ui.common

import app.babylog.domain.DiaperConsistency
import app.babylog.ui.growth.GrowthMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two glyph sets that are `when`s over an enum.
 *
 * A `when` protects the branches that exist, not the ones that should: a consistency with no drawable,
 * or two consistencies sharing one, is a chip that lies about what it is — and neither mistake fails the
 * build or moves a number. The consistency set had no test at all until the `Mucousy` silhouette was read
 * as a letter `T` on the phone, which is exactly the kind of defect a mapping test cannot catch and an
 * is-it-mapped test can at least keep honest.
 */
class StoolAndMetricGlyphTest {

    @Test
    fun everyConsistencyHasItsOwnSilhouette() {
        val ids = DiaperConsistency.entries.map { it.stoolRes() }
        assertTrue("every consistency must resolve to a drawable", ids.none { it == 0 })
        assertEquals("two consistencies must never share one shape", ids.size, ids.toSet().size)
    }

    @Test
    fun everyGrowthMetricHasItsOwnMark() {
        val ids = GrowthMetric.entries.map { it.iconRes() }
        assertTrue("every metric must resolve to a drawable", ids.none { it == 0 })
        assertEquals("two metrics must never share one mark", ids.size, ids.toSet().size)
    }

    @Test
    fun theMetricMarksAreNotActivityGlyphs() {
        // The chips sit in the same row shape as the activity chips, so the tempting shortcut is to reuse
        // the Growth activity glyph on all three — which would tell a parent nothing about which one is on.
        val metricIds = GrowthMetric.entries.map { it.iconRes() }.toSet()
        val activityIds = app.babylog.domain.EventType.entries.map { it.glyphRes() }.toSet()
        assertTrue("metric marks must be their own assets", metricIds.intersect(activityIds).isEmpty())
    }
}
