package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The amount field's arithmetic, which has to be right at 3am on a phone keyboard.
 *
 * Plain JUnit: this is the part of the bottle sheet a wrong answer would get silently stored as a volume,
 * so it is tested away from Compose and away from the phone.
 */
class BottleMathTest {

    @Test
    fun aPlainNumberIsItsOwnValue() {
        assertEquals(6.0, evaluateAmount("6")!!, 0.0001)
        assertEquals(2.5, evaluateAmount("2.5")!!, 0.0001)
        assertEquals(2.5, evaluateAmount("  2.5  ")!!, 0.0001)
        assertEquals(0.5, evaluateAmount(".5")!!, 0.0001)
    }

    @Test
    fun madeMinusLeftIsTheAmountDrunk() {
        // The case this exists for: a 6 oz bottle with 2 oz left is 4 oz drunk.
        assertEquals(4.0, evaluateAmount("6-2")!!, 0.0001)
        assertEquals(4.0, evaluateAmount("6 - 2")!!, 0.0001)
        assertEquals(4.0, evaluateAmount("6 − 2")!!, 0.0001)
        assertEquals(1.5, evaluateAmount("4-2.5")!!, 0.0001)
    }

    @Test
    fun theFourOperationsAllWorkInTheDisplayUnit() {
        assertEquals(8.0, evaluateAmount("6+2")!!, 0.0001)
        assertEquals(12.0, evaluateAmount("6*2")!!, 0.0001)
        assertEquals(12.0, evaluateAmount("6×2")!!, 0.0001)
        assertEquals(3.0, evaluateAmount("6/2")!!, 0.0001)
        assertEquals(3.0, evaluateAmount("6÷2")!!, 0.0001)
    }

    @Test
    fun multiplicationBeforeAddition() {
        // Left-to-right evaluation would make this 10, which is a wrong volume nobody would question.
        assertEquals(8.0, evaluateAmount("2+3*2")!!, 0.0001)
        assertEquals(3.0, evaluateAmount("6-2-1")!!, 0.0001)
        assertEquals(2.0, evaluateAmount("(6-2)/2")!!, 0.0001)
    }

    @Test
    fun anUnreadableSumIsNullRatherThanAGuess() {
        assertNull(evaluateAmount(""))
        assertNull(evaluateAmount("   "))
        assertNull(evaluateAmount("6-"))
        assertNull(evaluateAmount("-"))
        assertNull(evaluateAmount("6-2+"))
        assertNull(evaluateAmount("6..2"))
        assertNull(evaluateAmount("6,2"))
        assertNull(evaluateAmount("abc"))
        assertNull(evaluateAmount("6oz"))
        assertNull(evaluateAmount("1e3"))
        assertNull(evaluateAmount("(6-2"))
        assertNull(evaluateAmount(")6("))
        // Division by zero is not an amount, and Infinity in a stored volume is how a total becomes NaN.
        assertNull(evaluateAmount("6/0"))
    }

    @Test
    fun theStoredValueIsMillilitresWhateverUnitWasTyped() {
        // 4 fl oz is 118.3 ml, and 120 ml typed in a metric phone stays 120.
        assertEquals(118, amountMlFor("6-2", VolumeUnit.FLUID_OUNCES))
        assertEquals(120, amountMlFor("120", VolumeUnit.MILLILITRES))
        assertEquals(60, amountMlFor("120-60", VolumeUnit.MILLILITRES))
        assertNull(amountMlFor("6-", VolumeUnit.FLUID_OUNCES))
        assertNull(amountMlFor("", VolumeUnit.FLUID_OUNCES))
    }

    @Test
    fun equalsPutsTheValueBackIntoTheField() {
        // `=` replaces what is in the field with the answer, so what it returns IS what gets stored. The
        // rounding is the field's rounding, so the number shown is the number kept.
        assertEquals("14", resolvedAmountText("7*2", VolumeUnit.FLUID_OUNCES))
        assertEquals("14", resolvedAmountText("7×2", VolumeUnit.FLUID_OUNCES))
        assertEquals("4", resolvedAmountText("6-2", VolumeUnit.FLUID_OUNCES))
        assertEquals("2.3", resolvedAmountText("7/3", VolumeUnit.FLUID_OUNCES))
        assertEquals("150", resolvedAmountText("100+50", VolumeUnit.MILLILITRES))
        assertEquals("8", resolvedAmountText("2*2*2", VolumeUnit.FLUID_OUNCES))
        // A plain number resolves to itself, so pressing `=` twice is harmless.
        assertEquals("4", resolvedAmountText("4", VolumeUnit.FLUID_OUNCES))
        // Unreadable stays unreadable: the sheet keeps the text and says why rather than inventing a value.
        assertNull(resolvedAmountText("6-", VolumeUnit.FLUID_OUNCES))
        assertNull(resolvedAmountText("", VolumeUnit.FLUID_OUNCES))
    }
}
