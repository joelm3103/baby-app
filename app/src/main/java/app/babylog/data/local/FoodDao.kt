package app.babylog.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Food ids are stable slugs, so a rename never orphans meals that referenced them. */
@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY name")
    suspend fun all(): List<FoodEntity>

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

    @Query("UPDATE foods SET lastUsedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)
}
