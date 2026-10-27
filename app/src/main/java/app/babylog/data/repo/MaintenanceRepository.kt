package app.babylog.data.repo

import androidx.room.withTransaction
import app.babylog.data.backup.Backup
import app.babylog.data.backup.BackupSnapshot
import app.babylog.data.local.AppDatabase
import app.babylog.data.sync.OutboxDrainer
import app.babylog.data.sync.outboxIntent
import java.time.Clock

/**
 * Whole-database operations that no single entity owns: backup, restore, archiving a child and
 * permanently deleting one.
 *
 * Every one of these touches several tables, so each runs inside ONE transaction. The import parses
 * and version-checks the file *before* the transaction opens, which is what makes a bad file harmless
 * rather than a half-wiped database.
 */
class MaintenanceRepository(
    private val database: AppDatabase,
    private val clock: Clock = Clock.systemUTC(),
    /** See `EventRepository.onQueued`: archive and permanent delete are news the other phone needs. */
    private val onQueued: suspend () -> Unit = {},
) {
    private val childDao get() = database.childDao()
    private val eventDao get() = database.eventDao()
    private val foodDao get() = database.foodDao()
    private val outboxDao get() = database.outboxDao()

    /** The whole database as a snapshot, tombstones included. */
    suspend fun snapshot(): BackupSnapshot = BackupSnapshot(
        exportedAt = clock.millis(),
        children = childDao.allIncludingDeleted(),
        events = eventDao.allIncludingDeleted(),
        foods = foodDao.all(),
    )

    /**
     * The file's rows, without applying anything.
     *
     * An import has to know the file's ids BEFORE it wipes the tables: those ids are the keep set the
     * server is reconciled against. `replaceAll` reads the file itself and throws on a bad or newer-schema
     * file, so this is the same guard, just earlier.
     */
    suspend fun readSnapshot(json: String): BackupSnapshot = Backup.read(json)

    /** The export file's contents. Indented JSON so it stays human-readable. */
    suspend fun exportJson(): String = Backup.write(snapshot())

    /**
     * Replace EVERYTHING with the file's contents.
     *
     * `Backup.read` runs first and throws on an unreadable or newer-schema file, so nothing is deleted
     * for a file that was never going to apply. Within the transaction the tables are wiped and rebuilt,
     * and the queued outbox intent is dropped because the file is now the truth.
     */
    suspend fun replaceAll(json: String) {
        val snapshot = Backup.read(json)
        database.withTransaction {
            eventDao.deleteAll()
            childDao.deleteAll()
            foodDao.deleteAll()
            outboxDao.deleteEverything()
            childDao.upsertAll(snapshot.children)
            eventDao.upsertAll(snapshot.events)
            foodDao.upsertAll(snapshot.foods)
        }
    }

    /**
     * Archive a child: the child and their whole history leave the UI, but every row survives as a
     * tombstone. This is the recoverable option and the one sync can propagate.
     */
    suspend fun archiveChild(childId: String) {
        val at = clock.millis()
        val eventIds = eventDao.idsForChild(childId)
        database.withTransaction {
            eventDao.softDeleteByChild(childId, at)
            childDao.softDelete(childId, at)
            // Archive is a tombstone, so it travels as one: the same DELETE intent a permanent delete
            // queues, but the rows stay here so the archive can be undone on either phone.
            enqueueTombstones(childId, eventIds, at)
        }
        onQueued()
    }

    /**
     * Delete a child and their log for good — **including on the other phone**.
     *
     * This used to leave no tombstone at all, which Phase 7 recorded as a defect: the other phone still
     * held the child and their events, so the next merge would hand them straight back. The fix is the
     * one the outbox was already shaped for — a DELETE intent needs only the id, so it can be queued for
     * rows that are about to stop existing:
     *
     * 1. queue a DELETE for the child and for every one of their events, in the same transaction that
     *    hard-deletes them, so a crash cannot lose the news;
     * 2. the drain writes an id-and-timestamp tombstone document for each;
     * 3. the other phone receives it and soft-deletes its copies, which keeps its own tombstones so
     *    nothing can resurrect them later.
     */
    suspend fun deleteChildPermanently(childId: String) {
        val eventIds = eventDao.idsForChild(childId)
        val at = clock.millis()
        database.withTransaction {
            // Intents first, deletes second: the other direction would leave a window where the rows are
            // gone and nothing remembers them.
            enqueueTombstones(childId, eventIds, at)
            eventDao.deleteByChild(childId)
            childDao.hardDelete(childId)
            // Drop every OTHER intent for these ids: a queued UPSERT now points at a row that no longer
            // exists, and the DELETE just queued is the truth.
            outboxDao.deleteUpserts(entityIds = eventIds + childId)
        }
        onQueued()
    }

    /**
     * Reset this phone to first-run state: the log and its children go, and the two seeded children come
     * back in their place.
     *
     * The ordering is the whole feature. A reset that deleted rows without telling the server would be
     * merged straight back in by the next snapshot, so every deletion travels as a tombstone queued in the
     * SAME transaction that removes the row — the same rule `deleteChildPermanently` follows, applied to
     * every row at once.
     *
     * The two seeded children are the exception, and the trap. They are re-created with the SAME ids, so a
     * DELETE for them would erase them on the server the moment it drained, leaving this phone with the
     * twins and the other phone with nothing. Their intents are therefore dropped rather than queued, and
     * `ChildRepository.seedIfEmpty` queues the UPSERTs that replace them.
     */
    suspend fun resetLocal(at: Long = clock.millis()) {
        val childIds = childDao.allIncludingDeleted().map { it.id }
        val eventIds = eventDao.allIncludingDeleted().map { it.id }
        database.withTransaction {
            // Intents first, deletes second: the other direction leaves a window where the rows are gone
            // and nothing remembers them.
            enqueueTombstones(childIds = childIds - SEED_CHILD_IDS, eventIds = eventIds, at = at)
            eventDao.deleteAll()
            childDao.deleteAll()
            // A queued UPSERT now points at a row that no longer exists; the DELETE just queued is the truth.
            outboxDao.deleteUpserts(entityIds = eventIds + childIds)
            // No intent of ANY kind may survive for the children that are about to be re-created.
            outboxDao.deleteByEntityIds(SEED_CHILD_IDS)
        }
        onQueued()
    }

    private suspend fun enqueueTombstones(childId: String, eventIds: List<String>, at: Long) =
        enqueueTombstones(childIds = listOf(childId), eventIds = eventIds, at = at)

    private suspend fun enqueueTombstones(childIds: List<String>, eventIds: List<String>, at: Long) {
        // An id that already has a DELETE queued is skipped, not queued twice: a server wipe reconciles the
        // remote ids and then deletes locally, and both passes see the same rows, so without this the queue
        // holds two intents per row and the "still queued" count reads double what it means.
        //
        // Only DELETE counts. A queued UPSERT is a different fact — the row was edited since — and it must
        // still be replaced by the tombstone, or the reset would leave a pointer to a row that no longer
        // exists once `deleteUpserts` clears it.
        val alreadyDeleted = outboxDao.pending()
            .filter { it.op == OutboxDrainer.OP_DELETE }
            .map { it.entityId }
            .toSet()
        childIds.filterNot { it in alreadyDeleted }.forEach { id ->
            outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_CHILD, id, deleted = true, queuedAt = at))
        }
        eventIds.filterNot { it in alreadyDeleted }.forEach { id ->
            outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, id, deleted = true, queuedAt = at))
        }
    }

    companion object {
        /**
         * `ChildRepository.seedIfEmpty` re-creates these two ids, so they must never travel as deletions.
         * Kept here rather than in the repository that seeds them because the *reset* is what has to know
         * the difference between "a child the parent added" and "a child the app owns".
         */
        val SEED_CHILD_IDS = listOf("first-twin", "second-twin")
    }
}
