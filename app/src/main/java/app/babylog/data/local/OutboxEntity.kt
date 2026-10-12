package app.babylog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Unsynced writes, queued from day one even though sync does not arrive until Phase 11. Writing to the
 * outbox from the first commit is what makes offline-first real rather than retrofitted.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** CHILD | EVENT | FOOD */
    val entityType: String,
    val entityId: String,
    /** UPSERT | DELETE */
    val op: String,
    val payloadJson: String,
    val queuedAt: Long,
)
