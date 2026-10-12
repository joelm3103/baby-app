package app.babylog.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeTest {

    private val birth = LocalDate.of(2025, 12, 31) // the children, 30 weeks
    private val today = LocalDate.of(2026, 10, 12)

    // ---- the number the reference app itself displayed ---------------------------------

    @Test
    fun `chronological age matches the value the reference app showed for the same child`() {
        // the reference app's Child screen read "9 months, 1 week, 5 days" for birth a date set in Settings on 12 Oct 2026.
        // Reproducing that exactly is the cheapest available proof our month arithmetic is right.
        assertEquals(AgeParts(months = 9, weeks = 1, days = 5), ageParts(birth, today))
        assertEquals("9 months, 1 week, 5 days", formatAge(ageParts(birth, today)))
    }

    @Test
    fun `chronological age in days`() {
        // Non-leap 2026: Jan 31 + Feb 28 + Mar 31 + Apr 30 + May 31 + Jun 30 + Jul 31 + Aug 31 + Sep 30
        // = 273, plus 12 days of October.
        assertEquals(285, ageDays(birth, today))
    }

    // ---- adjusted age, the reason this file exists ------------------------

    @Test
    fun `30 weeks shifts the adjusted birth date by 70 days`() {
        val first-twin = child(gestationalAgeDays = 210)
        assertEquals(70, first-twin.prematureDays)
        assertEquals(LocalDate.of(2026, 3, 11), adjustedBirthDate(first-twin))
    }

    @Test
    fun `adjusted age is 70 days behind chronological for a 30-week preemie`() {
        val first-twin = child(gestationalAgeDays = 210)
        val chronological = ageDaysForCharts(first-twin, today, useAdjustedAge = false)
        val adjusted = ageDaysForCharts(first-twin, today, useAdjustedAge = true)
        assertEquals(285, chronological)
        assertEquals(215, adjusted)
        assertEquals(70, chronological - adjusted)
    }

    @Test
    fun `adjusted age reads as a different age entirely, not a minor correction`() {
        val first-twin = child(gestationalAgeDays = 210)
        assertEquals("9 months, 1 week, 5 days", ageText(first-twin, today, useAdjustedAge = false))
        assertEquals("7 months, 1 day", ageText(first-twin, today, useAdjustedAge = true))
    }

    @Test
    fun `a term child has no adjustment`() {
        val term = child(gestationalAgeDays = 280)
        assertFalse(term.isPreterm)
        assertEquals(term.birthDate, adjustedBirthDate(term))
        assertEquals(
            ageDaysForCharts(term, today, useAdjustedAge = false),
            ageDaysForCharts(term, today, useAdjustedAge = true),
        )
    }

    @Test
    fun `the child's own toggle is the default`() {
        val adjusted = child(gestationalAgeDays = 210, useAdjustedAge = true)
        val notAdjusted = child(gestationalAgeDays = 210, useAdjustedAge = false)
        assertEquals(215, ageDaysForCharts(adjusted, today))
        assertEquals(285, ageDaysForCharts(notAdjusted, today))
    }

    @Test
    fun `32 weeks is 56 days premature`() {
        // a second data point, so the arithmetic is not hardcoded to one child
        assertEquals(56, child(gestationalAgeDays = 224).prematureDays)
    }

    // ---- formatting -------------------------------------------------------

    @Test
    fun `zero days never renders an empty string`() {
        assertEquals("0 days", formatAge(AgeParts(0, 0, 0)))
        assertEquals("0 days", formatAge(ageParts(today, today)))
    }

    @Test
    fun `units are omitted when zero and singular when one`() {
        assertEquals("1 week", formatAge(AgeParts(0, 1, 0)))
        assertEquals("5 days", formatAge(AgeParts(0, 0, 5)))
        assertEquals("1 month", formatAge(AgeParts(1, 0, 0)))
        assertEquals("2 months, 3 days", formatAge(AgeParts(2, 0, 3)))
        assertEquals("1 month, 1 week, 1 day", formatAge(AgeParts(1, 1, 1)))
        assertEquals("2 months, 2 weeks, 2 days", formatAge(AgeParts(2, 2, 2)))
    }

    @Test
    fun `a future date yields zero rather than a negative age`() {
        assertEquals(AgeParts(0, 0, 0), ageParts(today, birth))
        assertEquals(0, ageDays(today, birth))
    }

    private fun child(
        gestationalAgeDays: Int,
        useAdjustedAge: Boolean = true,
    ) = Child(
        id = "first-twin", name = "First Twin", birthDate = birth, sex = Sex.BOY,
        gestationalAgeDays = gestationalAgeDays, useAdjustedAge = useAdjustedAge, sortOrder = 0,
    )
}
