package app.babylog.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [Index("childId"), Index("type"), Index("startedAt"), Index("deleted")],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val childId: String,
    /** [app.babylog.domain.EventType] name, stored as text so a newer build's value cannot break an older one. */
    val type: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val amountMl: Int? = null,
    val formulaBrand: String? = null,
    val mealSlot: String? = null,
    /** Comma-separated food ids; see [app.babylog.domain.encodeFoodIds]. */
    val foodIdsCsv: String? = null,
    val reaction: String? = null,
    val diaperKind: String? = null,
    val rash: Boolean? = null,
    /**
     * Colour and consistency, comma-separated enum names, and NULL unless the nappy was DIRTY.
     *
     * Added in schema v3 (Phase 13.3, `0.12.0`). Two TEXT columns rather than a join table, for the
     * reason `CodedValues` records.
     */
    val diaperColoursCsv: String? = null,
    val diaperConsistencyCsv: String? = null,
    val weightGrams: Int? = null,
    val lengthMm: Int? = null,
    val headMm: Int? = null,
    val temperatureC: Double? = null,
    val medication: String? = null,
    /** The dose given with [medication], as entered. See [app.babylog.domain.DoseUnit]. */
    val doseAmount: Double? = null,
    val doseUnit: String? = null,
    val vaccineName: String? = null,
    val note: String? = null,
    val createdBy: String,
    val updatedAt: Long,
    /** Soft-delete tombstone. A hard delete resurrects from the other phone on the next sync. */
    val deleted: Boolean = false,
)
