package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The percentile maths, checked against WHO's own published z-score tables rather than against itself.
 *
 * Every `L`, `M`, `S` and expected value below is transcribed from WHO's `wfa-boys-zscore-expanded-tables`
 * (weight-for-age, boys) — the publisher's own file, not a mirror of it — so a wrong formula cannot pass
 * by agreeing with a wrong implementation. WHO rounds its SD columns to four significant figures, which
 * is why the tolerance is 0.005 kg: it is the source's precision, not slack.
 *
 * Pure JVM. No Android, no asset, no Room — the maths is the part that must be provable on its own.
 */
class PercentilesTest {

    // WHO weight-for-age, boys, day 0 and day 12.
    private val day0L = 0.3487
    private val day0M = 3.3464
    private val day0S = 0.14602

    private val day12L = 0.2628
    private val day12M = 3.671
    private val day12S = 0.14241

    @Test
    fun `the median is the M parameter`() {
        assertEquals(day0M, valueAtZ(0.0, day0L, day0M, day0S)!!, 1e-9)
        assertEquals(day12M, valueAtZ(0.0, day12L, day12M, day12S)!!, 1e-9)
        assertEquals(50.0, percentile(day12M, day12L, day12M, day12S)!!, 1e-6)
    }

    @Test
    fun `the inverse reproduces WHO's published SD columns`() {
        // day 0: WHO prints SD2neg 2.459 and SD2 4.419.
        assertEquals(2.459, valueAtZ(-2.0, day0L, day0M, day0S)!!, 0.005)
        assertEquals(4.419, valueAtZ(2.0, day0L, day0M, day0S)!!, 0.005)

        // day 12: WHO prints SD2neg 2.73 and SD2 4.831.
        assertEquals(2.73, valueAtZ(-2.0, day12L, day12M, day12S)!!, 0.005)
        assertEquals(4.831, valueAtZ(2.0, day12L, day12M, day12S)!!, 0.005)
    }

    @Test
    fun `a published SD column reads back as its percentile`() {
        // ±2 SD is the 2nd and the 98th percentile, which is the identity the chart relies on.
        assertEquals(2.3, percentile(2.459, day0L, day0M, day0S)!!, 0.1)
        assertEquals(97.7, percentile(4.419, day0L, day0M, day0S)!!, 0.1)
        assertEquals(2.3, percentile(2.73, day12L, day12M, day12S)!!, 0.1)
        assertEquals(97.7, percentile(4.831, day12L, day12M, day12S)!!, 0.1)
    }

    @Test
    fun `percentiles are monotonic in the measurement`() {
        val ascending = listOf(2.5, 3.0, 3.671, 4.0, 4.831, 5.503)
            .map { percentile(it, day12L, day12M, day12S)!! }
        assertEquals(ascending.sorted(), ascending)
    }

    @Test
    fun `a value above the median is above the 50th percentile`() {
        val above = percentile(4.0, day12L, day12M, day12S)!!
        assertTrue("expected >50, got $above", above > 50.0)
        val below = percentile(3.2, day12L, day12M, day12S)!!
        assertTrue("expected <50, got $below", below < 50.0)
    }

    @Test
    fun `L of zero uses the log form instead of dividing by zero`() {
        // The naive ((X/M)^L - 1)/(L*S) is 0/0 here and returns NaN without any error. WHO's tables do
        // contain L at or near zero at some ages.
        val mean = valueAtZ(0.0, 0.0, 3.0, 0.1)!!
        assertEquals(3.0, mean, 1e-9)
        val plus1 = valueAtZ(1.0, 0.0, 3.0, 0.1)!!
        assertEquals(3.0 * Math.exp(0.1), plus1, 1e-9)
        assertEquals(0.0, zScore(3.0, 0.0, 3.0, 0.1)!!, 1e-12)
        assertEquals(50.0, percentile(3.0, 0.0, 3.0, 0.1)!!, 1e-9)
        assertTrue("a near-zero L must take the same branch", valueAtZ(1.0, 1e-12, 3.0, 0.1)!!.isFinite())
    }

    @Test
    fun `a round trip through the inverse returns the same percentile`() {
        for (z in listOf(-3.0, -1.5, -0.5, 0.0, 0.5, 1.5, 3.0)) {
            val value = valueAtZ(z, day12L, day12M, day12S)!!
            assertEquals(normalCdf(z) * 100.0, percentile(value, day12L, day12M, day12S)!!, 1e-6)
        }
    }

    @Test
    fun `meaningless questions return null rather than a number`() {
        assertNull("0 kg is not in any percentile", percentile(0.0, day12L, day12M, day12S))
        assertNull(percentile(-1.0, day12L, day12M, day12S))
        assertNull(percentile(3.671, day12L, 0.0, day12S))
        assertNull(percentile(3.671, day12L, day12M, 0.0))
        assertNull(zScore(0.0, day12L, day12M, day12S))
        // The inverse's domain: 1 + L*S*z must stay positive.
        assertNull(valueAtZ(-4.0, 0.5, 3.0, 1.0))
        assertNull(valueAtZ(0.0, 0.0, 0.0, 0.1))
    }

    @Test
    fun `the normal CDF matches published quantiles`() {
        assertEquals(0.5, normalCdf(0.0), 1e-12)
        assertEquals(0.975, normalCdf(1.959964), 1e-5)
        assertEquals(0.025, normalCdf(-1.959964), 1e-5)
        assertEquals(0.90, normalCdf(zForCurve(90)!!), 1e-6)
        assertEquals(0.02, normalCdf(zForCurve(2)!!), 1e-6)
        assertTrue(normalCdf(1.0) > normalCdf(0.0))
        assertEquals(1.0, normalCdf(8.0), 1e-9)
        assertEquals(0.0, normalCdf(-8.0), 1e-9)
    }

    @Test
    fun `the curve bundle is the seven published percentiles`() {
        assertEquals(listOf(2, 10, 25, 50, 75, 90, 98), PERCENTILE_CURVES)
        assertEquals(0.0, zForCurve(50)!!, 1e-12)
        assertNull("no curve is drawn at 42", zForCurve(42))
        assertTrue(zForCurve(2)!! < zForCurve(10)!!)
    }

    @Test
    fun `the percentile label reads as an ordinal`() {
        assertEquals("1st", percentileLabel(1.0))
        assertEquals("2nd", percentileLabel(2.0))
        assertEquals("3rd", percentileLabel(3.0))
        assertEquals("4th", percentileLabel(4.4))
        assertEquals("11th", percentileLabel(11.0))
        assertEquals("12th", percentileLabel(12.0))
        assertEquals("13th", percentileLabel(13.0))
        assertEquals("21st", percentileLabel(21.0))
        assertEquals("42nd", percentileLabel(42.4))
        assertEquals("99th", percentileLabel(98.6))
    }

    @Test
    fun `a value off the bottom or top of the curves is not rounded into a percentile`() {
        // Found on the S23: 5.4 kg at 218 adjusted days is z = -3.9, a 0.005th percentile, and clamping
        // it to "1st" claimed the child was ON the 1st percentile line rather than below every curve.
        assertEquals("<1st", percentileLabel(0.005))
        assertEquals("<1st", percentileLabel(0.4))
        assertEquals(">99th", percentileLabel(99.6))
        assertEquals(">99th", percentileLabel(100.0))
        // The boundary itself still reads as a percentile.
        assertEquals("1st", percentileLabel(0.5))
        assertEquals("99th", percentileLabel(99.5))
        assertEquals("—", percentileLabel(Double.NaN))
    }
}
