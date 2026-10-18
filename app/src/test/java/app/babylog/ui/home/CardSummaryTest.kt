package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperKind
import app.babylog.domain.DoseUnit
import app.babylog.domain.EventType
import app.babylog.domain.LengthUnit
import app.babylog.domain.MealSlot
import app.babylog.domain.UnitPreferences
import app.babylog.domain.VolumeUnit
import app.babylog.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "What does the big number show" is the app's central design decision, so it gets tests rather than
 * being checked by eye on a device.
 */
class CardSummaryTest {

    private val t0 = 1_760_000_000_000L
    private val metric = UnitPreferences.Metric
    private val imperial = UnitPreferences.Imperial

    @Test
    fun `bottle shows the volume in the chosen family`() {
        val e = event(EventType.BOTTLE).copy(amountMl = 120)
        assertEquals("120" to "ml", bigValue(e, metric))
        assertEquals("4.1" to "oz", bigValue(e, imperial))
    }

    @Test
    fun `bottle with no amount has no big value rather than a zero`() {
        assertNull(bigValue(event(EventType.BOTTLE), metric))
    }

    @Test
    fun `diaper has no big value, it has a kind`() {
        assertNull(bigValue(event(EventType.DIAPER).copy(diaperKind = DiaperKind.WET), metric))
        assertEquals("Wet", detailLine(event(EventType.DIAPER).copy(diaperKind = DiaperKind.WET), metric))
        assertEquals("Mixed", detailLine(event(EventType.DIAPER).copy(diaperKind = DiaperKind.MIXED), metric))
    }

    @Test
    fun `a rash is called out on the diaper card`() {
        val e = event(EventType.DIAPER).copy(diaperKind = DiaperKind.DIRTY, rash = true)
        assertEquals("Dirty \u00b7 rash", detailLine(e, metric))
    }

    @Test
    fun `sleep shows its duration, or says it is still running`() {
        val finished = event(EventType.SLEEP).copy(endedAt = t0 + 95 * 60_000L)
        assertEquals("1h 35m 0s" to "", bigValue(finished, metric))
        assertEquals("Slept 1h 35m 0s", detailLine(finished, metric))

        val running = event(EventType.SLEEP)
        assertNull(bigValue(running, metric))
        assertEquals("Asleep now", detailLine(running, metric))
    }

    @Test
    fun `growth weight follows the unit preference, including the compound pound-ounce form`() {
        val e = event(EventType.GROWTH).copy(weightGrams = 5443)
        assertEquals("5.4" to "kg", bigValue(e, metric))
        assertEquals("12" to "lb 0 oz", bigValue(e, imperial))
    }

    @Test
    fun `growth falls back to length when weight is missing`() {
        val e = event(EventType.GROWTH).copy(lengthMm = 571)
        assertEquals("57.1" to "cm", bigValue(e, metric))
        assertEquals("22.5" to "in", bigValue(e, imperial))
    }

    @Test
    fun `growth detail lists every measurement present, each labelled`() {
        val e = event(EventType.GROWTH).copy(weightGrams = 5443, lengthMm = 571, headMm = 430)
        // W/L/H, not bare numbers: "57.1 cm" and "43 cm" are otherwise indistinguishable as length or
        // head circumference, which is the confusion the owner reported on the card.
        assertEquals("W 5.4 kg \u00b7 L 57.1 cm \u00b7 H 43 cm", detailLine(e, metric))
    }

    @Test
    fun `a growth card ROW lists every measurement recorded, not just the headline one`() {
        val allThree = event(EventType.GROWTH).copy(weightGrams = 5443, lengthMm = 571, headMm = 430)
        assertEquals("W 5.4 kg \u00b7 L 57.1 cm \u00b7 H 43 cm", recentRowLabel(allThree, metric))
        assertEquals("W 12 lb 0 oz \u00b7 L 22.5 in \u00b7 H 16.9 in", recentRowLabel(allThree, imperial))

        // ...and a weight-only measurement says so honestly, rather than padding with blanks.
        val weightOnly = event(EventType.GROWTH).copy(weightGrams = 5443)
        assertEquals("W 5.4 kg", recentRowLabel(weightOnly, metric))

        // Length and head without a weight still both appear, and still say which is which.
        val noWeight = event(EventType.GROWTH).copy(lengthMm = 571, headMm = 430)
        assertEquals("L 57.1 cm \u00b7 H 43 cm", recentRowLabel(noWeight, metric))
    }

    @Test
    fun `the Both-mode summary gives each child their own column`() {
        // The owner's rule: in Both mode a combined headline has no owner - "6 kg" was one child's weight
        // read as the twins' - so each child gets their own latest weight, length and head.
        val first-twin = event(EventType.GROWTH).copy(id = "k", childId = "first-twin", weightGrams = 6000, lengthMm = 660, headMm = 420)
        val second-twin = event(EventType.GROWTH).copy(id = "l", childId = "second-twin", weightGrams = 5400, headMm = 410)

        val columns = growthByChild(listOf(second-twin, first-twin), listOf("first-twin", "second-twin"), metric)

        assertEquals(listOf("first-twin", "second-twin"), columns.map { it.childId })
        // One line per measurement, in the sheet's field order — the shape the card draws.
        assertEquals(listOf("W 6 kg", "L 66 cm", "H 42 cm"), columns[0].lines)
        // Second Twin never recorded a length, so his column omits it rather than borrowing First Twin's.
        assertEquals(listOf("W 5.4 kg", "H 41 cm"), columns[1].lines)
        assertTrue(columns.all { it.hasData })
    }

    @Test
    fun `a child with no measurements gets an empty column, not the other child's numbers`() {
        val first-twin = event(EventType.GROWTH).copy(id = "k", childId = "first-twin", weightGrams = 6000)

        val columns = growthByChild(listOf(first-twin), listOf("first-twin", "second-twin"), metric)

        assertEquals(listOf("W 6 kg"), columns[0].lines)
        assertTrue(columns[0].hasData)
        assertTrue(columns[1].lines.isEmpty())
        assertFalse(columns[1].hasData)
    }

    @Test
    fun `the growth summary takes the latest of EACH measurement, not the newest entry`() {
        // The scenario that prompted this, with a blank where no measurement was taken:
        //   weight 10, 12, 20 kg  |  length 50, 55, (blank) cm  |  head (blank), 25, (blank) cm
        // The newest entry holds only a weight, so reading it alone answered "20 kg" and hid a 55 cm
        // length and a 25 cm head circumference that are still the most recent of their kind.
        val day1 = event(EventType.GROWTH, at = t0 - 2 * day)
            .copy(weightGrams = 10_000, lengthMm = 500)
        val day2 = event(EventType.GROWTH, at = t0 - day)
            .copy(weightGrams = 12_000, lengthMm = 550, headMm = 250)
        val day3 = event(EventType.GROWTH, at = t0).copy(weightGrams = 20_000)
        val entries = listOf(day3, day2, day1)

        val latest = growthLatest(entries)

        assertEquals(20_000, latest.weight!!.weightGrams)
        assertEquals(550, latest.length!!.lengthMm)
        assertEquals(250, latest.head!!.headMm)
        assertEquals("20" to "kg", growthBigValue(latest, metric))
        assertEquals("W 20 kg \u00b7 L 55 cm \u00b7 H 25 cm", growthDetail(latest, metric))
        // ...and the same in pounds and inches, in the fields' own order.
        // ...and the same in pounds and inches, in the fields' own order. Lengths use the display
        // convention's one decimal, so 55 cm reads 21.7 in rather than 21.65.
        assertEquals("W 44 lb 1 oz \u00b7 L 21.7 in \u00b7 H 9.8 in", growthDetail(latest, imperial))
    }

    @Test
    fun `the growth summary is indifferent to the order it is given`() {
        val older = event(EventType.GROWTH, at = t0 - day).copy(weightGrams = 10_000, headMm = 400)
        val newer = event(EventType.GROWTH, at = t0).copy(lengthMm = 600)

        assertEquals("W 10 kg \u00b7 L 60 cm \u00b7 H 40 cm", growthDetail(growthLatest(listOf(newer, older)), metric))
        assertEquals("W 10 kg \u00b7 L 60 cm \u00b7 H 40 cm", growthDetail(growthLatest(listOf(older, newer)), metric))
        // The headline is the newest WEIGHT, which is the older entry here, so its date is what the card
        // shows beside the number.
        assertEquals(older.startedAt, growthLatest(listOf(newer, older)).headline!!.startedAt)
    }

    @Test
    fun `a growth summary with nothing measured says nothing`() {
        val latest = growthLatest(listOf(event(EventType.BOTTLE)))
        assertTrue(latest.isEmpty)
        assertNull(growthBigValue(latest, metric))
        assertEquals("", growthDetail(latest, metric))
    }

    @Test
    fun `temperature is always celsius`() {
        val e = event(EventType.HEALTH).copy(temperatureC = 38.2)
        assertEquals("38.2" to "\u00b0C", bigValue(e, metric))
        // and an imperial preference must not change it
        assertEquals("38.2" to "\u00b0C", bigValue(e, imperial))
    }

    @Test
    fun `solids names the foods, falling back to the meal slot`() {
        val withFood = event(EventType.SOLIDS).copy(foodIds = listOf("banana", "sweet-potato"))
        assertEquals("banana, sweet potato", detailLine(withFood, metric))

        val withSlotOnly = event(EventType.SOLIDS).copy(mealSlot = MealSlot.LUNCH)
        assertEquals("Lunch", detailLine(withSlotOnly, metric))

        val bare = event(EventType.SOLIDS)
        assertEquals("Solids", detailLine(bare, metric))
    }

    @Test
    fun `a recent row shows the volume for a bottle, not the brand`() {
        val withAmount = event(EventType.BOTTLE).copy(amountMl = 120, formulaBrand = "Aptamil")

        assertEquals("120 ml", recentRowLabel(withAmount, metric))
        assertEquals("4.1 oz", recentRowLabel(withAmount, imperial))
        // detailLine still shows the brand under the big value: the two questions differ on purpose.
        assertEquals("Aptamil", detailLine(withAmount, metric))
    }

    @Test
    fun `a bottle row without an amount falls back rather than showing nothing`() {
        assertEquals("Bottle", recentRowLabel(event(EventType.BOTTLE), metric))
    }

    @Test
    fun `a non-bottle row reuses the detail line`() {
        val diaper = event(EventType.DIAPER).copy(diaperKind = DiaperKind.MIXED, rash = true)

        assertEquals("Mixed \u00b7 rash", recentRowLabel(diaper, metric))
        assertEquals(detailLine(diaper, metric), recentRowLabel(diaper, metric))
    }

    @Test
    fun `a health row carries the reading, the word fever and what was given`() {
        val fever = event(EventType.HEALTH).copy(temperatureC = 38.2, medication = "Calpol")
        // The row is self-describing: the old label showed only the medication, so a fever row read
        // "Calpol" and looked exactly like a normal one.
        assertEquals("38.2 \u00b0C \u00b7 fever \u00b7 Calpol", recentRowLabel(fever, metric))

        val normal = event(EventType.HEALTH).copy(temperatureC = 37.1, medication = "Calpol")
        assertEquals("37.1 \u00b0C \u00b7 Calpol", recentRowLabel(normal, metric))

        // Under the big value the number is not repeated: `38.2` is already the headline.
        assertEquals("Calpol \u00b7 fever", detailLine(fever, metric))
        assertEquals("Calpol", detailLine(normal, metric))
    }

    @Test
    fun `a dose is shown with its medicine, wherever the medicine is shown`() {
        val dosed = event(EventType.HEALTH).copy(
            temperatureC = 38.5,
            medication = "Calpol",
            doseAmount = 2.5,
            doseUnit = DoseUnit.MILLILITRES,
        )

        assertEquals("38.5 \u00b0C \u00b7 fever \u00b7 Calpol 2.5 ml", recentRowLabel(dosed, metric))
        assertEquals("Calpol 2.5 ml \u00b7 fever", detailLine(dosed, metric))

        // A medicine with no dose recorded says only the medicine - never a 0, and never a blank unit.
        val undosed = event(EventType.HEALTH).copy(medication = "Vitamin D")
        assertEquals("Vitamin D", recentRowLabel(undosed, metric))
    }

    @Test
    fun `a medication-only row names the medication rather than saying Health`() {
        val med = event(EventType.HEALTH).copy(medication = "Vitamin D")
        assertEquals("Vitamin D", recentRowLabel(med, metric))
        assertEquals("Health", recentRowLabel(event(EventType.HEALTH), metric))
    }

    @Test
    fun `a vaccine row says it is a vaccine, exactly once`() {
        val vaccine = event(EventType.HEALTH).copy(vaccineName = "6-in-1")
        assertEquals("6-in-1 vaccine", recentRowLabel(vaccine, metric))

        // A parent who typed the word themselves must not read "Vaccine 6-in-1 vaccine".
        val typed = event(EventType.HEALTH).copy(vaccineName = "Vaccine 6-in-1")
        assertEquals("Vaccine 6-in-1", recentRowLabel(typed, metric))
    }

    @Test
    fun `the health summary is the latest of each kind, not the newest entry`() {
        val entries = listOf(
            event(EventType.HEALTH, at = t0 + 3_000).copy(temperatureC = 38.4),
            event(EventType.HEALTH, at = t0 + 2_000).copy(temperatureC = 36.8, medication = "Calpol"),
            event(EventType.HEALTH, at = t0 + 1_000).copy(vaccineName = "6-in-1"),
        )
        val latest = healthLatest(entries)

        // The newest entry is the fever, but the vaccine must not vanish because of it.
        assertEquals(38.4, latest.record?.temperatureC)
        assertEquals("6-in-1", latest.vaccine?.vaccineName)
        assertFalse(latest.isEmpty)
    }

    @Test
    fun `a medication-only record is a summary record, so it is not lost`() {
        val latest = healthLatest(listOf(event(EventType.HEALTH).copy(medication = "Calpol")))
        assertEquals("Calpol", latest.record?.medication)
        assertFalse(latest.isEmpty)
    }

    @Test
    fun `a health summary with nothing recorded says so`() {
        val latest = healthLatest(listOf(event(EventType.HEALTH)))
        assertTrue(latest.isEmpty)
    }

    @Test
    fun `both-mode health columns carry each child's own fever and vaccine`() {
        val entries = listOf(
            event(EventType.HEALTH, at = t0).copy(childId = "first-twin", temperatureC = 38.4),
            event(EventType.HEALTH, at = t0).copy(childId = "first-twin", vaccineName = "6-in-1"),
            event(EventType.HEALTH, at = t0).copy(childId = "second-twin", temperatureC = 36.9),
        )
        val columns = cardColumns(EventType.HEALTH, entries, listOf("first-twin", "second-twin"), metric)!!

        assertEquals(listOf("first-twin", "second-twin"), columns.map { it.childId })
        // First Twin's fever line is marked so the card can colour it, and his vaccine keeps its own line.
        assertEquals("38.4 \u00b0C \u00b7 fever", columns[0].lines[0].text)
        assertTrue(columns[0].lines[0].fever)
        assertEquals("6-in-1 vaccine", columns[0].lines[1].text)
        assertFalse(columns[0].lines[1].fever)
        // Second Twin has a temperature and nothing else; his line is not a fever.
        assertEquals("36.9 \u00b0C", columns[1].lines[0].text)
        assertFalse(columns[1].lines[0].fever)
    }

    @Test
    fun `a child with no health record has no lines`() {
        val columns = cardColumns(EventType.HEALTH, emptyList(), listOf("first-twin"), metric)!!
        assertTrue(columns.single().lines.isEmpty())
        assertFalse(columns.single().hasData)
    }

    @Test
    fun `only growth and health split per child`() {
        val diaper = listOf(event(EventType.DIAPER).copy(diaperKind = DiaperKind.WET))
        assertNull(cardColumns(EventType.DIAPER, diaper, listOf("first-twin", "second-twin"), metric))
        assertNotNull(cardColumns(EventType.GROWTH, emptyList(), listOf("first-twin"), metric))
    }

    private fun event(type: EventType, at: Long = t0) = BabyEvent(
        id = "e", childId = "first-twin", type = type, startedAt = at,
        createdBy = "u1", updatedAt = at,
    )

    /** One day, for the per-measurement-latest cases where the fields were taken on different days. */
    private val day = 86_400_000L
}
