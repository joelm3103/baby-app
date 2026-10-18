package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.DoseUnit
import app.babylog.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the health and vaccine sheets would STORE, tested without a composable.
 *
 * The interesting cases are the ones that are silent when wrong: a Fahrenheit reading accepted as a
 * Celsius one, a stored temperature rewritten by opening and saving the sheet, and a vaccine saved
 * with no name at all.
 */
class HealthEntryTest {

    /** A fixed instant, so the "newest wins" hints do not depend on the wall clock. */
    private val t0 = 1_760_000_000_000L

    @Test
    fun `a blank sheet reads as nothing recorded, with no error`() {
        val read = readHealth(HealthFields())
        assertNull(read.error)
        assertTrue(read.values.isEmpty)
        assertNull(read.values.temperatureC)
        assertNull(read.values.medication)
    }

    @Test
    fun `a temperature is stored in celsius, as typed`() {
        val read = readHealth(HealthFields(temperature = "38.2"))
        assertNull(read.error)
        assertEquals(38.2, read.values.temperatureC!!, 0.0)
        assertFalse(read.values.isEmpty)
    }

    @Test
    fun `a receipt of medication alone is a savable record`() {
        val read = readHealth(HealthFields(medication = "  Calpol 2.5 ml  "))
        assertNull(read.error)
        assertEquals("Calpol 2.5 ml", read.values.medication)
        assertNull(read.values.temperatureC)
        assertFalse(read.values.isEmpty)
    }

    @Test
    fun `a fever reading is kept exactly, not rounded on the way in`() {
        val read = readHealth(HealthFields(temperature = "38.05"))
        assertEquals(38.05, read.values.temperatureC!!, 0.0)
        assertTrue(isFever(read.values.temperatureC))
    }

    @Test
    fun `text that is not a number is refused`() {
        val read = readHealth(HealthFields(temperature = "hot"))
        assertNotNull(read.error)
        assertNull(read.values.temperatureC)
    }

    @Test
    fun `a fahrenheit reading typed into the celsius box is refused`() {
        // 100.4 F is what a parent with a US thermometer reads off the display. Storing it as 100.4 C
        // would be a confident wrong answer with no error message anywhere, which is what the range is for.
        val read = readHealth(HealthFields(temperature = "100.4"))
        assertNotNull(read.error)
        assertNull(read.values.temperatureC)
    }

    @Test
    fun `the plausible range includes its endpoints and excludes a tenth outside`() {
        assertNull(readHealth(HealthFields(temperature = "30")).error)
        assertNull(readHealth(HealthFields(temperature = "45")).error)
        assertNotNull(readHealth(HealthFields(temperature = "29.9")).error)
        assertNotNull(readHealth(HealthFields(temperature = "45.1")).error)
    }

    @Test
    fun `an untouched prefill keeps the stored double exactly`() {
        // 36.55 renders as "36.6" (one decimal, the card's own convention). Re-parsing that would store
        // 36.6 and silently change the log - the same defect Phase 9 found in the bottle amount.
        val stored = HealthValues(temperatureC = 36.55, medication = "Vitamin D")
        val seed = healthFieldsFor(stored)
        assertEquals("36.6", seed.temperature)

        val untouched = readHealth(seed, stored)
        assertNull(untouched.error)
        assertEquals(36.55, untouched.values.temperatureC!!, 0.0)
        assertEquals("Vitamin D", untouched.values.medication)
    }

    @Test
    fun `editing a prefill stores what was typed`() {
        val stored = HealthValues(temperatureC = 36.55)
        val typed = healthFieldsFor(stored).copy(temperature = "39.1")
        val read = readHealth(typed, stored)
        assertEquals(39.1, read.values.temperatureC!!, 0.0)
        assertTrue(isFever(read.values.temperatureC))
    }

    @Test
    fun `editable text matches the card's own format`() {
        assertEquals("38.2", editableTemperature(38.2))
        assertEquals("38", editableTemperature(38.0))
    }

    @Test
    fun `fever starts at 38 degrees, and a missing temperature is not a fever`() {
        assertFalse(isFever(37.9))
        assertFalse(isFever(null))
        assertTrue(isFever(38.0))
        assertTrue(isFever(38.4))
    }

    @Test
    fun `a vaccine needs a name`() {
        assertNull(readVaccine(VaccineFields()).vaccineName)
        assertNull(readVaccine(VaccineFields(vaccineName = "   ")).vaccineName)
        assertEquals("6-in-1", readVaccine(VaccineFields(vaccineName = " 6-in-1 ")).vaccineName)
    }

    // ---- the dose ----------------------------------------------------------

    @Test
    fun `a dose is read as an amount plus the measure it was given in`() {
        val read = readHealth(
            HealthFields(medication = "Calpol", dose = "2.5", doseUnit = DoseUnit.MILLILITRES),
        )
        assertNull(read.error)
        assertEquals(2.5, read.values.doseAmount!!, 0.0)
        assertEquals(DoseUnit.MILLILITRES, read.values.doseUnit)
        assertEquals("Calpol", read.values.medication)
    }

    @Test
    fun `an amount with no unit is an error, not a silent drop`() {
        // The parent typed 2.5. Saving without it would be the log changing under their hands, which is
        // the same class of defect as the bottle amount Phase 9 found.
        val read = readHealth(HealthFields(medication = "Calpol", dose = "2.5"))

        assertNotNull(read.error)
        assertNull(read.values.doseAmount)
        assertNull(read.values.doseUnit)
    }

    @Test
    fun `a unit with no amount is simply no dose`() {
        // Nothing was typed, so nothing is lost - and a half-filled picker must not block the save.
        val read = readHealth(
            HealthFields(medication = "Calpol", doseUnit = DoseUnit.MILLILITRES),
        )

        assertNull(read.error)
        assertNull(read.values.doseAmount)
        assertNull(read.values.doseUnit)
        assertFalse(read.values.isEmpty)
    }

    @Test
    fun `a dose that is not a number, or is zero or absurd, is refused`() {
        fun dose(text: String) = readHealth(
            HealthFields(medication = "Calpol", dose = text, doseUnit = DoseUnit.MILLILITRES),
        )

        assertNotNull(dose("lots").error)
        assertNotNull(dose("0").error)
        assertNotNull(dose("5000").error)
        assertNull(dose("0.5").error)
    }

    @Test
    fun `an untouched prefill keeps the stored dose exactly`() {
        // 0.333 renders as "0.33"; re-parsing that on save would store a different number than the one
        // the parent entered, so the seed-equality guard keeps the stored value verbatim.
        val stored = HealthValues(medication = "Calpol", doseAmount = 0.333, doseUnit = DoseUnit.MILLILITRES)
        val seed = healthFieldsFor(stored)
        assertEquals("0.33", seed.dose)

        val untouched = readHealth(seed, stored)
        assertNull(untouched.error)
        assertEquals(0.333, untouched.values.doseAmount!!, 0.0)
        assertEquals(DoseUnit.MILLILITRES, untouched.values.doseUnit)
    }

    @Test
    fun `changing the unit of an edited dose stores the new unit`() {
        val stored = HealthValues(medication = "Calpol", doseAmount = 2.5, doseUnit = DoseUnit.MILLILITRES)
        val changed = healthFieldsFor(stored).copy(doseUnit = DoseUnit.MILLIGRAMS)

        val read = readHealth(changed, stored)
        assertEquals(2.5, read.values.doseAmount!!, 0.0)
        assertEquals(DoseUnit.MILLIGRAMS, read.values.doseUnit)
    }

    // ---- the last-dose hint ------------------------------------------------

    @Test
    fun `the hint is the last dose of that medicine for each child in scope`() {
        val entries = listOf(
            health("first-twin", at = t0, medication = "Calpol", dose = 2.5, unit = DoseUnit.MILLILITRES),
            health("first-twin", at = t0 + 1_000, medication = "Calpol", dose = 5.0, unit = DoseUnit.MILLILITRES),
            health("second-twin", at = t0, medication = "Calpol", dose = 1.5, unit = DoseUnit.MILLILITRES),
        )

        assertEquals(
            listOf("first-twin" to "5 ml", "second-twin" to "1.5 ml"),
            lastDoses(entries, listOf("first-twin", "second-twin"), "Calpol"),
        )
    }

    @Test
    fun `the hint ignores a different medicine, a different case and a record with no dose`() {
        val entries = listOf(
            health("first-twin", at = t0, medication = "Nurofen", dose = 2.5, unit = DoseUnit.MILLILITRES),
            health("first-twin", at = t0 + 1, medication = "Vitamin D", dose = null, unit = null),
        )

        // Exact name, ignoring case and spacing, and only entries that actually recorded a dose.
        assertEquals(listOf("first-twin" to "2.5 ml"), lastDoses(entries, listOf("first-twin"), "nurofen"))
        assertEquals(emptyList<Pair<String, String>>(), lastDoses(entries, listOf("first-twin"), "Vitamin D"))
        assertEquals(emptyList<Pair<String, String>>(), lastDoses(entries, listOf("first-twin"), ""))
        assertEquals(emptyList<Pair<String, String>>(), lastDoses(entries, listOf("second-twin"), "Nurofen"))
    }

    private fun health(
        childId: String,
        at: Long,
        medication: String? = null,
        dose: Double? = null,
        unit: DoseUnit? = null,
    ) = BabyEvent(
        id = "health-$childId-$at", childId = childId, type = EventType.HEALTH, startedAt = at,
        medication = medication, doseAmount = dose, doseUnit = unit,
        createdBy = "t", updatedAt = at,
    )
}
