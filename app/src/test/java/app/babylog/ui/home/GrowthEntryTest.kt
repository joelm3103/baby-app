package app.babylog.ui.home

import app.babylog.domain.LengthUnit
import app.babylog.domain.UnitPreferences
import app.babylog.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the growth sheet would STORE, tested without a composable.
 *
 * The interesting cases are the ones that are silent when wrong: a measurement converted into the
 * wrong unit, a value rewritten by round-tripping through the display unit, and a typo accepted as a
 * real weight.
 */
class GrowthEntryTest {

    private val metric = UnitPreferences(length = LengthUnit.CENTIMETRES, weight = WeightUnit.KILOGRAMS)
    private val imperial = UnitPreferences(length = LengthUnit.INCHES, weight = WeightUnit.POUNDS_OUNCES)

    @Test
    fun `a blank sheet reads as nothing measured, with no error`() {
        val read = readGrowth(GrowthFields(), metric)
        assertNull(read.error)
        assertTrue(read.values.isEmpty)
        assertNull(read.values.weightGrams)
        assertNull(read.values.lengthMm)
        assertNull(read.values.headMm)
    }

    @Test
    fun `kilograms become grams`() {
        val read = readGrowth(GrowthFields(weight = "6.35"), metric)
        assertNull(read.error)
        assertEquals(6350, read.values.weightGrams)
    }

    @Test
    fun `centimetres become millimetres`() {
        val read = readGrowth(GrowthFields(length = "57.1", head = "38.5"), metric)
        assertNull(read.error)
        assertEquals(571, read.values.lengthMm)
        assertEquals(385, read.values.headMm)
    }

    @Test
    fun `inches become millimetres`() {
        val read = readGrowth(GrowthFields(length = "22.5"), imperial)
        assertNull(read.error)
        // 22.5 in is 571.5 mm, which rounds to 572 - inches are read, cm stored, and the two are not
        // interchangeable just because the number looks the same.
        assertEquals(572, read.values.lengthMm)
    }

    @Test
    fun `an imperial length round-trips a stored millimetre exactly`() {
        // 2 decimals in inches is 0.254 mm of resolution, so the box is finer than what is stored.
        val stored = GrowthValues(lengthMm = 571, headMm = 418)
        val seed = growthFieldsFor(stored, imperial)
        val untouched = readGrowth(seed, imperial, stored)
        assertEquals(571, untouched.values.lengthMm)
        assertEquals(418, untouched.values.headMm)
    }

    @Test
    fun `pounds and ounces are a compound value`() {
        val read = readGrowth(GrowthFields(pounds = "14", ounces = "8"), imperial)
        assertNull(read.error)
        // 14 lb 8 oz = 6577 g. Treating the pair as a decimal would give nonsense.
        assertEquals(6577, read.values.weightGrams)
    }

    @Test
    fun `ounces alone are allowed and pounds default to zero`() {
        val read = readGrowth(GrowthFields(ounces = "12"), imperial)
        assertNull(read.error)
        assertEquals(340, read.values.weightGrams)
    }

    @Test
    fun `ounces beyond a pound carry rather than being rejected`() {
        // A parent typing 16 oz has not made a mistake worth an error message.
        val read = readGrowth(GrowthFields(pounds = "3", ounces = "16"), imperial)
        assertNull(read.error)
        assertEquals(1814, read.values.weightGrams)
    }

    @Test
    fun `an untouched prefill keeps the stored value instead of round-tripping it`() {
        // 3460 g renders as "7 lb 10 oz", and re-parsing that pair gives 3459 g. Opening a sheet and
        // pressing Save must not change the log, which is the Phase-9 bottle defect in another unit.
        val stored = GrowthValues(weightGrams = 3460, lengthMm = 571, headMm = 385)
        val seed = growthFieldsFor(stored, imperial)
        assertEquals("7", seed.pounds)
        assertEquals("10", seed.ounces)

        val untouched = readGrowth(seed, imperial, stored)
        assertNull(untouched.error)
        assertEquals(3460, untouched.values.weightGrams)
        assertEquals(571, untouched.values.lengthMm)
        assertEquals(385, untouched.values.headMm)
    }

    @Test
    fun `editing one field leaves the other stored values alone`() {
        val stored = GrowthValues(weightGrams = 3460, lengthMm = 571, headMm = 385)
        val seed = growthFieldsFor(stored, metric)
        val edited = readGrowth(seed.copy(weight = "3.6"), metric, stored)
        assertNull(edited.error)
        assertEquals(3600, edited.values.weightGrams)
        assertEquals(571, edited.values.lengthMm)
        assertEquals(385, edited.values.headMm)
    }

    @Test
    fun `the sheet echoes a stored weight at 10 g, unlike the card's one decimal`() {
        // The card shows "6.4 kg" for 6350 g. The measurement box must show 6.35, or a parent cannot
        // see - let alone correct - the 50 g between them.
        val stored = GrowthValues(weightGrams = 6350)
        val seed = growthFieldsFor(stored, metric)
        assertEquals("6.35", seed.weight)
        assertEquals(6350, readGrowth(seed, metric, stored).values.weightGrams)
        assertEquals("1.37", growthFieldsFor(GrowthValues(weightGrams = 1370), metric).weight)
    }

    @Test
    fun `the box keeps finer grams than it can show, and only a real edit changes them`() {
        // 6355 g is 5 g finer than a 2-decimal kg box can show. Leaving the box alone keeps 6355, and
        // so does typing the same digits back - the rule keys on the text, not on the gesture. What
        // does change storage is a different number.
        val stored = GrowthValues(weightGrams = 6355)
        val seed = growthFieldsFor(stored, metric)
        assertEquals("6.36", seed.weight)
        assertEquals(6355, readGrowth(seed, metric, stored).values.weightGrams)
        assertEquals(6355, readGrowth(seed.copy(weight = "6.36"), metric, stored).values.weightGrams)
        assertEquals(6400, readGrowth(seed.copy(weight = "6.4"), metric, stored).values.weightGrams)
    }

    @Test
    fun `a typo is an error, not a measurement`() {
        val read = readGrowth(GrowthFields(weight = "6.3.5"), metric)
        assertNotNull(read.error)
        assertNull(read.values.weightGrams)
    }

    @Test
    fun `implausible values are refused with a unit-aware message`() {
        val tooSmall = readGrowth(GrowthFields(weight = "0.05"), metric)
        assertNotNull(tooSmall.error)
        assertTrue("message was ${tooSmall.error}", tooSmall.error!!.contains("kg"))

        val tooBig = readGrowth(GrowthFields(weight = "60"), metric)
        assertNotNull(tooBig.error)

        val longLength = readGrowth(GrowthFields(length = "900"), metric)
        assertNotNull(longLength.error)
        assertTrue("message was ${longLength.error}", longLength.error!!.contains("cm"))
    }

    @Test
    fun `a length typo does not stop a valid weight being read`() {
        // The error blocks Save, but the fields that ARE readable still come back, so the sheet can
        // show what it understood alongside what it did not.
        val read = readGrowth(GrowthFields(weight = "6.35", length = "abc"), metric)
        assertNotNull(read.error)
        assertEquals(6350, read.values.weightGrams)
        assertNull(read.values.lengthMm)
    }

    @Test
    fun `switching to imperial changes the boxes, not the stored grams`() {
        val stored = GrowthValues(weightGrams = 6350, lengthMm = 571)
        val metricSeed = growthFieldsFor(stored, metric)
        val imperialSeed = growthFieldsFor(stored, imperial)
        assertEquals("6.35", metricSeed.weight)
        assertEquals("14", imperialSeed.pounds)
        assertEquals("0", imperialSeed.ounces)
        assertEquals("22.48", imperialSeed.length)

        assertEquals(6350, readGrowth(metricSeed, metric, stored).values.weightGrams)
        assertEquals(6350, readGrowth(imperialSeed, imperial, stored).values.weightGrams)
    }
}
