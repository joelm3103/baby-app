package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.domain.LengthUnit
import app.babylog.domain.MealSlot
import app.babylog.domain.UnitPreferences
import app.babylog.domain.VolumeUnit
import app.babylog.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `growth detail lists every measurement present`() {
        val e = event(EventType.GROWTH).copy(weightGrams = 5443, lengthMm = 571, headMm = 430)
        assertEquals("5.4 kg \u00b7 57.1 cm \u00b7 43 cm", detailLine(e, metric))
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

    private fun event(type: EventType) = BabyEvent(
        id = "e", childId = "first-twin", type = type, startedAt = t0,
        createdBy = "u1", updatedAt = t0,
    )
}
