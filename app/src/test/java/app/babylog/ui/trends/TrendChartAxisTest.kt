package app.babylog.ui.trends

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The x axis, which is the one part of the trend chart that has no equivalent in Growth's.
 *
 * A colliding axis is a layout failure a green build cannot see: the labels are drawn on a Canvas, so
 * nothing measures them for overlap. The rule is therefore pinned here — never more than six labels
 * across the plot, and the newest day is always named, because "which day is that" is the first
 * question a chart answers.
 */
class TrendChartAxisTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    @Test
    fun `every day is labelled at a week and the labels thin out from there`() {
        assertEquals(1, labelStep(7))
        assertEquals(2, labelStep(14))
        assertEquals(5, labelStep(30))
    }

    @Test
    fun `date labels are evenly spaced, never crowded, and always name the newest day`() {
        // Spacing is the invariant, not a label count. Appending the newest day on top of a step of two
        // put two labels one day apart on a fortnight — 21dp apart for a ~33dp label — and they
        // overlapped, which is what this pins.
        listOf(7, 14, 30).forEach { days ->
            val labels = labelIndices(days)

            assertEquals("the newest day is always named", listOf(days - 1), labels.takeLast(1))
            assertEquals("oldest first", labels, labels.sorted())
            assertEquals("no duplicate days", labels.size, labels.distinct().size)
            assertTrue("${labels.size} labels on $days days", labels.size <= 7)
            assertTrue("a label must be inside the chart", labels.first() >= 0 && labels.last() < days)
            val gaps = labels.zipWithNext { a, b -> b - a }
            assertTrue(
                "$days days drew gaps of $gaps",
                gaps.all { it == labelStep(days) },
            )
        }

        // A week is the one range that names every single day.
        assertEquals((0..6).toList(), labelIndices(7))
    }

    @Test
    fun `a count axis ticks in whole things`() {
        // Found on the phone: one solids meal in a 14-day window gave the axis a 0.2 step, so it read
        // "0.4" above a bar of one meal.
        assertEquals(listOf(0.0, 1.0), integerTicks(0f..1.08f))
        assertTrue("no fractional tick", integerTicks(0f..1.08f).all { it == Math.floor(it) })

        // A wider count keeps the round step it can have, so the axis still reads as a scale.
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0), integerTicks(0f..9.72f))
        assertTrue("at least two gridlines", integerTicks(0f..9.72f).size >= 2)
        assertTrue("at least two gridlines on a flat zero", integerTicks(0f..0.1f).size >= 2)
        assertEquals(listOf(0.0, 1.0), integerTicks(0f..0.1f))
    }

    @Test
    fun `the second child's line is dashed, so the twins are told apart by shape as well as shade`() {
        // Two shades of one hue were not enough on the phone, and the design contract does not allow a
        // second hue for the second child. Same reasoning as the fever marker: a shape, not a colour.
        assertNull(linePathEffect(0))
        assertNotNull("the second child must not draw the same line", linePathEffect(1))
        assertNotNull(linePathEffect(2))
    }

    @Test
    fun `a day is labelled in the display zone, not UTC`() {
        // 00:30 on the 18th in Tokyo is 15:30 on the 17th UTC. Labelling from UTC would print the 17th
        // above a bar the parent logged on the 18th.
        val dayStart = LocalDate.of(2026, 10, 18).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals("18 Oct", dayLabel(dayStart, zone))
        assertEquals("17 Oct", dayLabel(dayStart, ZoneId.of("UTC")))
    }
}
