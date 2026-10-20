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

    private suspend fun enqueueTombstones(childId: String, eventIds: List<String>, at: Long) {
        outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_CHILD, childId, deleted = true, queuedAt = at))
        eventIds.forEach { id ->
            outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, id, deleted = true, queuedAt = at))
        }
    }
}
