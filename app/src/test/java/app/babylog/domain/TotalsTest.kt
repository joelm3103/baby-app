package app.babylog.domain

import app.babylog.domain.TemperatureBand
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * History totals, on the JVM. Every function takes `zone` and `now` explicitly, so these assertions are
 * the proof that a day boundary is the LOCAL day and not UTC's — which is the whole reason the totals
 * are not SQL aggregates.
 *
 * The headline test is a hand-counted day: three bottles, four diapers, two sleeps.
 */
class TotalsTest {

    private val zone = ZoneId.of("Asia/Tokyo") // UTC+9, so a local day is nothing like a UTC day.
    private val today = LocalDate.of(2026, 10, 14)
    private val now = at(23, 0)

    private val firstTwin = Child(id = "first-twin", name = "First Twin", birthDate = LocalDate.of(2025, 12, 31), sex = Sex.BOY, sortOrder = 0)
    private val secondTwin = Child(id = "second-twin", name = "Second Twin", birthDate = LocalDate.of(2025, 12, 31), sex = Sex.BOY, sortOrder = 1)

    // ---- ranges ------------------------------------------------------------

    @Test
    fun `dayRange is the local calendar day, not the UTC one`() {
        val range = dayRange(now, zone, 1)

        assertEquals(at(0, 0), range.from)
        assertEquals(at(0, 0, dayOffset = 1), range.to)
        // Midnight UTC on the same date is a different instant; using it would file events a day out.
        assertNotEquals(
            today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            range.from,
        )
    }

    @Test
    fun `seven and fourteen day ranges start the right number of calendar days back`() {
        assertEquals(at(0, 0, dayOffset = -6), dayRange(now, zone, 7).from)
        assertEquals(at(0, 0, dayOffset = -13), dayRange(now, zone, 14).from)
        assertEquals(at(0, 0, dayOffset = 1), dayRange(now, zone, 14).to)
    }

    // ---- night -------------------------------------------------------------

    @Test
    fun `night wraps midnight and the boundaries are half-open`() {
        val night = NightWindow(18, 6)

        assertTrue("23:30 is night", isNight(at(23, 30), zone, night))
        assertTrue("05:59 is night", isNight(at(5, 59), zone, night))
        assertTrue("18:00 is night (inclusive start)", isNight(at(18, 0), zone, night))
        assertFalse("06:00 is day (exclusive end)", isNight(at(6, 0), zone, night))
        assertFalse("17:59 is day", isNight(at(17, 59), zone, night))
        assertFalse("12:00 is day", isNight(at(12, 0), zone, night))
    }

    @Test
    fun `equal start and end means no night at all`() {
        val night = NightWindow(6, 6)

        assertTrue(night.isEmpty)
        assertFalse(isNight(at(3, 0), zone, night))
        assertFalse(isNight(at(22, 0), zone, night))
    }

    // ---- the hand-counted day ---------------------------------------------

    @Test
    fun `totals match a hand-count of the day`() {
        val events = listOf(
            bottle(at(6, 0), 120), bottle(at(10, 0), 90), bottle(at(14, 0), 150),
            diaper(at(3, 0)), diaper(at(8, 0)), diaper(at(12, 0)), diaper(at(15, 0)),
            sleep(from = at(1, 0), to = at(2, 30)), sleep(from = at(20, 0), to = at(21, 0)),
            solids(at(17, 0), listOf("banana", "mango-lassi")),
            growth(at(9, 0), grams = 5443),
            health(at(16, 0), celsius = 37.5),
        )

        val totals = totalsFor(events, listOf(firstTwin), UnitPreferences.Metric, NightWindow(18, 6), zone)
            .associateBy { it.type }

        val bottle = totals.getValue(EventType.BOTTLE)
        assertEquals("360", bottle.headline) // 120 + 90 + 150
        assertEquals("ml", bottle.unit)
        assertEquals("3", bottle.rows.value("Sessions"))
        assertEquals("120 ml", bottle.rows.value("Average bottle")) // 360 / 3
        assertEquals("4h 0m 0s", bottle.rows.value("Time between feeds")) // 4h then 4h

        val diaper = totals.getValue(EventType.DIAPER)
        assertEquals("4", diaper.headline)
        assertEquals("3", diaper.rows.value("Day")) // 08:00, 12:00, 15:00
        assertEquals("1", diaper.rows.value("Night")) // 03:00

        val sleep = totals.getValue(EventType.SLEEP)
        assertEquals("2h 30m 0s", sleep.headline) // 90m + 60m
        assertEquals("2", sleep.rows.value("Sessions"))

        val solids = totals.getValue(EventType.SOLIDS)
        assertEquals("1", solids.headline)
        assertEquals("2", solids.rows.value("Foods logged"))

        val growth = totals.getValue(EventType.GROWTH)
        assertEquals("5.4", growth.headline)
        assertEquals("kg", growth.unit)

        val health = totals.getValue(EventType.HEALTH)
        assertEquals("37.5", health.headline)
        assertEquals("\u00b0C", health.unit)
        assertEquals("1", health.rows.value("Records"))
    }

    // ---- edges -------------------------------------------------------------

    @Test
    fun `a single feed has no average gap`() {
        val totals = totalsFor(
            listOf(bottle(at(6, 0), 120)),
            listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone,
        ).associateBy { it.type }

        assertEquals("\u2014", totals.getValue(EventType.BOTTLE).rows.value("Time between feeds"))
    }

    @Test
    fun `both mode carries a per-child line`() {
        val events = listOf(
            bottle(at(6, 0), 120, childId = "first-twin"),
            bottle(at(7, 0), 60, childId = "second-twin"),
        )

        val bottle = totalsFor(events, listOf(firstTwin, secondTwin), UnitPreferences.Metric, NightWindow(), zone)
            .associateBy { it.type }.getValue(EventType.BOTTLE)

        assertEquals("180", bottle.headline)
        assertEquals(listOf("First Twin" to "120 ml", "Second Twin" to "60 ml"), bottle.perChild.map { it.label to it.value })
    }

    @Test
    fun `growth and health are empty when nothing was measured in range`() {
        val totals = totalsFor(emptyList(), listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone)
            .associateBy { it.type }

        assertTrue(totals.getValue(EventType.GROWTH).empty)
        assertTrue(totals.getValue(EventType.HEALTH).empty)
        // The other four still report a real zero rather than an empty state.
        assertFalse(totals.getValue(EventType.BOTTLE).empty)
        assertEquals("0", totals.getValue(EventType.DIAPER).headline)
    }

    @Test
    fun `the same total renders in the chosen unit without changing the stored value`() {
        val events = listOf(bottle(at(6, 0), 120))

        val metric = totalsFor(events, listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone)
            .associateBy { it.type }.getValue(EventType.BOTTLE)
        val imperial = totalsFor(events, listOf(firstTwin), UnitPreferences.Imperial, NightWindow(), zone)
            .associateBy { it.type }.getValue(EventType.BOTTLE)

        assertEquals("120" to "ml", metric.headline to metric.unit)
        assertEquals("4.1" to "oz", imperial.headline to imperial.unit) // 120 ml is 4.06 fl oz
    }

    @Test
    fun `a running sleep adds a session but no duration`() {
        val events = listOf(BabyEvent(
            id = "s1", childId = "first-twin", type = EventType.SLEEP, startedAt = at(22, 0),
            endedAt = null, createdBy = "t", updatedAt = at(22, 0),
        ))

        val sleep = totalsFor(events, listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone)
            .associateBy { it.type }.getValue(EventType.SLEEP)

        assertEquals("0s", sleep.headline)
        assertEquals("1", sleep.rows.value("Sessions"))
    }

    // ---- helpers -----------------------------------------------------------

    private fun List<TotalRow>.value(label: String): String =
        first { it.label == label }.value

    @Test
    fun `a flagged reading carries its band in the totals and a normal one carries none`() {
        fun healthTotal(vararg celsius: Double) = totalsFor(
            celsius.mapIndexed { i, c -> health(at(9 + i, 0), c) },
            listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone,
        ).associateBy { it.type }.getValue(EventType.HEALTH)

        val fevered = healthTotal(38.4)
        assertEquals(TemperatureBand.FEVER, fevered.temperatureBand)
        assertEquals("38.4", fevered.headline)

        // NORMAL is a null band, not a separate value: a normal reading gets no pill at all.
        assertNull(healthTotal(37.2).temperatureBand)
        // The newest reading wins, so a fever this morning still reads as recovered by the evening.
        assertNull(healthTotal(38.4, 37.0).temperatureBand)
        assertEquals("37", healthTotal(38.4, 37.0).headline)
        // The band is not a fever-only flag: a mild reading is marked, and only FEVER recolours anything.
        assertEquals(TemperatureBand.MILD_FEVER, healthTotal(37.8).temperatureBand)
        assertEquals(TemperatureBand.LOW, healthTotal(36.1).temperatureBand)
    }

    @Test
    fun `health totals name the latest medication and vaccine, each of its own kind`() {
        val events = listOf(
            health(at(12, 0), 38.4),
            health(at(11, 0), 36.9).copy(medication = "Calpol"),
            health(at(8, 0), 36.8).copy(vaccineName = "6-in-1"),
        )
        val health = totalsFor(events, listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone)
            .associateBy { it.type }.getValue(EventType.HEALTH)

        // A vaccine logged in the morning must not vanish because a temperature was logged at noon.
        assertEquals("Calpol", health.rows.value("Medication"))
        assertEquals("6-in-1", health.rows.value("Vaccine"))
        assertEquals("3", health.rows.value("Records"))
        assertEquals("38.4", health.headline)
    }

    @Test
    fun `health totals give the dose its own row`() {
        val events = listOf(
            health(at(12, 0), 38.4),
            health(at(11, 0), 36.9).copy(
                medication = "Calpol",
                doseAmount = 2.5,
                doseUnit = DoseUnit.MILLILITRES,
            ),
        )
        val health = totalsFor(events, listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone)
            .associateBy { it.type }.getValue(EventType.HEALTH)

        // "Which medicines" and "how much of that one" are separate answers, and need not come from the
        // same record.
        assertEquals("Calpol", health.rows.value("Medication"))
        assertEquals("2.5 ml", health.rows.value("Dose"))
    }

    @Test
    fun `a range holding only a medication is not reported as empty`() {
        val events = listOf(health(at(9, 0), 36.8).copy(temperatureC = null, medication = "Vitamin D"))
        val health = totalsFor(events, listOf(firstTwin), UnitPreferences.Metric, NightWindow(), zone)
            .associateBy { it.type }.getValue(EventType.HEALTH)

        // "Nothing logged in this range" beside a record count of 1 is a contradiction.
        assertFalse(health.empty)
        assertEquals("1", health.rows.value("Records"))
        assertEquals("Vitamin D", health.rows.value("Medication"))
        assertNull(health.temperatureBand)
    }

    /** A wall-clock instant on [today] (offset in days) in the test zone. */
    private fun at(hour: Int, minute: Int = 0, dayOffset: Long = 0): Long =
        today.plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun bottle(at: Long, ml: Int, childId: String = "first-twin") = BabyEvent(
        id = "bottle-$at", childId = childId, type = EventType.BOTTLE, startedAt = at,
        amountMl = ml, createdBy = "t", updatedAt = at,
    )

    private fun diaper(at: Long, childId: String = "first-twin") = BabyEvent(
        id = "diaper-$at", childId = childId, type = EventType.DIAPER, startedAt = at,
        diaperKind = DiaperKind.WET, createdBy = "t", updatedAt = at,
    )

    private fun sleep(from: Long, to: Long, childId: String = "first-twin") = BabyEvent(
        id = "sleep-$from", childId = childId, type = EventType.SLEEP, startedAt = from,
        endedAt = to, createdBy = "t", updatedAt = from,
    )

    private fun solids(at: Long, foods: List<String>, childId: String = "first-twin") = BabyEvent(
        id = "solids-$at", childId = childId, type = EventType.SOLIDS, startedAt = at,
        foodIds = foods, createdBy = "t", updatedAt = at,
    )

    private fun growth(at: Long, grams: Int, childId: String = "first-twin") = BabyEvent(
        id = "growth-$at", childId = childId, type = EventType.GROWTH, startedAt = at,
        weightGrams = grams, createdBy = "t", updatedAt = at,
    )

    private fun health(at: Long, celsius: Double, childId: String = "first-twin") = BabyEvent(
        id = "health-$at", childId = childId, type = EventType.HEALTH, startedAt = at,
        temperatureC = celsius, createdBy = "t", updatedAt = at,
    )
}
