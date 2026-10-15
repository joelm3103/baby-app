package app.babylog.ui.growth

import app.babylog.data.growth.GrowthAxis
import app.babylog.data.growth.GrowthMeasure
import app.babylog.data.growth.GrowthReference
import app.babylog.data.growth.GrowthSourceFile
import app.babylog.data.growth.LmsPoint
import app.babylog.domain.BabyEvent
import app.babylog.domain.Child
import app.babylog.domain.EventType
import app.babylog.domain.LengthUnit
import app.babylog.domain.Sex
import app.babylog.domain.UnitPreferences
import app.babylog.domain.WeightUnit
import app.babylog.domain.percentile
import app.babylog.domain.valueAtZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The chart's data layer, against a synthetic reference rather than the shipped asset.
 *
 * A fake is the point: this file is testing the RULES - which measurement gets a percentile, what the
 * axis means, what happens before term - and those must hold for any reference. The real asset's own
 * parameters are pinned in `GrowthReferenceTest`.
 *
 * The twins are the fixtures: a date set in Settings, 30 weeks, 70 days premature.
 */
class GrowthChartTest {

    private val birth = LocalDate.of(2025, 12, 31)
    private val zone = ZoneOffset.UTC

    private val first-twin = Child(
        id = "first-twin",
        name = "First Twin",
        birthDate = birth,
        sex = Sex.BOY,
        gestationalAgeDays = 210,
        useAdjustedAge = true,
    )
    private val second-twin = first-twin.copy(id = "second-twin", name = "Second Twin")

    private val metric = UnitPreferences(length = LengthUnit.CENTIMETRES, weight = WeightUnit.KILOGRAMS)
    private val imperial = UnitPreferences(length = LengthUnit.INCHES, weight = WeightUnit.POUNDS_OUNCES)

    /**
     * A reference that grows linearly: M is 3 kg at day 0, +1 kg per 100 days. L and S are held fixed,
     * so a value equal to M is the 50th percentile and the arithmetic is checkable by hand.
     */
    private fun reference(): GrowthReference {
        val rows = (0..2000).map { day ->
            LmsPoint(axis = day.toDouble(), l = 0.4, m = 3.0 + day / 100.0, s = 0.12)
        }
        val tables = GrowthMeasure.entries.associateWith { measure ->
            mapOf(
                Sex.BOY to rows.map { point ->
                    // Length and head are in cm like weight is in kg; the same shape keeps the fake simple.
                    point.copy(m = point.m * if (measure == GrowthMeasure.WEIGHT_FOR_AGE) 1.0 else 10.0)
                },
                Sex.GIRL to rows,
            )
        }
        return GrowthReference(
            dataset = "Test Growth Standard",
            version = "who-2006.2026.10",
            publishedAt = "2006-04-27",
            retrievedAt = "2026-10-15",
            citation = "test",
            license = "test",
            sourceFiles = GrowthMeasure.entries.map {
                GrowthSourceFile("${it.name}.txt", it, GrowthAxis.AGE_DAYS, 2001, "0".repeat(64), "https://example.test")
            },
            tables = tables,
        )
    }

    private fun growth(id: String, childId: String, on: LocalDate, grams: Int? = null, mm: Int? = null): BabyEvent =
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
    fun `the bundle is the seven published curves, drawn from the reference`() {
        val chart = growthChart(listOf(first-twin), emptyList(), reference(), GrowthMetric.WEIGHT, metric, zone)
        assertEquals(listOf(2, 10, 25, 50, 75, 90, 98), chart.curves.map { it.percentile })
        assertTrue(chart.curves.all { it.points.size > 5 })
        // The median curve IS the reference: at day 0 it must equal M exactly.
        val median = chart.curves.single { it.percentile == 50 }
        assertEquals(3.0, median.points.first().value, 1e-9)
        assertEquals(0.0, median.points.first().axis, 1e-9)
    }

    @Test
    fun `a measurement equal to the reference median is the 50th percentile`() {
        // Day 100 with adjusted age: born 70 days early, so 2026-04-10 is 100 chronological days and
        // 30 adjusted days. The fake's M at day 30 is 3.3 kg.
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(growth("a", "first-twin", birth.plusDays(100), grams = 3300)),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        val marker = chart.markers.single()
        assertEquals(30.0, marker.axis, 1e-9)
        assertEquals(3.3, marker.value, 1e-9)
        assertEquals(50.0, marker.percentile!!, 1e-6)
        assertNull(marker.noPercentileReason)
        assertTrue(marker.wasAdjusted)
    }

    @Test
    fun `a measurement before term has no percentile and says why`() {
        // The birth measurement: taken at 30 weeks, which is 70 days before term. The WHO standards
        // describe babies born at term, so there is no percentile to read - and showing one would read
        // as a growth failure rather than as immaturity.
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(growth("a", "first-twin", birth, grams = 1370)),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        val marker = chart.markers.single()
        assertNull(marker.percentile)
        assertEquals(PRETERM_NOTE, marker.noPercentileReason)
        assertEquals(PRETERM_NOTE, chart.callouts.single().noPercentileReason)
        // It is still plotted and still listed: the value is real even when the percentile is not.
        assertEquals(1.37, marker.value, 1e-9)
    }

    @Test
    fun `a measurement past the end of the tables has no percentile either`() {
        // Adjusted age 2200 - 70 = 2130 days, past the fake's 0..2000 rows. The synthetic reference is
        // deliberately longer than the real one so this case is reachable with a plausible date.
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(growth("a", "first-twin", birth.plusDays(2200), grams = 20000)),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        val marker = chart.markers.single()
        assertNull(marker.percentile)
        assertEquals(OUT_OF_RANGE_NOTE, marker.noPercentileReason)
    }

    @Test
    fun `each measurement keeps its own child, badge and date`() {
        val chart = growthChart(
            children = listOf(first-twin, second-twin),
            events = listOf(
                growth("a", "first-twin", birth.plusDays(100), grams = 3300),
                growth("b", "second-twin", birth.plusDays(100), grams = 3300),
                growth("c", "second-twin", birth.plusDays(130), grams = 3600),
            ),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(3, chart.markers.size)
        // Newest first, and a Both-mode pair of the same date stays stable by child name.
        assertEquals(listOf("second-twin", "first-twin", "second-twin"), chart.markers.map { it.childId })
        assertEquals("K", chart.markers.first { it.childId == "first-twin" }.badge)
        assertNotNull(chart.markers.first { it.childId == "first-twin" }.percentile)
    }

    @Test
    fun `both children of the same sex share one curve bundle`() {
        val chart = growthChart(
            children = listOf(first-twin, second-twin),
            events = listOf(growth("a", "first-twin", birth.plusDays(100), grams = 3300)),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals("WHO boys", chart.referenceLabel)
        assertEquals(7, chart.curves.size)
    }

    @Test
    fun `the axis converts for display while the percentile stays metric`() {
        val event = growth("a", "first-twin", birth.plusDays(100), grams = 3300)
        val imperialChart = growthChart(
            children = listOf(first-twin),
            events = listOf(event),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = imperial,
            zone = zone,
        )
        val marker = imperialChart.markers.single()
        assertEquals(ChartUnit.POUNDS, imperialChart.unit)
        // 3.3 kg is 7.28 lb, and the percentile is computed from 3.3 kg all the same.
        assertEquals(3.3 * 1000.0 / 453.59237, marker.value, 1e-9)
        assertEquals(50.0, marker.percentile!!, 1e-6)

        val metricChart = growthChart(
            children = listOf(first-twin),
            events = listOf(event),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(3.3, metricChart.markers.single().value, 1e-9)
    }

    @Test
    fun `length and head read their own fields and units`() {
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(growth("a", "first-twin", birth.plusDays(100), mm = 520)),
            reference = reference(),
            metric = GrowthMetric.LENGTH,
            units = imperial,
            zone = zone,
        )
        val marker = chart.markers.single()
        assertEquals(ChartUnit.INCHES, chart.unit)
        assertEquals(520.0 / 25.4, marker.value, 1e-9)
        // A weight-only event contributes nothing to the length chart.
        assertTrue(
            growthChart(
                children = listOf(first-twin),
                events = listOf(growth("a", "first-twin", birth.plusDays(100), grams = 3300)),
                reference = reference(),
                metric = GrowthMetric.HEAD,
                units = metric,
                zone = zone,
            ).markers.isEmpty(),
        )
    }

    @Test
    fun `an empty log produces curves but no markers and no callout`() {
        val chart = growthChart(listOf(first-twin), emptyList(), reference(), GrowthMetric.WEIGHT, metric, zone)
        assertTrue(chart.markers.isEmpty())
        assertTrue(chart.callouts.isEmpty())
        assertTrue(!chart.hasData)
        assertEquals(7, chart.curves.size)
    }

    @Test
    fun `Both mode gets one callout per child, not one winner`() {
        // The owner's rule, matching the card's two columns: in Both mode a single highlight box
        // showed whichever child won a name-order tie on the same day. Each child now gets their own.
        val chart = growthChart(
            children = listOf(first-twin, second-twin),
            events = listOf(
                // Second Twin's is the newer on the same date, and First Twin's the heavier: neither should decide the
                // other's box.
                growth("k", "first-twin", birth.plusDays(200), grams = 7000),
                growth("l", "second-twin", birth.plusDays(200), grams = 6600),
                growth("k-old", "first-twin", birth.plusDays(100), grams = 3300),
            ),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )

        assertEquals(listOf("first-twin", "second-twin"), chart.callouts.map { it.childId })
        // Each box is that child's OWN newest measurement.
        assertEquals(7.0, chart.callouts.single { it.childId == "first-twin" }.value, 1e-9)
        assertEquals(6.6, chart.callouts.single { it.childId == "second-twin" }.value, 1e-9)
    }

    @Test
    fun `one child in scope gets one callout`() {
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(growth("k", "first-twin", birth.plusDays(100), grams = 3300)),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(1, chart.callouts.size)
        assertEquals("first-twin", chart.callouts.single().childId)
    }

    @Test
    fun `a child with nothing for this metric gets no callout`() {
        // Second Twin has only a length measurement, so the WEIGHT chart must not invent a box for him.
        val chart = growthChart(
            children = listOf(first-twin, second-twin),
            events = listOf(
                growth("k", "first-twin", birth.plusDays(200), grams = 7000),
                growth("l", "second-twin", birth.plusDays(200), mm = 660),
            ),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(listOf("first-twin"), chart.callouts.map { it.childId })
        // Second Twin's length is not a WEIGHT marker at all, so he is neither plotted nor highlighted here.
        assertEquals(1, chart.markers.size)
        assertEquals("first-twin", chart.markers.single().childId)
    }

    @Test
    fun `the x-axis covers the child's own age or two years, whichever is longer`() {
        assertEquals(730.0, xAxisEndDays(0.0), 1e-9)
        assertEquals(730.0, xAxisEndDays(400.0), 1e-9)
        assertEquals(830.0, xAxisEndDays(800.0), 1e-9)
        // Never past the end of the tables.
        assertEquals(1856.0, xAxisEndDays(3000.0), 1e-9)
    }

    @Test
    fun `the y-range keeps a marker off the edge and the ticks are round numbers`() {
        val range = yRangeFor(listOf(3.0, 9.0))
        assertTrue("range $range", range.start < 3.0f && range.endInclusive > 9.0f)
        val ticks = yTicks(range)
        assertTrue("expected several ticks, got $ticks", ticks.size >= 3)
        assertEquals(ticks, ticks.sorted())

        // A single value, and a constant series, must not collapse the chart to a zero-height band.
        val flat = yRangeFor(listOf(4.0, 4.0, 4.0))
        assertTrue("range $flat", flat.endInclusive > flat.start)
        assertTrue(yRangeFor(emptyList()).endInclusive > yRangeFor(emptyList()).start)
    }

    @Test
    fun `the axis has a readable number of round gridlines at any span`() {
        // A head circumference spanning ~31-52 cm is the case that caught this: rounding the ideal step
        // UP to the next nice value gave a 10-unit step and only two gridlines.
        val head = yRangeFor(listOf(32.5, 52.0))
        val headTicks = yTicks(head)
        assertTrue("head ticks were $headTicks", headTicks.size in 3..6)
        assertEquals(headTicks, headTicks.sorted())

        for (values in listOf(listOf(3.0, 9.0), listOf(0.5, 0.9), listOf(40.0, 100.0), listOf(31.0, 32.0))) {
            val ticks = yTicks(yRangeFor(values))
            assertTrue("ticks for $values were $ticks", ticks.size in 1..6)
            // Every tick is a multiple of the step, so the labels read as a scale.
            if (ticks.size > 1) {
                val step = ticks[1] - ticks[0]
                assertTrue("uneven spacing in $ticks", ticks.zipWithNext().all { (a, b) -> abs((b - a) - step) < 1e-9 })
            }
        }
    }

    @Test
    fun `the axis labels read as ages`() {
        assertEquals("birth", monthLabel(0.0))
        assertEquals("1", monthLabel(30.4))
        assertEquals("12", monthLabel(365.0))
        assertEquals(30.4375, xLabelStep(400.0), 1e-9)
        assertEquals(91.3125, xLabelStep(730.0), 1e-9)
    }

    @Test
    fun `the chart agrees with the maths it is drawn from`() {
        // Ties the marker's percentile to the pure function, so a change in either shows up here rather
        // than as a marker sitting on the wrong curve.
        val reference = reference()
        val lms = reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 30.0)!!
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(growth("a", "first-twin", birth.plusDays(100), grams = 3300)),
            reference = reference,
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(percentile(3.3, lms.l, lms.m, lms.s)!!, chart.markers.single().percentile!!, 1e-9)
        // And the +2 SD curve is above the median at the same age.
        val median = chart.curves.single { it.percentile == 50 }
        val upper = chart.curves.single { it.percentile == 98 }
        val day = median.points[4].axis
        assertTrue(upper.points.first { it.axis == day }.value > median.points[4].value)
        assertEquals(valueAtZ(0.0, lms.l, lms.m, lms.s)!!, lms.m, 1e-9)
    }

    @Test
    fun `a child's own line runs oldest to newest, unlike the marker list`() {
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(
                growth("new", "first-twin", birth.plusDays(200), grams = 6000),
                growth("old", "first-twin", birth.plusDays(100), grams = 3300),
            ),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        // The list reads newest first; the LINE reads left to right.
        assertEquals(listOf(6.0, 3.3), chart.markers.map { it.value })
        val line = chart.trajectories.single()
        assertEquals(2, line.points.size)
        assertEquals(30.0, line.points.first().axis, 1e-9)
        assertEquals(130.0, line.points.last().axis, 1e-9)
        assertTrue(line.points.first().value < line.points.last().value)
    }

    @Test
    fun `the join into a pre-term point is dashed and the rest is not`() {
        // The birth measurement sits at x = 0, which is the DUE DATE. An unbroken line through it would
        // draw a slope that is not growth, so only that segment is dashed.
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(
                growth("birth", "first-twin", birth, grams = 1370),
                growth("three", "first-twin", birth.plusDays(100), grams = 3300),
                growth("six", "first-twin", birth.plusDays(200), grams = 6000),
            ),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        val points = chart.trajectories.single().points
        assertEquals(3, points.size)
        assertTrue("the birth point is pre-term", points.first().preTerm)
        assertTrue(!points[1].preTerm && !points[2].preTerm)
        assertTrue("the join into the birth point is dashed", segmentIsDashed(points[0], points[1]))
        assertTrue("growth after term is a solid line", !segmentIsDashed(points[1], points[2]))
    }

    @Test
    fun `one measurement is a dot, not a line`() {
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(growth("only", "first-twin", birth.plusDays(100), grams = 3300)),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(1, chart.markers.size)
        assertTrue("a single point has nothing to join", chart.trajectories.isEmpty())
    }

    @Test
    fun `Both mode gives each child their own line from their own measurements`() {
        val chart = growthChart(
            children = listOf(first-twin, second-twin),
            events = listOf(
                growth("k1", "first-twin", birth.plusDays(100), grams = 3300),
                growth("k2", "first-twin", birth.plusDays(200), grams = 6000),
                growth("l1", "second-twin", birth.plusDays(100), grams = 3100),
                growth("l2", "second-twin", birth.plusDays(200), grams = 5600),
            ),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(2, chart.trajectories.size)
        val firstLine = chart.trajectories.single { it.childId == "first-twin" }
        val secondLine = chart.trajectories.single { it.childId == "second-twin" }
        assertEquals("K", firstLine.badge)
        assertEquals(listOf(3300.0 / 1000, 6000.0 / 1000), firstLine.points.map { it.value })
        // Second Twin never borrowed one of First Twin's measurements.
        assertEquals(listOf(3.1, 5.6), secondLine.points.map { it.value })
    }

    @Test
    fun `a length measurement does not appear on the weight line`() {
        val chart = growthChart(
            children = listOf(first-twin),
            events = listOf(
                growth("w", "first-twin", birth.plusDays(100), grams = 3300),
                growth("l", "first-twin", birth.plusDays(190), mm = 520),
                growth("w2", "first-twin", birth.plusDays(200), grams = 6000),
            ),
            reference = reference(),
            metric = GrowthMetric.WEIGHT,
            units = metric,
            zone = zone,
        )
        assertEquals(2, chart.markers.size)
        assertEquals(2, chart.trajectories.single().points.size)
        assertTrue("the length row must not join the weight line", chart.trajectories.single().points.none { it.value > 20 })
    }

    @Test
    fun `the measured-on label is readable`() {
        assertEquals("10 Apr 2026", measuredOnLabel(birth.plusDays(100)))
    }

    /** A clock helper is not needed here, but the zone must not silently be the system's. */
    @Test
    fun `markers are dated in the zone they are given`() {
        val instant = LocalDate.of(2026, 4, 10).atStartOfDay(ZoneId.of("Pacific/Auckland")).toInstant()
        val event = BabyEvent(
            id = "a",
            childId = "first-twin",
            type = EventType.GROWTH,
            startedAt = instant.toEpochMilli(),
            weightGrams = 3300,
            createdBy = "owner",
            updatedAt = instant.toEpochMilli(),
        )
        assertEquals(
            LocalDate.of(2026, 4, 10),
            growthChart(listOf(first-twin), listOf(event), reference(), GrowthMetric.WEIGHT, metric, ZoneId.of("Pacific/Auckland"))
                .markers.single().measuredOn,
        )
        assertEquals(
            LocalDate.of(2026, 4, 9),
            growthChart(listOf(first-twin), listOf(event), reference(), GrowthMetric.WEIGHT, metric, ZoneOffset.UTC)
                .markers.single().measuredOn,
        )
        assertEquals(Instant.parse("2026-04-09T12:00:00Z").toEpochMilli(), instant.toEpochMilli())
    }
}
