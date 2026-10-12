package app.babylog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.babylog.domain.TERM_GESTATION_DAYS

@Entity(tableName = "children")
data class ChildEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** `LocalDate.toEpochDay()` — a birth date has no time and no timezone, so store it that way. */
    val birthDateEpochDay: Long,
    /** 280 = term, 210 = 30 weeks. See [app.babylog.domain.Child]. */
    val gestationalAgeDays: Int = TERM_GESTATION_DAYS,
    val sex: String,
    val useAdjustedAge: Boolean = true,
    val sortOrder: Int = 0,
    val deleted: Boolean = false,
    val updatedAt: Long,
)
