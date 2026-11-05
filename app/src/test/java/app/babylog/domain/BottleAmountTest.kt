package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one subtraction in Phase 26, tested rather than trusted.
 *
 * The sheet types two numbers and the app decides what is stored. That decision is the whole phase: get it
 * wrong and every total, every card and the wastage report are wrong together, in a way no screenshot would
 * show. So the rule lives in one pure function and these are its edges — including the two refusals, which
 * are the ones a tired parent is most likely to type.
 */
class BottleAmountTest {

    private val oz = VolumeUnit.FLUID_OUNCES
    private fun ounces(value: Double): Double = toMillilitres(value, oz)

    @Test
    fun `what was drunk is stored, and what was left sits beside it`() {
        val stored = bottleAmount(180.0, 30.0)!!

        assertEquals(150, stored.consumedMl)
        assertEquals(30, stored.leftBehindMl)
        // The pair is what makes the poured volume recoverable exactly: 150 + 30 is the 180 that was made.
        assertEquals(180, stored.pouredMl)
    }

    @Test
    fun `nothing left is recorded as nothing, not as a zero`() {
        val stored = bottleAmount(150.0, null)!!

        assertEquals(150, stored.consumedMl)
        // NULL and 0 are different claims: one says nobody measured a leftover, the other says somebody did
        // and it was none. Only the second belongs in a wastage count as a measurement.
        assertNull(stored.leftBehindMl)
        assertEquals(150, stored.pouredMl)
    }

    @Test
    fun `a feed with no amount is not a measured feed`() {
        val stored = bottleAmount(null, null)!!

        assertNull(stored.consumedMl)
        assertNull(stored.leftBehindMl)
        assertNull(stored.pouredMl)
    }

    @Test
    fun `a leftover with no amount beside it is refused`() {
        // The amount field is what the leftover is OF. Half the pair is not a record, and inventing the
        // other half would put a volume on a feed nobody measured.
        assertNull(bottleAmount(null, 30.0))
    }

    @Test
    fun `a leftover larger than the pour is refused`() {
        assertNull(bottleAmount(150.0, 180.0))
    }

    @Test
    fun `a negative leftover is refused rather than trusted`() {
        // `decimalInput` strips the sign, so this is unreachable from the phone — which is exactly why it
        // is checked here instead of being assumed.
        assertNull(bottleAmount(150.0, -1.0))
    }

    @Test
    fun `a bottle made and then refused stores zero drunk, and counts as waste`() {
        val stored = bottleAmount(180.0, 180.0)!!

        // Left equal to the amount is legal on purpose: a refused bottle is a real event, and it is the
        // clearest case of the wastage the phase reports.
        assertEquals(0, stored.consumedMl)
        assertEquals(180, stored.leftBehindMl)
        assertEquals(180, stored.pouredMl)
    }

    @Test
    fun `ounces are converted before subtracting, so the arithmetic is done once`() {
        val stored = bottleAmount(ounces(6.0), ounces(1.0))!!

        // 177.44 - 29.57 = 147.87, rounded ONCE to 148. Subtracting the ROUNDED display values instead
        // (177 - 30) would store 147, and every edit of that row would drift another millilitre.
        assertEquals(148, stored.consumedMl)
        assertEquals(30, stored.leftBehindMl)
        // 148 + 30 is the stored pair's own sum. It can differ from the typed 6 fl oz (177.44) by a
        // millilitre, which is why the report derives poured from the pair and never re-parses a field.
        assertEquals(178, stored.pouredMl)
    }

    @Test
    fun `decimals in the amount survive as a rounded millilitre`() {
        val stored = bottleAmount(toMillilitres(4.5, oz), toMillilitres(1.5, oz))!!

        assertEquals(3 * toMillilitres(1.0, oz), stored.consumedMl!!.toDouble(), 1.0)
        assertEquals(toMillilitres(1.5, oz), stored.leftBehindMl!!.toDouble(), 1.0)
    }
}
