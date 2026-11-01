package app.babylog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The solids catalogue, seeded from a bundled asset so a new food is a data change rather than a code
 * change. `id` is a stable slug ("banana"), never a display name: renaming "Courgette" to "Zucchini"
 * must not orphan every meal that referenced it.
 */
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    /** Drives recent-first ordering in the picker — the only reason a catalogue beats free text. */
    val lastUsedAt: Long? = null,
    /**
     * True while the food is a parent's addition (`foods_pending`) rather than a catalogue entry.
     *
     * Flips to false when the published catalogue takes the same id, which is what stops a promoted food
     * from being treated as pending again. Nothing syncs this column: the catalogue is published, not
     * merged row by row (ADR-0002).
     */
    val custom: Boolean = false,
    /**
     * This phone's own name for the food, or null when the catalogue's is good enough (schema v4).
     *
     * Device-local on purpose: one parent renaming "Courgette" to "Zucchini" writes a rename proposal for
     * the owner to decide on, and never rewrites the shared catalogue from a phone.
     */
    val localName: String? = null,
    /** Taken out of THIS phone's picker, or false (schema v4). Never travels; nothing else filters on it. */
    val hidden: Boolean = false,
)
