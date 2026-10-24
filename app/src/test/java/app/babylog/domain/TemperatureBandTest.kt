package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The band boundaries, and the one invariant the bands exist to keep: **the marker agrees with the number
 * printed beside it**. Every threshold is therefore checked through the display rounding, not against a
 * raw double — `formatTemperature` rounds to one decimal, and a pill that disagreed with the reading next
 * to it would be the same class of defect as a tinted fever nobody could see.
 */
class TemperatureBandTest {

    @Test
    fun `the band boundaries are half open and pinned at every edge`() {
        assertEquals(TemperatureBand.LOW, temperatureBand(36.4))
        assertEquals(TemperatureBand.NORMAL, temperatureBand(NORMAL_MIN_C))
        assertEquals(TemperatureBand.NORMAL, temperatureBand(37.5))
        assertEquals(TemperatureBand.MILD_FEVER, temperatureBand(MILD_FEVER_MIN_C))
        assertEquals(TemperatureBand.MILD_FEVER, temperatureBand(37.9))
        // Exactly 38.0 stays a fever: FEVER_C keeps its meaning and every older caller keeps its answer.
        assertEquals(TemperatureBand.FEVER, temperatureBand(FEVER_C))
        assertEquals(TemperatureBand.FEVER, temperatureBand(38.1))
        assertEquals(TemperatureBand.FEVER, temperatureBand(41.0))
    }

    @Test
    fun `no reading is no band`() {
        assertNull(temperatureBand(null))
    }

    @Test
    fun `a reading is classified as the number it is displayed as`() {
        // 37.99 SHOWS as 38.0, so it is a fever - a `Mild` pill beside "38.0 °C" would contradict itself.
        assertEquals("38", formatTemperature(37.99).removeSuffix(" °C"))
        assertEquals(TemperatureBand.FEVER, temperatureBand(37.99))

        // 37.94 shows as 37.9 and stays mild.
        assertEquals("37.9", formatTemperature(37.94).removeSuffix(" °C"))
        assertEquals(TemperatureBand.MILD_FEVER, temperatureBand(37.94))

        // 36.46 shows as 36.5, the bottom of NORMAL, and must not be classified from the raw value.
        assertEquals("36.5", formatTemperature(36.46).removeSuffix(" °C"))
        assertEquals(TemperatureBand.NORMAL, temperatureBand(36.46))
    }

    @Test
    fun `every hundredth of a degree agrees with its own display text`() {
        // 35.00 .. 39.00 in 0.01 steps: a marker and a printed number can never disagree in this range.
        for (hundredths in 3500..3900) {
            val celsius = hundredths / 100.0
            val shown = formatTemperature(celsius).removeSuffix(" °C").toDouble()
            assertEquals(
                "band for $celsius must match the band for its display $shown",
                temperatureBand(shown),
                temperatureBand(celsius),
            )
        }
    }

    @Test
    fun `the band never changes with the temperature unit`() {
        for (celsius in listOf(36.1, 36.5, 37.2, 37.6, 37.9, 38.0, 38.4, 39.2)) {
            // The stored value is Celsius; the °F preference is a display choice only, so the band computed
            // from the same stored value has to stay put whatever is shown.
            assertEquals("band for $celsius", temperatureBand(celsius), temperatureBand(celsius))
            // Round-tripping through the Fahrenheit display and back must not cross a boundary either.
            val asFahrenheit = fromCelsius(celsius, TemperatureUnit.FAHRENHEIT)
            assertEquals(
                "band for $celsius returning from $asFahrenheit °F",
                temperatureBand(celsius),
                temperatureBand(toCelsius(asFahrenheit, TemperatureUnit.FAHRENHEIT)),
            )
        }
    }

    @Test
    fun `isFever is the FEVER band and nothing else`() {
        assertFalse(isFever(null))
        assertFalse(isFever(36.2))
        assertFalse(isFever(37.5))
        // A mild fever is NOT a fever: the word and the fill are reserved for the top band.
        assertFalse(isFever(37.6))
        assertFalse(isFever(37.9))
        assertTrue(isFever(38.0))
        assertTrue(isFever(38.4))
        // The marker helper drops NORMAL only, so Low keeps its outline on the cards.
        assertEquals(TemperatureBand.LOW, markedBand(36.2))
        assertEquals(TemperatureBand.MILD_FEVER, markedBand(37.7))
        assertEquals(TemperatureBand.FEVER, markedBand(38.4))
        assertNull("NORMAL has no pill at all", markedBand(37.0))
        assertNull(markedBand(null))
    }
}
