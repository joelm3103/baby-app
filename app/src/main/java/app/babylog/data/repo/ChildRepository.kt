package app.babylog.data.repo

import app.babylog.data.local.ChildDao
import app.babylog.data.local.ChildEntity
import app.babylog.data.local.toDomain
import app.babylog.domain.Child
import app.babylog.domain.Sex
import app.babylog.domain.TERM_GESTATION_DAYS
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ChildRepository(
    private val childDao: ChildDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun observeAll(): Flow<List<Child>> = childDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<Child> = childDao.all().map { it.toDomain() }

    suspend fun upsert(child: Child) {
        childDao.upsert(
            ChildEntity(
                id = child.id,
                name = child.name,
                birthDateEpochDay = child.birthDate.toEpochDay(),
                gestationalAgeDays = child.gestationalAgeDays,
                sex = child.sex.name,
                useAdjustedAge = child.useAdjustedAge,
                sortOrder = child.sortOrder,
                deleted = false,
                updatedAt = clock.millis(),
            ),
        )
    }

    /**
     * First-run seed. Both boys are 30-week preemies born a date set in Settings, but that is **initial data, not
     * a fact baked into the app** — every field here is editable in Settings, and this only runs when
     * the table is empty.
     */
    suspend fun seedIfEmpty() {
        if (childDao.count() > 0) return
        val now = clock.millis()
        childDao.upsertAll(
            listOf(
                seed("first-twin", "First Twin", order = 0, now),
                seed("second-twin", "Second Twin", order = 1, now),
            ),
        )
    }

    private fun seed(id: String, name: String, order: Int, now: Long) = ChildEntity(
        id = id,
        name = name,
        birthDateEpochDay = LocalDate.of(2025, 12, 31).toEpochDay(),
        // 30 weeks = 210 days. 70 days premature, which is what the adjusted-age maths depends on.
        gestationalAgeDays = 30 * 7,
        sex = Sex.BOY.name,
        useAdjustedAge = true,
        sortOrder = order,
        deleted = false,
        updatedAt = now,
    )

    companion object {
        const val TERM = TERM_GESTATION_DAYS
    }
}
