package app.babylog.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trends, on the JVM — and the same zone trap Totals has, which is the whole reason these are not SQL.
 *
 * `Asia/Tokyo` (UTC+9) again: a 23:30 local feed is 14:30 UTC *the same date*, but a 06:00 local feed on
 * the 14th is 21:00 UTC on the 13th. Bucketing by UTC moves that feed a day back and the chart is wrong
 * in a way nothing on screen would reveal.
 *
 * The headline test is a hand-counted window: 7 days, three bottles on one day, none on another.
 */
class TrendsTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 10, 18)
    private val now = at(23, 0)

    private val firstTwin = Child(id = "first-twin", name = "First Twin", birthDate = LocalDate.of(2025, 12, 31), sex = Sex.BOY, sortOrder = 0)
    private val secondTwin = Child(id = "second-twin", name = "Second Twin", birthDate = LocalDate.of(2025, 12, 31), sex = Sex.BOY, sortOrder = 1)

    // ---- the window ---------------------------------------------------------

    @Test
    fun `the window is the last N local days ending today, oldest first`() {
        val days = dayStarts(now, zone, 7)

        assertEquals(7, days.size)
        assertEquals(at(0, 0, dayOffset = -6), days.first())
        assertEquals(at(0, 0), days.last())
    }

    @Test
    fun `thirty days reach thirty local midnights back`() {
        val days = dayStarts(now, zone, 30)

        assertEquals(30, days.size)
        assertEquals(at(0, 0, dayOffset = -29), days.first())
    }

    @Test
    fun `an event just before local midnight lands on its own local day`() {
        // 23:59 on the 17th is 14:59 UTC on the 17th. A UTC bucket keeps this on the 17th too, so the
        // assertion that matters is against the LOCAL day list, which is what the axis draws.
        val series = seriesOf(EventType.BOTTLE, listOf(firstTwin), listOf(bottle(at(23, 59, dayOffset = -1), 90)))

        val line = series.lines.single()
        assertEquals(90.0, line.points[line.points.size - 2].value, 1e-9)
        assertTrue(line.points[line.points.size - 2].recorded)
        assertFalse("the newest day has nothing", line.points.last().recorded)
    }

    @Test
    fun `an early morning feed is not pushed onto the previous day`() {
        // 06:00 local on the 18th is 21:00 UTC on the 17th — the case that makes a UTC bucket wrong.
        val series = seriesOf(EventType.BOTTLE, listOf(firstTwin), listOf(bottle(at(6, 0), 120)))

        val line = series.lines.single()
        assertTrue("today, not yesterday", line.points.last().recorded)
        assertFalse(line.points[line.points.size - 2].recorded)
        assertEquals(120.0, line.points.last().value, 1e-9)
    }

    // ---- what a day's number is ---------------------------------------------

    @Test
    fun `bottle volume is summed per day and the empty days are zero`() {
        val events = listOf(
            bottle(at(7, 0), 90),
            bottle(at(11, 30), 120),
            bottle(at(19, 0, dayOffset = -2), 60),
        )
        val series = seriesOf(EventType.BOTTLE, listOf(firstTwin), events)

        val line = series.lines.single()
        assertEquals(270.0, line.total, 1e-9)
        assertEquals(90.0 + 120.0, line.points.last().value, 1e-9)
        assertEquals(60.0, line.points[series.range.days - 3].value, 1e-9)
        // Five untouched days are zero AND unrecorded — the chart draws a floor, not a gap, and the
        // summary can still say how many days were actually logged.
        assertEquals(2, line.daysRecorded)
        assertEquals(270.0 / 7, line.averagePerDay, 1e-9)
    }

    @Test
    fun `a logged bottle with no amount counts as a day recorded with no volume`() {
        val series = seriesOf(EventType.BOTTLE, listOf(firstTwin), listOf(bottle(at(9, 0), null)))

        val line = series.lines.single()
        assertEquals(1, line.daysRecorded)
        assertEquals(0.0, line.total, 1e-9)
        assertTrue(series.hasData)
    }

    @Test
    fun `diapers and solids are counted`() {
        val diapers = List(3) { index -> diaper(at(9 + index, 0)) }
        val series = seriesOf(EventType.DIAPER, listOf(firstTwin), diapers)

        assertEquals(3.0, series.lines.single().points.last().value, 1e-9)
        assertEquals(TrendMetric.COUNT, series.metric)
    }

    @Test
    fun `sleep is summed in minutes, and a running timer contributes nothing yet`() {
        val events = listOf(
            sleep(from = at(1, 0, dayOffset = -1), to = at(3, 30, dayOffset = -1)),
            // Running: started at 13:00 and never stopped, so it is a day with a record and no duration.
            sleep(from = at(13, 0), to = null),
        )
        val series = seriesOf(EventType.SLEEP, listOf(firstTwin), events)

        val line = series.lines.single()
        // Two and a half hours from the finished session; guessing "until now" for the other one would
        // make this total grow while nobody is looking.
        assertEquals(150.0, line.total, 1e-9)
        assertEquals(2, line.daysRecorded)
        assertEquals(0.0, line.points.last().value, 1e-9)
        assertTrue("the running session is still a day recorded", line.points.last().recorded)
        assertEquals("2h 30m 0s", formatTrendTotal(TrendMetric.DURATION, line.total, UnitPreferences.Metric))
    }

    @Test
    fun `a sleep that starts before midnight is counted on the day it started`() {
        val events = listOf(sleep(from = at(22, 0, dayOffset = -1), to = at(6, 0)))

        val line = seriesOf(EventType.SLEEP, listOf(firstTwin), events).lines.single()
        assertEquals(480.0, line.total, 1e-9)
        // Yesterday's bucket, in full: 8 hours, none of it split onto today.
        assertEquals(480.0, line.points[line.points.size - 2].value, 1e-9)
        assertEquals(0.0, line.points.last().value, 1e-9)
    }

    // ---- per child ----------------------------------------------------------

    @Test
    fun `each child gets their own line, so Both mode cannot merge the twins`() {
        val events = listOf(
            bottle(at(8, 0), 100, childId = "first-twin"),
            bottle(at(8, 0), 60, childId = "second-twin"),
            bottle(at(20, 0), 40, childId = "second-twin"),
        )
        val series = seriesOf(EventType.BOTTLE, listOf(firstTwin, secondTwin), events)

        assertEquals(listOf("First Twin", "Second Twin"), series.lines.map { it.childName })
        assertEquals(100.0, series.lines[0].total, 1e-9)
        assertEquals(100.0, series.lines[1].total, 1e-9)
        assertTrue(series.multipleChildren)
    }

    @Test
    fun `another child's events never leak into a single-child series`() {
        val events = listOf(
            bottle(at(8, 0), 100, childId = "first-twin"),
            bottle(at(9, 0), 900, childId = "second-twin"),
        )

        val line = seriesOf(EventType.BOTTLE, listOf(firstTwin), events).lines.single()
        assertEquals(100.0, line.total, 1e-9)
    }

    @Test
    fun `an event outside the window is ignored, not clipped onto the edge day`() {
        val events = listOf(
            bottle(at(9, 0, dayOffset = -20), 500),
            bottle(at(9, 0, dayOffset = -30), 500),
            bottle(at(9, 0), 80),
        )

        val line = seriesOf(EventType.BOTTLE, listOf(firstTwin), events).lines.single()
        assertEquals(80.0, line.total, 1e-9)
    }

    @Test
    fun `the best day is the biggest recorded day, and there is none when nothing was logged`() {
        val events = listOf(
            bottle(at(7, 0), 60),
            bottle(at(21, 0), 120),
            bottle(at(9, 0, dayOffset = -3), 30),
        )
        val line = seriesOf(EventType.BOTTLE, listOf(firstTwin), events).lines.single()

        assertEquals(180.0, line.bestDay?.value ?: 0.0, 1e-9)
        assertNull(seriesOf(EventType.BOTTLE, listOf(firstTwin), emptyList()).lines.single().bestDay)
        assertFalse(seriesOf(EventType.BOTTLE, listOf(firstTwin), emptyList()).hasData)
    }

    @Test
    fun `a deleted event is not charted`() {
        // The DAO filters tombstones; this pins the domain's own behaviour if one ever reaches it live.
        val events = listOf(bottle(at(9, 0), 90).copy(deleted = true))

        val series = seriesOf(EventType.BOTTLE, listOf(firstTwin), events)
        assertEquals(0, series.lines.single().daysRecorded)
        assertFalse(series.hasData)
    }

    @Test
    fun `the range chip changes the window, not the data`() {
        val events = listOf(bottle(at(9, 0, dayOffset = -10), 200), bottle(at(9, 0), 100))

        val week = seriesOf(EventType.BOTTLE, listOf(firstTwin), events, TrendRange.WEEK)
        val month = seriesOf(EventType.BOTTLE, listOf(firstTwin), events, TrendRange.MONTH)

        assertEquals(100.0, week.lines.single().total, 1e-9)
        assertEquals(300.0, month.lines.single().total, 1e-9)
        assertEquals(7, week.days.size)
        assertEquals(30, month.days.size)
    }

    // ---- health: a table, and the fever boundary ----------------------------

    @Test
    fun `readings at or above 37_6 are listed with their band, and 38 stays the fever boundary`() {
        val events = listOf(
            health(at(8, 0), 37.5),
            health(at(9, 0), 37.6),
            health(at(10, 0), 37.9),
            health(at(11, 0), 38.0),
            health(at(12, 0), 38.6),
        )
        val trend = healthTrend(listOf(firstTwin), events, zone)

        assertEquals(5, trend.readings)
        // Mild rows are listed too - that is the whole point of a band table - newest first.
        assertEquals(listOf(38.6, 38.0, 37.9, 37.6), trend.feverReadings.map { it.temperatureC })
        assertEquals(
            listOf(
                TemperatureBand.FEVER,
                TemperatureBand.FEVER,
                TemperatureBand.MILD_FEVER,
                TemperatureBand.MILD_FEVER,
            ),
            trend.feverReadings.map { it.band },
        )
        assertEquals(1, trend.feverDays)
        assertTrue(trend.hasFever)
    }

    @Test
    fun `the flagged table follows the displayed value at the mild boundary`() {
        val events = listOf(
            health(at(8, 0), 37.54), // displays 37.5: normal, omitted
            health(at(9, 0), 37.56), // displays 37.6: mild, listed
        )

        val trend = healthTrend(listOf(firstTwin), events, zone)

        assertEquals(listOf(37.56), trend.feverReadings.map { it.temperatureC })
        assertEquals(listOf(TemperatureBand.MILD_FEVER), trend.feverReadings.map { it.band })
        assertEquals(1, trend.feverDays)
    }

    @Test
    fun `a mild day counts as a day in the band table`() {
        val events = listOf(
            health(at(8, 0, dayOffset = -1), 37.7),
            health(at(9, 0), 38.2),
        )
        val trend = healthTrend(listOf(firstTwin), events, zone)

        // Yesterday was mild, today is a fever: two days, even though only one reading is a fever.
        assertEquals(2, trend.feverDays)
        assertEquals(listOf(TemperatureBand.FEVER, TemperatureBand.MILD_FEVER), trend.feverReadings.map { it.band })
    }

    @Test
    fun `fever days are distinct days, not readings`() {
        val events = listOf(
            health(at(8, 0), 38.4),
            health(at(12, 0), 38.9),
            health(at(20, 0), 38.1),
            health(at(8, 0, dayOffset = -1), 38.2),
            health(at(8, 0, dayOffset = -2), 38.7),
        )
        val trend = healthTrend(listOf(firstTwin), events, zone)

        assertEquals(5, trend.readings)
        assertEquals("three evenings of fever, five readings", 3, trend.feverDays)
        assertEquals(5, trend.feverReadings.size)
    }

    @Test
    fun `a medication alone is a reading count and never a fever`() {
        val events = listOf(
            health(at(9, 0), 36.8).copy(temperatureC = null, medication = "Vitamin D"),
        )
        val trend = healthTrend(listOf(firstTwin), events, zone)

        // No temperature is no reading: the table counts thermometers, not rows.
        assertEquals(0, trend.readings)
        assertFalse(trend.hasFever)
        assertEquals(0, trend.feverDays)
    }

    @Test
    fun `the health table names the child, so Both mode is readable`() {
        val events = listOf(
            health(at(9, 0), 38.5, childId = "first-twin"),
            health(at(10, 0), 39.1, childId = "second-twin"),
        )
        val trend = healthTrend(listOf(firstTwin, secondTwin), events, zone)

        assertEquals(listOf("Second Twin", "First Twin"), trend.feverReadings.map { it.childName })
    }

    @Test
    fun `a shared initial is numbered in the fever table too`() {
        val kai = firstTwin.copy(id = "kai", name = "Kai")
        val kim = firstTwin.copy(id = "kim", name = "Kim", sortOrder = 1)
        val events = listOf(
            health(at(9, 0), 38.5, childId = "kai"),
            health(at(10, 0), 39.1, childId = "kim"),
        )
        val trend = healthTrend(listOf(kai, kim), events, zone)

        assertEquals(listOf("K2", "K1"), trend.feverReadings.map { it.childBadge })
    }

    // ---- display ------------------------------------------------------------

    @Test
    fun `volume follows the unit preference without touching the stored number`() {
        val metric = TrendMetric.VOLUME

        assertEquals(90.0, trendDisplayValue(metric, 90.0, UnitPreferences.Metric), 1e-9)
        assertEquals("90", formatTrendNumber(trendDisplayValue(metric, 90.0, UnitPreferences.Metric)))
        assertEquals("ml per day", trendAxisUnit(metric, UnitPreferences.Metric))

        // 90 ml is 3.0433 oz. The display conversion is real and lossy-looking, so the axis label — what
        // a parent actually reads — is what gets asserted, and the stored number is untouched by it.
        assertEquals(3.0433, trendDisplayValue(metric, 90.0, UnitPreferences.Imperial), 1e-3)
        assertEquals("3", formatTrendNumber(trendDisplayValue(metric, 90.0, UnitPreferences.Imperial)))
        assertEquals("oz per day", trendAxisUnit(metric, UnitPreferences.Imperial))
        assertEquals("90 ml", formatTrendTotal(metric, 90.0, UnitPreferences.Metric))
    }

    @Test
    fun `a count is a whole number on the axis and a duration is hours`() {
        assertEquals("8", formatTrendNumber(trendDisplayValue(TrendMetric.COUNT, 8.0, UnitPreferences.Metric)))
        assertEquals("8.3", formatTrendNumber(trendDisplayValue(TrendMetric.COUNT, 8.25, UnitPreferences.Metric)))
        assertEquals(2.5, trendDisplayValue(TrendMetric.DURATION, 150.0, UnitPreferences.Metric), 1e-9)
        assertEquals("hours per day", trendAxisUnit(TrendMetric.DURATION, UnitPreferences.Metric))
        assertEquals("8", formatTrendNumber(8.0))
        assertEquals("0", formatTrendNumber(Double.NaN))
    }

    @Test
    fun `a count keeps a decimal in the average, so a real number never prints as zero`() {
        // Found on the phone: 13 nappies over 30 days averaged to "0 per day" beside a total of 13, and 2
        // solids over 30 days read the same way. A count is a whole number per DAY, not per average.
        assertEquals("0.4", trendHeadline(TrendMetric.COUNT, 13.0 / 30, UnitPreferences.Metric))
        assertEquals("0.1", trendHeadline(TrendMetric.COUNT, 2.0 / 30, UnitPreferences.Metric))
        assertEquals("8", trendHeadline(TrendMetric.COUNT, 8.0, UnitPreferences.Metric))
        assertEquals("8.3", trendHeadline(TrendMetric.COUNT, 8.25, UnitPreferences.Metric))

        // And when the average genuinely rounds away while the total does not, it says so rather than
        // contradicting the total: 13 minutes of sleep across 30 days is not "0 hours a day".
        assertEquals("< 0.1", trendHeadline(TrendMetric.DURATION, 13.08 / 30, UnitPreferences.Metric))
        assertEquals("0", trendHeadline(TrendMetric.COUNT, 0.0, UnitPreferences.Metric))
        assertEquals("12.4", trendHeadline(TrendMetric.DURATION, 12.4 * 60, UnitPreferences.Metric))
    }

    @Test
    fun `every charted type has a metric and growth and health are not charted`() {
        assertFalse(EventType.GROWTH in CHARTED_TYPES)
        assertFalse(EventType.HEALTH in CHARTED_TYPES)
        assertEquals(TrendMetric.VOLUME, metricFor(EventType.BOTTLE))
        assertEquals(TrendMetric.DURATION, metricFor(EventType.SLEEP))
        assertEquals(TrendMetric.COUNT, metricFor(EventType.DIAPER))
        assertEquals(TrendMetric.COUNT, metricFor(EventType.SOLIDS))
    }

    // ---- helpers ------------------------------------------------------------

    private fun seriesOf(
        type: EventType,
        children: List<Child>,
        events: List<BabyEvent>,
        range: TrendRange = TrendRange.WEEK,
    ) = trendSeries(
        type = type,
        children = children,
        events = events,
        range = range,
        now = now,
        zone = zone,
        units = UnitPreferences.Metric,
    )

    /** A wall-clock instant on [today] (offset in days) in the test zone. */
    private fun at(hour: Int, minute: Int = 0, dayOffset: Long = 0): Long =
        today.plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun bottle(at: Long, ml: Int?, childId: String = "first-twin") = BabyEvent(
        id = "bottle-$at-$childId", childId = childId, type = EventType.BOTTLE, startedAt = at,
        amountMl = ml, createdBy = "t", updatedAt = at,
    )

    private fun diaper(at: Long, childId: String = "first-twin") = BabyEvent(
        id = "diaper-$at", childId = childId, type = EventType.DIAPER, startedAt = at,
        diaperKind = DiaperKind.WET, createdBy = "t", updatedAt = at,
    )

    private fun sleep(from: Long, to: Long?, childId: String = "first-twin") = BabyEvent(
        id = "sleep-$from", childId = childId, type = EventType.SLEEP, startedAt = from,
        endedAt = to, createdBy = "t", updatedAt = from,
    )

    private fun health(at: Long, celsius: Double?, childId: String = "first-twin") = BabyEvent(
        id = "health-$at-$childId", childId = childId, type = EventType.HEALTH, startedAt = at,
        temperatureC = celsius, createdBy = "t", updatedAt = at,
    )
}
