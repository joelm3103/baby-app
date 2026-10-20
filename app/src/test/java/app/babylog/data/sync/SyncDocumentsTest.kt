package app.babylog.data.sync

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.domain.TERM_GESTATION_DAYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format. Firestore hands back a `Map` and cannot be constructed in a JVM test, so the mapping
 * is where the round trip is proved: every field the app stores has to come back unchanged, and a
 * document that cannot be a row must be refused rather than defaulted into one.
 */
class SyncDocumentsTest {

    private val child = ChildEntity(
        id = "first-twin",
        name = "First Twin",
        birthDateEpochDay = 20_454,
        gestationalAgeDays = 210,
        sex = "BOY",
        useAdjustedAge = true,
        sortOrder = 1,
        deleted = false,
        updatedAt = 1_760_000_000_000L,
    )

    private val event = EventEntity(
        id = "event-1",
        childId = "first-twin",
        type = "BOTTLE",
        startedAt = 1_760_000_000_000L,
        endedAt = 1_760_000_600_000L,
        amountMl = 120,
        formulaBrand = "Kendamil",
        mealSlot = null,
        foodIdsCsv = null,
        reaction = null,
        diaperKind = null,
        rash = null,
        weightGrams = null,
        lengthMm = null,
        headMm = null,
        temperatureC = 38.4,
        medication = "Calpol",
        doseAmount = 2.5,
        doseUnit = "ML",
        vaccineName = null,
        note = "fussy",
        createdBy = "parent-one",
        updatedAt = 1_760_000_000_000L,
        deleted = false,
    )

    @Test
    fun `a child survives the round trip`() {
        assertEquals(child, childFromSyncMap(child.toSyncMap()))
    }

    @Test
    fun `a child tombstone survives the round trip`() {
        val tombstoned = child.copy(deleted = true, updatedAt = 1_760_000_900_000L)

        assertEquals(tombstoned, childFromSyncMap(tombstoned.toSyncMap()))
    }

    @Test
    fun `an event survives the round trip, including a dose and a temperature`() {
        assertEquals(event, eventFromSyncMap(event.toSyncMap()))
    }

    @Test
    fun `a bare event survives the round trip`() {
        val bare = EventEntity(
            id = "event-2",
            childId = "second-twin",
            type = "DIAPER",
            startedAt = 1_760_000_000_000L,
            diaperKind = "WET",
            createdBy = "parent-two",
            updatedAt = 1_760_000_000_000L,
        )

        assertEquals(bare, eventFromSyncMap(bare.toSyncMap()))
    }

    @Test
    fun `integers come back as integers even though Firestore may hand them back as doubles`() {
        // A whole number that round-trips through a Double and back to Int is the difference between
        // 120 ml and a row the entity constructor refuses.
        val documents = event.toSyncMap().toMutableMap()
        documents["amountMl"] = 120.0
        documents["temperatureC"] = 38.0

        val read = eventFromSyncMap(documents)!!

        assertEquals(120, read.amountMl)
        assertEquals(38.0, read.temperatureC!!, 0.0001)
    }

    @Test
    fun `a child document with no name is refused rather than defaulted`() {
        val documents = child.toSyncMap() - "name"

        assertNull(childFromSyncMap(documents))
    }

    @Test
    fun `an event document with no child is refused`() {
        assertNull(eventFromSyncMap(event.toSyncMap() - "childId"))
    }

    @Test
    fun `a missing gestational age falls back to term, not to zero`() {
        // Zero would make every percentile read as extremely premature.
        val read = childFromSyncMap(child.toSyncMap() - "gestationalAgeDays")!!

        assertEquals(TERM_GESTATION_DAYS, read.gestationalAgeDays)
    }

    @Test
    fun `a tombstone carries only what the other phone needs`() {
        val map = tombstone("event-9", 1_760_000_000_000L)

        assertEquals("event-9", map["id"])
        assertEquals(true, map["deleted"])
        assertEquals(1_760_000_000_000L, map["updatedAt"])
        // No payload: the row it describes is already gone.
        assertEquals(3, map.size)
    }

    @Test
    fun `a tombstone becomes a deletable remote row`() {
        val row = remoteRowOf("event-9", tombstone("event-9", 1_760_000_000_000L))!!

        assertEquals("event-9", row.id)
        assertTrue(row.deleted)
        assertEquals(1_760_000_000_000L, row.updatedAt)
    }

    @Test
    fun `a document without its id field uses the document id`() {
        val row = remoteRowOf("child-7", mapOf("updatedAt" to 5L, "deleted" to false))

        assertEquals("child-7", row?.id)
    }

    @Test
    fun `a document with no updatedAt is not one of ours`() {
        assertNull(remoteRowOf("child-7", mapOf("id" to "child-7", "name" to "First Twin")))
    }
}
