package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How a dose reads. Small, but it appears on the card, in the entry list and in History, and a dose is
 * the one value in this app that a parent may later repeat to a clinician — so "2.5 ml" must not be
 * printed as "2.50" by one screen and "2.5" by another.
 */
class DoseTest {

    @Test
    fun `a whole dose drops the decimal`() {
        assertEquals("5 ml", formatDose(5.0, DoseUnit.MILLILITRES))
        assertEquals("1 drop", formatDose(1.0, DoseUnit.DROPS))
        assertEquals("300 mg", formatDose(300.0, DoseUnit.MILLIGRAMS))
    }

    @Test
    fun `the unit word fits the amount`() {
        // The bug this pins: a fixed label wrote "1 drops" onto a fever card.
        assertEquals("1 drop", formatDose(1.0, DoseUnit.DROPS))
        assertEquals("3 drops", formatDose(3.0, DoseUnit.DROPS))
        assertEquals("1 sachet", formatDose(1.0, DoseUnit.SACHET))
        assertEquals("2 sachets", formatDose(2.0, DoseUnit.SACHET))
        assertEquals("1 tablet", formatDose(1.0, DoseUnit.TABLET))
        assertEquals("2 tablets", formatDose(2.0, DoseUnit.TABLET))
        // A fraction of a measure is still one measure: "half a sachet", not "half a sachets".
        assertEquals("0.5 sachet", formatDose(0.5, DoseUnit.SACHET))
        assertEquals("1.5 sachets", formatDose(1.5, DoseUnit.SACHET))
        // Measures that have no plural form stay as they are.
        assertEquals("1 ml", formatDose(1.0, DoseUnit.MILLILITRES))
        assertEquals("2 ml", formatDose(2.0, DoseUnit.MILLILITRES))
    }

    @Test
    fun `a fractional dose keeps its digits`() {
        assertEquals("2.5 ml", formatDose(2.5, DoseUnit.MILLILITRES))
        assertEquals("0.5 sachet", formatDose(0.5, DoseUnit.SACHET))
        assertEquals("0.25 ml", formatDose(0.25, DoseUnit.MILLILITRES))
    }

    @Test
    fun `half a dose is not a dose`() {
        assertNull(formatDose(null, DoseUnit.MILLILITRES))
        assertNull(formatDose(2.5, null))
        assertNull(formatDose(null, null))
    }

    @Test
    fun `the unit is whatever the parent used, including other`() {
        assertEquals("1 tablet", formatDose(1.0, DoseUnit.TABLET))
        assertEquals("2 other", formatDose(2.0, DoseUnit.OTHER))
    }

    @Test
    fun `the picker label is the plural word, not the dose word`() {
        // Two different jobs: the dropdown offers "drops", a dose of one reads "1 drop".
        assertEquals("drops", DoseUnit.DROPS.label)
        assertEquals("drop", DoseUnit.DROPS.singular)
        assertEquals("ml", DoseUnit.MILLILITRES.label)
    }
}
