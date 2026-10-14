package app.babylog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox ORDER BY queuedAt ASC")
    suspend fun pending(): List<OutboxEntity>

    /** Drives a "pending sync" indicator, and lets Phase 13 assert an empty queue after a drain. */
    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun enqueue(item: OutboxEntity): Long

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    /** Permanent deletion removes the row a queued write pointed at, so the intent is dropped too. */
    @Query("DELETE FROM outbox WHERE entityId IN (:entityIds)")
    suspend fun deleteByEntityIds(entityIds: List<String>)

    /** Replace-everything import: the file's rows are the new truth, so queued intent is discarded. */
    @Query("DELETE FROM outbox")
    suspend fun deleteEverything()
}
