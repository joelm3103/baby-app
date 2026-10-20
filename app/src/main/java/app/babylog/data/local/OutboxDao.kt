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

    /**
     * How many intents already point at one row.
     *
     * Used to avoid piling up duplicate intents for the same entity when a snapshot and a drain race:
     * one queued write per row is enough, because the drain re-reads the row and every write is a merge.
     */
    @Query("SELECT COUNT(*) FROM outbox WHERE entityId = :entityId")
    suspend fun countFor(entityId: String): Int

    @Insert
    suspend fun enqueue(item: OutboxEntity): Long

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    /** Permanent deletion removes the row a queued write pointed at, so the intent is dropped too. */
    @Query("DELETE FROM outbox WHERE entityId IN (:entityIds)")
    suspend fun deleteByEntityIds(entityIds: List<String>)

    /**
     * Drop only the *write* intents for these rows, keeping any DELETE.
     *
     * A permanent delete hard-deletes rows that may still have an UPSERT waiting in the queue. That
     * UPSERT now points at a row that no longer exists, so it is dead weight — but the DELETE just
     * queued for the same ids is the news the other phone needs, and must survive.
     */
    @Query("DELETE FROM outbox WHERE entityId IN (:entityIds) AND op = 'UPSERT'")
    suspend fun deleteUpserts(entityIds: List<String>)

    /** Replace-everything import: the file's rows are the new truth, so queued intent is discarded. */
    @Query("DELETE FROM outbox")
    suspend fun deleteEverything()
}
