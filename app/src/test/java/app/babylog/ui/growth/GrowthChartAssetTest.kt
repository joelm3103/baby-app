package app.babylog.ui.growth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.growth.GrowthReferenceParser
import app.babylog.domain.BabyEvent
import app.babylog.domain.Child
import app.babylog.domain.EventType
import app.babylog.domain.LengthUnit
import app.babylog.domain.Sex
import app.babylog.domain.UnitPreferences
import app.babylog.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The real asset driving the real chart.
 *
 * `GrowthReferenceTest` parses the asset and `GrowthChartTest` exercises the rules against a synthetic
 * reference, so neither would notice if the two did not fit together — a measure key spelled
 * differently in the asset, a sex key the chart does not look up, an axis read as the wrong unit. That
 * failure mode is an empty chart on a phone, which is precisely what a green build cannot catch. This
 * is the seam, tested with the shipped bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrowthChartAssetTest {

    private val birth = LocalDate.of(2025, 12, 31)
    private val zone = ZoneOffset.UTC
    private val metric = UnitPreferences(length = LengthUnit.CENTIMETRES, weight = WeightUnit.KILOGRAMS)

    // the children: 30 weeks at birth, so 70 days premature.
    private val first-twin = Child(
        id = "first-twin",
        name = "First Twin",
        birthDate = birth,
        sex = Sex.BOY,
        gestationalAgeDays = 210,
    )
    private val second-twin = first-twin.copy(id = "second-twin", name = "Second Twin")

    private fun reference() = ApplicationProvider.getApplicationContext<Context>()
        .assets.open(GrowthReferenceParser.ASSET_PATH)
        .bufferedReader()
        .use { it.readText() }
        .let(GrowthReferenceParser::parse)

    private fun measurement(id: String, childId: String, on: LocalDate, grams: Int? = null, mm: Int? = null) =
        BabyEvent(
            id = id,
            childId = childId,
            type = EventType.GROWTH,
            startedAt = on.atStartOfDay(zone).toInstant().toEpochMilli(),
            weightGrams = grams,
            lengthMm = mm,
            createdBy = "owner",
            updatedAt = on.atStartOfDay(zone).toInstant().toEpochMilli(),
        )

    @Test
    fun `every metric draws seven real curves from the shipped asset`() {
        val reference = reference()
        for (metricChoice in GrowthMetric.entries) {
            val chart = growthChart(listOf(first-twin), emptyList(), reference, metricChoice, metric, zone)
            assertEquals("$metricChoice curves", listOf(2, 10, 25, 50, 75, 90, 98), chart.curves.map { it.percentile })
            assertTrue("$metricChoice has too few points", chart.curves.all { it.points.size > 20 })
            assertTrue(
                "$metricChoice produced a non-positive value",
                chart.curves.all { curve -> curve.points.all { it.value.isFinite() && it.value > 0.0 } },
            )
            // Growing children: every curve rises across the plotted range.
            assertTrue(
                "$metricChoice curve is not monotonic",
                chart.curves.all { curve -> curve.points.last().value > curve.points.first().value },
            )
            // ...and the bundle is ordered, which is what makes it readable as a chart.
            val atYear = chart.curves.map { curve -> curve.points.first { it.axis >= 365.0 }.value }
            assertEquals(atYear.sorted(), atYear)
        }
    }

    @Test
    fun `a nine-month-old lands on a real curve`() {
        val reference = reference()
        // 2026-10-14 is 287 days after birth, and 217 adjusted days for a 70-day preemie.
        val measured = LocalDate.of(2026, 10, 14)
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(measurement("a", "first-twin", measured, grams = 8600, mm = 720)),
            reference = reference,
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        val marker = chart.markers.single()
        assertEquals(217.0, marker.axis, 1e-9)
        assertEquals(8.6, marker.value, 1e-9)
        assertNotNull("no percentile for a real measurement", marker.percentile)
        assertTrue("percentile ${marker.percentile} is not a percentile", marker.percentile!! in 1.0..99.0)
        assertEquals("WHO 2006 · v2026.10 · updated 15 Oct 2026", chart.identityLine)
        assertTrue(chart.hasData)
    }

    @Test
    fun `both children are plotted on one bundle`() {
        val reference = reference()
        val chart = growthChart(
            children = listOf(first-twin, second-twin),
            events = listOf(
                measurement("a", "first-twin", LocalDate.of(2026, 10, 14), grams = 8600),
                measurement("b", "second-twin", LocalDate.of(2026, 10, 14), grams = 8300),
            ),
            reference = reference,
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(2, chart.markers.size)
        assertEquals(setOf("K", "L"), chart.markers.map { it.badge }.toSet())
        assertTrue(chart.markers.all { it.percentile != null })
        assertEquals("WHO boys", chart.referenceLabel)
        // Two markers share an age, so their values must be ordered by value on the chart, not by luck.
        assertTrue(chart.markers.first { it.childId == "first-twin" }.value > chart.markers.first { it.childId == "second-twin" }.value)
    }

    @Test
    fun `the birth measurement has a value and no percentile`() {
        val reference = reference()
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(measurement("a", "first-twin", birth, grams = 1370, mm = 380)),
            reference = reference,
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        val marker = chart.markers.single()
        assertEquals(1.37, marker.value, 1e-9)
        org.junit.Assert.assertNull(
            "a 30-week birth measurement must not be given a term percentile",
            marker.percentile,
        )
        assertEquals(PRETERM_NOTE, chart.noPercentileReason)
    }

    @Test
    fun `the chart's y-range covers the curves it drew`() {
        val reference = reference()
        val chart = growthChart(listOf(first-twin), emptyList(), reference, GrowthMetric.HEAD, metric, zone)
        val values = chart.curves.flatMap { curve -> curve.points.map { it.value } }
        val range = yRangeFor(values)
        assertTrue(range.start < values.min().toFloat())
        assertTrue(range.endInclusive > values.max().toFloat())
        assertTrue(yTicks(range).size >= 3)
    }
}
