package app.babylog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Unsynced writes, queued from day one even though sync does not arrive until Phase 12. Writing to the
 * outbox from the first commit is what makes offline-first real rather than retrofitted.
 *
 * **Deliberately no serialised payload.** An earlier version carried a JSON copy of the entity, which
 * would drift the moment the row was edited again before the drain ran. The drain reads the *current*
 * row by id instead, so the queue records intent and the data has one home.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** CHILD | EVENT | FOOD */
    val entityType: String,
    val entityId: String,
    /** UPSERT | DELETE */
    val op: String,
    val queuedAt: Long,
)
