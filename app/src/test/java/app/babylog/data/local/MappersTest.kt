package app.babylog.data.local

import app.babylog.domain.BabyEvent
import app.babylog.domain.Child
import app.babylog.domain.DiaperKind
import app.babylog.domain.DoseUnit
import app.babylog.domain.EventType
import app.babylog.domain.FoodReaction
import app.babylog.domain.MealSlot
import app.babylog.domain.Sex
import app.babylog.domain.decodeFoodIds
import app.babylog.domain.encodeFoodIds
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    private val now = 1_700_000_000_000L

    // ---- prematurity ------------------------------------------------------

    @Test
    fun `30 weeks is 70 days premature`() {
        val first-twin = child(gestationalAgeDays = 210)
        assertTrue(first-twin.isPreterm)
        assertEquals(70, first-twin.prematureDays)
        assertEquals("30 weeks", first-twin.gestationalWeeksLabel)
    }

    @Test
    fun `term is not premature`() {
        val c = child(gestationalAgeDays = 280)
        assertFalse(c.isPreterm)
        assertEquals(0, c.prematureDays)
        assertEquals("40 weeks", c.gestationalWeeksLabel)
    }

    // ---- child round trip --------------------------------------------------

    @Test
    fun `child survives a round trip including gestational age`() {
        val original = child(gestationalAgeDays = 210)
        val back = original.toEntity(now).toDomain()
        assertEquals(original, back)
        assertEquals(210, back.gestationalAgeDays)
        assertEquals(LocalDate.of(2025, 12, 31), back.birthDate)
        assertEquals(Sex.BOY, back.sex)
    }

    @Test
    fun `birth date round trips without a timezone shift`() {
        // epochDay is date-only; a LocalDate near a year boundary is the case that catches an
        // accidental epoch-millis conversion.
        val newYear = child(birthDate = LocalDate.of(2025, 12, 31))
        assertEquals(newYear.birthDate, newYear.toEntity(now).toDomain().birthDate)
    }

    // ---- event round trips -------------------------------------------------

    @Test
    fun `bottle round trips`() {
        val e = base(EventType.BOTTLE).copy(amountMl = 120, formulaBrand = "Aptamil")
        assertEquals(e, e.toEntity().toDomain())
    }

    @Test
    fun `solids round trips with foods, meal slot and reaction`() {
        val e = base(EventType.SOLIDS).copy(
            mealSlot = MealSlot.LUNCH,
            foodIds = listOf("banana", "oats"),
            reaction = FoodReaction.ALLERGY_OR_SENSITIVITY,
        )
        val back = e.toEntity().toDomain()
        assertEquals(e, back)
        assertEquals(listOf("banana", "oats"), back!!.foodIds)
    }

    @Test
    fun `diaper round trips with kind and rash`() {
        val e = base(EventType.DIAPER).copy(diaperKind = DiaperKind.MIXED, rash = false)
        assertEquals(e, e.toEntity().toDomain())
    }

    @Test
    fun `growth round trips all three measurements`() {
        val e = base(EventType.GROWTH).copy(weightGrams = 5443, lengthMm = 571, headMm = 430)
        assertEquals(e, e.toEntity().toDomain())
    }

    @Test
    fun `health round trips celsius, medication and dose`() {
        val e = base(EventType.HEALTH).copy(
            temperatureC = 38.2,
            medication = "Calpol",
            doseAmount = 2.5,
            doseUnit = DoseUnit.MILLILITRES,
        )
        assertEquals(e, e.toEntity().toDomain())
    }

    @Test
    fun `an unknown dose unit becomes no dose rather than a crash`() {
        // Same cross-version rule as the other event enums: a newer build on the other phone must never
        // take this one down, and "no dose" is honest about what this build can read.
        val entity = base(EventType.HEALTH).toEntity().copy(doseAmount = 2.5, doseUnit = "TEASPOONS")
        val back = entity.toDomain()!!

        assertEquals(2.5, back.doseAmount!!, 0.0)
        assertNull(back.doseUnit)
    }

    @Test
    fun `a running sleep event keeps a null end and reports running`() {
        val e = base(EventType.SLEEP).copy(endedAt = null)
        val back = e.toEntity().toDomain()!!
        assertTrue(back.isRunning)
        assertNull(back.endedAt)
    }

    // ---- cross-version tolerance ------------------------------------------

    @Test
    fun `an unknown event type is dropped rather than crashing`() {
        // What happens when the other phone runs a newer build and writes a type this build never had.
        val rogue = base(EventType.BOTTLE).toEntity().copy(type = "TEMPERATURE_OF_THE_VOID")
        assertNull(rogue.toDomain())
        assertEquals(0, listOf(rogue).toDomain().size)
    }

    @Test
    fun `an unknown reaction is nulled but the event still maps`() {
        val rogue = base(EventType.SOLIDS).toEntity().copy(reaction = "SOMETHING_NEW")
        val back = rogue.toDomain()
        assertNull(back!!.reaction)
        assertEquals(EventType.SOLIDS, back.type) // the rest of the row is still usable
    }

    @Test
    fun `a tombstone survives the round trip so deletions can sync`() {
        val e = base(EventType.DIAPER).copy(deleted = true)
        assertTrue(e.toEntity().toDomain()!!.deleted)
    }

    // ---- food id codec -----------------------------------------------------

    @Test
    fun `food ids encode to null when empty and decode back to empty`() {
        assertNull(encodeFoodIds(emptyList()))
        assertNull(encodeFoodIds(listOf("", "  ")))
        assertEquals(emptyList<String>(), decodeFoodIds(null))
    }

    @Test
    fun `food ids are trimmed, deduped and stable`() {
        assertEquals("banana,oats", encodeFoodIds(listOf(" banana ", "oats", "banana", "")))
        assertEquals(listOf("banana", "oats"), decodeFoodIds("banana,oats"))
        assertEquals(listOf("banana", "oats"), decodeFoodIds(" banana , oats "))
    }

    // ---- helpers -----------------------------------------------------------

    private fun child(
        gestationalAgeDays: Int = 210,
        birthDate: LocalDate = LocalDate.of(2025, 12, 31),
    ) = Child(
        id = "c1", name = "First Twin", birthDate = birthDate, sex = Sex.BOY,
        gestationalAgeDays = gestationalAgeDays, useAdjustedAge = true, sortOrder = 0,
    )

    private fun base(type: EventType) = BabyEvent(
        id = "e-$type", childId = "c1", type = type, startedAt = now,
        createdBy = "u1", updatedAt = now,
    )
}
