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
}
