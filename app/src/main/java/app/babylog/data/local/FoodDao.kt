package app.babylog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Food ids are stable slugs, so a rename never orphans meals that referenced them. */
@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY name")
    suspend fun all(): List<FoodEntity>

    /** One row, for a read-modify-write that must not disturb the other columns. */
    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: String): FoodEntity?

    /**
     * The catalogue ordered the way the picker wants it: recently used first, then the rest
     * alphabetically. A Flow, so that logging a food (which touches `lastUsedAt`) reorders the picker
     * without a manual refresh — the recent-first ordering is the entire reason a catalogue beats free
     * text, and a stale order would quietly undo that.
     */
    @Query("SELECT * FROM foods ORDER BY (lastUsedAt IS NULL), lastUsedAt DESC, name")
    fun observeCatalogue(): Flow<List<FoodEntity>>

    /** SQLite's LIKE is case-insensitive for ASCII, which is enough for a food list. Parameterised, never concatenated. */
    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun search(query: String): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(food: FoodEntity)

    @Upsert
    suspend fun upsertAll(foods: List<FoodEntity>)

    /**
     * Insert-IF-ABSENT, the seeding path. `@Upsert` would be wrong here: re-seeding a catalogue must
     * never overwrite a row it already created, because that would reset `lastUsedAt` (the recent-first
     * ordering) and could clobber a parent's custom entry. Returns -1 for a row that already existed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(foods: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(food: FoodEntity): Long

    @Query("UPDATE foods SET lastUsedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    /** Replace-everything import, and the reset's wipe (which is also what clears `hidden`/`localName`). */
    @Query("DELETE FROM foods")
    suspend fun deleteAll()

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun hardDelete(id: String)

    /** How many foods this phone has taken out of its picker, for the "N hidden" row. */
    @Query("SELECT COUNT(*) FROM foods WHERE hidden = 1")
    suspend fun hiddenCount(): Int
}
