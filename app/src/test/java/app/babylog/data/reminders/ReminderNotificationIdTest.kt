package app.babylog.data.reminders

import app.babylog.domain.EventType
import app.babylog.domain.REMINDER_TYPES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The id a reminder notification is posted, replaced and — since Phase 20 — CANCELLED under.
 *
 * This was `type.ordinal * 31 + childId.hashCode()` for as long as an id only decided which notification
 * replaced which. Phase 20 gave a refresh the power to cancel by id, and that turned a collision from a
 * cosmetic overwrite into one twin's reminder being taken down in the other's place. The pair is now digested,
 * and the cases that used to collide are pinned here.
 */
class ReminderNotificationIdTest {

    /** The companion member, named once so the call sites below read as the question they ask. */
    private fun id(type: EventType, child: String): Int = ReminderNotifications.notificationId(type, child)

    /** The two children this app actually has, plus ids built to hash alike on purpose. */
    private val children = listOf("first-twin", "second-twin", "Aa", "BB")

    @Test
    fun everyActivityAndChildPairHasItsOwnId() {
        val ids = REMINDER_TYPES.flatMap { type -> children.map { type to it } }
            .associate { (type, child) -> id(type, child) to "$type/$child" }
        assertEquals(REMINDER_TYPES.size * children.size, ids.size)
    }

    @Test
    fun twoChildrenWhoseIdsHashAlikeDoNotShareANotification() {
        // `"Aa".hashCode() == "BB".hashCode()` in Java, so the old scheme gave one activity's two children
        // exactly the same notification id — one reminder silently standing in for the other, and, since
        // Phase 20, one cancel taking the wrong twin's notification away.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        REMINDER_TYPES.forEach { type ->
            assertTrue(
                "$type gave 'Aa' and 'BB' the same id",
                id(type, "Aa") != id(type, "BB"),
            )
        }
    }

    @Test
    fun theSamePairAlwaysGetsTheSameId() {
        // Deterministic, because it is also the PendingIntent request code: a value that moved between builds
        // would orphan every notification already in the shade.
        REMINDER_TYPES.forEach { type ->
            children.forEach { child ->
                assertEquals(id(type, child), id(type, child))
            }
        }
    }

    @Test
    fun theActivitiesThatCarryNoReminderStillGetTheirOwnIds() {
        // Growth and Sleep cannot have a rule, but `refresh()` sweeps every type to cancel a rule that was
        // removed — so their ids must not collide with a real reminder's either.
        val all = EventType.entries.flatMap { type -> children.map { type to it } }
            .associate { (type, child) -> id(type, child) to "$type/$child" }
        assertEquals(EventType.entries.size * children.size, all.size)
    }
}
