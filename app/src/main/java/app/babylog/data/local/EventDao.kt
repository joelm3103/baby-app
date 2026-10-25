package app.babylog.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Every read filters `deleted = 0`: a tombstone exists for sync, never for display.
 *
 * Note there are deliberately NO SQL aggregate queries here for History. Day/night splits and daily
 * boundaries depend on the *local* timezone, which SQLite cannot resolve, so totals are computed in
 * Kotlin over a date-ranged read — see `domain/Totals.kt`. Aggregating in SQL would silently use UTC
 * and put 8pm diapers on the wrong day.
 */
@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deleted = 0 AND childId IN (:childIds) ORDER BY startedAt DESC")
    fun observeForChildren(childIds: List<String>): Flow<List<EventEntity>>

    @Query(
        "SELECT * FROM events WHERE deleted = 0 AND childId IN (:childIds) " +
            "AND startedAt >= :fromMs AND startedAt < :toMs ORDER BY startedAt DESC",
    )
    suspend fun between(childIds: List<String>, fromMs: Long, toMs: Long): List<EventEntity>

    /**
     * The same range as [between], but observed. History uses this so that logging an event on the
     * Activity tab is reflected the moment the History tab is looked at, rather than only on relaunch.
     */
    @Query(
        "SELECT * FROM events WHERE deleted = 0 AND childId IN (:childIds) " +
            "AND startedAt >= :fromMs AND startedAt < :toMs ORDER BY startedAt DESC",
    )
    fun observeBetween(childIds: List<String>, fromMs: Long, toMs: Long): Flow<List<EventEntity>>

    @Query(
        "SELECT * FROM events WHERE deleted = 0 AND type = :type AND childId IN (:childIds) " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    fun observeLatest(type: String, childIds: List<String>): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE deleted = 0 AND endedAt IS NULL ORDER BY startedAt DESC")
    fun observeRunning(): Flow<List<EventEntity>>

    /** Powers the recent rows inside each activity card. */
    @Query(
        "SELECT * FROM events WHERE deleted = 0 AND type = :type AND childId IN (:childIds) " +
            "ORDER BY startedAt DESC LIMIT :limit",
    )
    fun observeRecentByType(type: String, childIds: List<String>, limit: Int): Flow<List<EventEntity>>

    /**
     * The newest non-deleted row of one type for ONE child, read once rather than observed.
     *
     * The reminder scheduler runs outside the UI (an alarm, a boot receiver), where there is no Flow to
     * collect: it needs the answer now, not a stream of it. Per child, because each child's reminder is
     * anchored on that child's own last feed - one twin's bottle must never reset the other's reminder.
     */
    @Query(
        "SELECT * FROM events WHERE deleted = 0 AND type = :type AND childId = :childId " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun latestOnce(type: String, childId: String): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: String): EventEntity?

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** Undo, for the delete snackbar. A soft delete makes undo a flag flip rather than a re-insert. */
    @Query("UPDATE events SET deleted = 0, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    // ---- backup and maintenance (Phase 7) ---------------------------------

    /** Every row including tombstones — the backup read. Display reads all filter `deleted = 0`. */
    @Query("SELECT * FROM events")
    suspend fun allIncludingDeleted(): List<EventEntity>

    /** Replace-everything import wipes first, so a failed file can never leave a half-applied log. */
    @Query("DELETE FROM events")
    suspend fun deleteAll()

    /** Permanently removing a child takes their log with them. */
    @Query("DELETE FROM events WHERE childId = :childId")
    suspend fun deleteByChild(childId: String)

    /** Collected BEFORE a permanent delete, so the matching outbox intent can be dropped too. */
    @Query("SELECT id FROM events WHERE childId = :childId")
    suspend fun idsForChild(childId: String): List<String>

    /** Archiving a child hides their history without destroying the rows. */
    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE childId = :childId")
    suspend fun softDeleteByChild(childId: String, now: Long)
}
