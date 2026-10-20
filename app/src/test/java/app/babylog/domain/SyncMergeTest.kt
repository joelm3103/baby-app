package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The merge rule, as a pure function: **later `updatedAt` wins, and a deletion wins a tie**.
 *
 * Every case here is a way two phones can disagree, and the cost of getting one wrong is a log entry
 * that silently appears, disappears or duplicates itself.
 */
class SyncMergeTest {

    private fun local(at: Long, deleted: Boolean = false) = SyncRow(updatedAt = at, deleted = deleted)

    private fun remote(at: Long, deleted: Boolean = false) = SyncRow(updatedAt = at, deleted = deleted)

    @Test
    fun `a row this phone has never seen is taken`() {
        assertEquals(SyncVerdict.TAKE_REMOTE, resolveSync(local = null, remote = remote(100)))
    }

    @Test
    fun `a later remote edit wins`() {
        assertEquals(SyncVerdict.TAKE_REMOTE, resolveSync(local(100), remote(101)))
    }

    @Test
    fun `a later local edit is kept`() {
        assertEquals(SyncVerdict.KEEP_LOCAL, resolveSync(local(101), remote(100)))
    }

    @Test
    fun `an older remote tombstone cannot undo a newer local edit`() {
        // The phone that edited last wins even though the other phone deleted the row first in wall-clock
        // terms: the edit is newer, and the app is offline-first, so arrival order means nothing.
        assertEquals(SyncVerdict.KEEP_LOCAL, resolveSync(local(200), remote(100, deleted = true)))
    }

    @Test
    fun `a remote tombstone beats an older local row`() {
        assertEquals(SyncVerdict.TAKE_REMOTE, resolveSync(local(100), remote(200, deleted = true)))
    }

    @Test
    fun `on equal timestamps a deletion wins, so nothing resurrects`() {
        assertEquals(SyncVerdict.TAKE_REMOTE, resolveSync(local(100), remote(100, deleted = true)))
    }

    @Test
    fun `on equal timestamps a local tombstone is not undone by a live remote row`() {
        assertEquals(SyncVerdict.KEEP_LOCAL, resolveSync(local(100, deleted = true), remote(100)))
    }

    @Test
    fun `two identical live rows need no write`() {
        assertEquals(SyncVerdict.IDENTICAL, resolveSync(local(100), remote(100)))
    }

    @Test
    fun `two identical tombstones need no write`() {
        assertEquals(SyncVerdict.IDENTICAL, resolveSync(local(100, deleted = true), remote(100, deleted = true)))
    }
}
