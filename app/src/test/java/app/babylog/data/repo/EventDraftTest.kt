package app.babylog.data.repo

import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edit path. Rows on the activity cards open a pre-filled sheet and save back onto the SAME row, so
 * what has to be proved here is identity: an edit must not become a different event.
 */
class EventDraftTest {

    private val existing = BabyEvent(
        id = "row-1",
        childId = "second-twin",
        type = EventType.BOTTLE,
        startedAt = 1_760_000_000_000L,
        amountMl = 120,
        formulaBrand = "Aptamil",
        createdBy = "local",
        updatedAt = 1_760_000_000_000L,
    )

    @Test
    fun `an edit keeps the row's identity and applies the new values`() {
        val draft = EventDraft(
            type = EventType.BOTTLE,
            startedAt = 1_760_000_060_000L,
            amountMl = 150,
            formulaBrand = "Kendamil",
            note = "fussy",
        )

        val edited = draft.applyTo(existing, updatedAt = 1_760_000_999_000L)

        assertEquals("row-1", edited.id)
        assertEquals("second-twin", edited.childId)
        assertEquals("local", edited.createdBy)
        assertEquals(EventType.BOTTLE, edited.type)
        assertEquals(1_760_000_060_000L, edited.startedAt)
        assertEquals(150, edited.amountMl)
        assertEquals("Kendamil", edited.formulaBrand)
        assertEquals("fussy", edited.note)
        assertEquals(1_760_000_999_000L, edited.updatedAt)
    }

    @Test
    fun `the child comes from the row, never from the sheet`() {
        // One Both-mode tap writes two rows. Editing First Twin's feed must leave Second Twin's alone, which is only
        // true if the child is read off the row being edited.
        val firstRow = existing.copy(id = "first-twin-row", childId = "first-twin")
        val draft = EventDraft(type = EventType.BOTTLE, startedAt = existing.startedAt, amountMl = 90)

        assertEquals("first-twin", draft.applyTo(firstRow, 1L).childId)
        assertEquals("second-twin", draft.applyTo(existing, 1L).childId)
    }

    @Test
    fun `clearing a value clears it rather than keeping the old one`() {
        // An amount deleted in the sheet means "no amount", not "forget I asked".
        val draft = EventDraft(type = EventType.BOTTLE, startedAt = existing.startedAt, amountMl = null)

        assertNull(draft.applyTo(existing, 1L).amountMl)
    }

    @Test
    fun `a draft cannot retype an existing row`() {
        // A diaper sheet saving onto a bottle row would leave every bottle column stranded.
        val draft = EventDraft(type = EventType.DIAPER, startedAt = existing.startedAt, diaperKind = DiaperKind.WET)

        assertThrows(IllegalArgumentException::class.java) { draft.applyTo(existing, 1L) }
    }

    @Test
    fun `editing a tombstone does not resurrect it`() {
        val draft = EventDraft(type = EventType.BOTTLE, startedAt = existing.startedAt, amountMl = 150)

        assertTrue(draft.applyTo(existing.copy(deleted = true), 1L).deleted)
    }

    @Test
    fun `a new event is not deleted`() {
        val draft = EventDraft(type = EventType.BOTTLE, startedAt = existing.startedAt, amountMl = 150)

        assertFalse(draft.toEvent(id = "new", childId = "first-twin", createdBy = "local", updatedAt = 1L).deleted)
    }
}
