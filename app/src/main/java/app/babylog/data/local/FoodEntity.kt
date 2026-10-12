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
    val custom: Boolean = false,
)
