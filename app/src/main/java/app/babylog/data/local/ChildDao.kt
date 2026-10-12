package app.babylog.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Query("SELECT * FROM children WHERE deleted = 0 ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE deleted = 0 ORDER BY sortOrder, name")
    suspend fun all(): List<ChildEntity>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun byId(id: String): ChildEntity?

    @Query("SELECT COUNT(*) FROM children WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(child: ChildEntity)

    @Upsert
    suspend fun upsertAll(children: List<ChildEntity>)

    @Query("UPDATE children SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
