package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    // --- exact conversion factors -----------------------------------------

    @Test
    fun `inch is exactly 25_4 millimetres`() {
        assertEquals(25.4, toMillimetres(1.0, LengthUnit.INCHES), 1e-12)
        assertEquals(2.54, toMillimetres(0.1, LengthUnit.INCHES), 1e-12)
    }

    @Test
    fun `pound and ounce match their defined values`() {
        assertEquals(453.59237, toGrams(1.0, WeightUnit.POUNDS_OUNCES), 1e-9)
        assertEquals(28.349523125, toGrams(0, 1), 1e-9)
        assertEquals(453.59237, toGrams(1, 0), 1e-9)
    }

    @Test
    fun `US fluid ounce is 29_5735295625 millilitres`() {
        assertEquals(29.5735295625, toMillilitres(1.0, VolumeUnit.FLUID_OUNCES), 1e-12)
        assertEquals(29.5735295625, toMillilitres(1.0, VolumeUnit.FLUID_OUNCES), 1e-12)
        assertEquals(30.0, toMillilitres(30.0, VolumeUnit.MILLILITRES), 1e-12)
        assertEquals("1 oz", formatVolume(30.0, VolumeUnit.FLUID_OUNCES)) // 1.014 oz -> trimmed
    }

    @Test
    fun `fromMillilitres round trips through toMillilitres`() {
        // The amount stepper steps in display units and converts once on save, so this must be exact.
        assertEquals(120.0, fromMillilitres(120.0, VolumeUnit.MILLILITRES), 1e-12)
        assertEquals(4.0, fromMillilitres(toMillilitres(4.0, VolumeUnit.FLUID_OUNCES), VolumeUnit.FLUID_OUNCES), 1e-12)
        assertEquals(2.5, fromMillilitres(toMillilitres(2.5, VolumeUnit.FLUID_OUNCES), VolumeUnit.FLUID_OUNCES), 1e-12)
    }

    // --- weight ------------------------------------------------------------
    @Test
    fun `weight in kilos has one decimal`() {
        assertEquals("5.4 kg", formatWeight(5443.0, WeightUnit.KILOGRAMS))
        assertEquals("3.3 kg", formatWeight(3346.0, WeightUnit.KILOGRAMS))
    }

    @Test
    fun `weight in pounds and ounces splits correctly`() {
        // 5443 g is 12.00 lb -> the exact value the reference app displayed as "12 lb 0 oz"
        assertEquals("12 lb 0 oz", formatWeight(5443.0, WeightUnit.POUNDS_OUNCES))
        assertEquals(12 to 0, poundsOunces(5443.0))
        assertEquals(9 to 15, poundsOunces(4500.0)) // 4500 g = 158.73 oz -> 159 -> 9 lb 15 oz
    }

    @Test
    fun `rounding sixteen ounces carries into a pound instead of printing 0 lb 16 oz`() {
        // 453 g = 15.98 oz. Rounding the ounce component alone yields 16 -> "0 lb 16 oz".
        assertEquals("1 lb 0 oz", formatWeight(453.0, WeightUnit.POUNDS_OUNCES))
        assertEquals(1 to 0, poundsOunces(453.0))
        // just under the carry: 430 g = 15.17 oz -> 15, so it must NOT carry
        assertEquals("0 lb 15 oz", formatWeight(430.0, WeightUnit.POUNDS_OUNCES))
        // and 440 g is 15.52 oz, which DOES round to 16 and must therefore carry
        assertEquals("1 lb 0 oz", formatWeight(440.0, WeightUnit.POUNDS_OUNCES))
    }

    // --- length ------------------------------------------------------------

    @Test
    fun `length in both systems`() {
        assertEquals("57.1 cm", formatLength(571.0, LengthUnit.CENTIMETRES))
        assertEquals("22.5 in", formatLength(571.0, LengthUnit.INCHES)) // 571mm = 22.48in
        assertEquals("50 cm", formatLength(500.0, LengthUnit.CENTIMETRES))
    }

    // --- volume ------------------------------------------------------------

    @Test
    fun `volume in both systems`() {
        assertEquals("120 ml", formatVolume(120.0, VolumeUnit.MILLILITRES))
        assertEquals("3 oz", formatVolume(90.0, VolumeUnit.FLUID_OUNCES))   // 3.04 -> trimmed
        assertEquals("4 oz", formatVolume(118.0, VolumeUnit.FLUID_OUNCES))  // 3.99 -> rounds to 4
        assertEquals("4.1 oz", formatVolume(120.0, VolumeUnit.FLUID_OUNCES))
    }

    @Test
    fun `steppers are unit appropriate`() {
        assertEquals(0.5, volumeStep(VolumeUnit.FLUID_OUNCES), 1e-9)
        assertEquals(10.0, volumeStep(VolumeUnit.MILLILITRES), 1e-9)
        assertEquals(0.25, lengthStep(LengthUnit.INCHES), 1e-9)
        assertEquals(0.01, weightStep(WeightUnit.KILOGRAMS), 1e-9)
    }

    // --- temperature: Celsius only -----------------------------------------

    @Test
    fun `temperature is always celsius with one decimal`() {
        assertEquals("36.8 °C", formatTemperature(36.8))
        assertEquals("38 °C", formatTemperature(38.0))
        assertEquals("37.2 °C", formatTemperature(37.24))
    }

    // --- preferences -------------------------------------------------------

    @Test
    fun `system is derived from the three selections`() {
        assertEquals(UnitSystem.IMPERIAL, UnitPreferences.Imperial.system)
        assertEquals(UnitSystem.METRIC, UnitPreferences.Metric.system)
        assertEquals(
            UnitSystem.CUSTOM,
            UnitPreferences(LengthUnit.INCHES, WeightUnit.KILOGRAMS, VolumeUnit.MILLILITRES).system,
        )
        // a mix of exactly one imperial family is still CUSTOM, not IMPERIAL
        assertEquals(
            UnitSystem.CUSTOM,
            UnitPreferences(LengthUnit.INCHES, WeightUnit.KILOGRAMS, VolumeUnit.FLUID_OUNCES).system,
        )
    }

    @Test
    fun `metric is the default`() {
        assertEquals(UnitPreferences.Metric, UnitPreferences())
    }

    // --- round trip --------------------------------------------------------

    @Test
    fun `converting to canonical and back is lossless enough to be stable`() {
        val grams = toMillilitres(4.0, VolumeUnit.FLUID_OUNCES)
        assertEquals("4 oz", formatVolume(grams, VolumeUnit.FLUID_OUNCES))
        val mm = toMillimetres(22.5, LengthUnit.INCHES)
        assertEquals("22.5 in", formatLength(mm, LengthUnit.INCHES))
    }
}
