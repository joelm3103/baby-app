package app.babylog.data.repo

import androidx.room.withTransaction
import app.babylog.data.backup.Backup
import app.babylog.data.backup.BackupSnapshot
import app.babylog.data.local.AppDatabase
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
        database.withTransaction {
            eventDao.softDeleteByChild(childId, at)
            childDao.softDelete(childId, at)
        }
    }

    /**
     * Delete a child and their log for good, and drop the queued outbox intent that pointed at those
     * rows so the queue cannot reference something that no longer exists.
     *
     * **Leaves no tombstone.** A later sync therefore cannot learn about this deletion and could
     * resurrect the child from the other phone — recorded in PLAN.md as something Phase 13 must revisit.
     */
    suspend fun deleteChildPermanently(childId: String) {
        val eventIds = eventDao.idsForChild(childId)
        database.withTransaction {
            eventDao.deleteByChild(childId)
            childDao.hardDelete(childId)
            outboxDao.deleteByEntityIds(eventIds + childId)
        }
    }
}
