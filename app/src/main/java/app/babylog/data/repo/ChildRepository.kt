package app.babylog.data.repo

import app.babylog.data.local.ChildDao
import app.babylog.data.local.ChildEntity
import app.babylog.data.local.OutboxDao
import app.babylog.data.local.toDomain
import app.babylog.data.sync.OutboxDrainer
import app.babylog.data.sync.outboxIntent
import app.babylog.domain.Child
import app.babylog.domain.Sex
import app.babylog.domain.TERM_GESTATION_DAYS
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ChildRepository(
    private val childDao: ChildDao,
    /**
     * Children are part of the shared log, so a new or renamed child has to reach the other phone too.
     * Until Phase 13 this class was the one write path that did NOT queue an intent — a rename would
     * have stayed on the phone that made it.
     */
    private val outboxDao: OutboxDao,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** See `EventRepository.onQueued`: a child edit should reach the other phone without a relaunch. */
    private val onQueued: suspend () -> Unit = {},
) {
    fun observeAll(): Flow<List<Child>> = childDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<Child> = childDao.all().map { it.toDomain() }

    suspend fun upsert(child: Child) {
        val at = clock.millis()
        val entity = ChildEntity(
            id = child.id,
            name = child.name,
            birthDateEpochDay = child.birthDate.toEpochDay(),
            gestationalAgeDays = child.gestationalAgeDays,
            sex = child.sex.name,
            useAdjustedAge = child.useAdjustedAge,
            sortOrder = child.sortOrder,
            deleted = false,
            updatedAt = at,
        )
        childDao.upsert(entity)
        outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_CHILD, entity.id, deleted = false, queuedAt = at))
        onQueued()
    }

    /**
     * A new child, appended after the ones already here.
     *
     * The id is generated, not slugged from the name: two children can share a name, and the id is what
     * every event row references. `sortOrder` is therefore the only ordering that matters.
     */
    suspend fun add(
        name: String,
        birthDate: LocalDate,
        sex: Sex,
        gestationalAgeDays: Int = TERM_GESTATION_DAYS,
        useAdjustedAge: Boolean = true,
    ): Child {
        val order = (childDao.maxSortOrder() ?: -1) + 1
        val child = Child(
            id = newId(),
            name = name.trim(),
            birthDate = birthDate,
            sex = sex,
            gestationalAgeDays = gestationalAgeDays,
            useAdjustedAge = useAdjustedAge,
            sortOrder = order,
        )
        upsert(child)
        return child
    }

    /**
     * First-run seed. Both boys are 30-week preemies born a date set in Settings, but that is **initial data, not
     * a fact baked into the app** — every field here is editable in Settings, and this only runs when
     * the table is empty.
     */
    suspend fun seedIfEmpty() {
        if (childDao.count() > 0) return
        val now = clock.millis()
        val seeded = listOf(
            seed("first-twin", "First Twin", order = 0, now),
            seed("second-twin", "Second Twin", order = 1, now),
        )
        childDao.upsertAll(seeded)
        // Queued like any other write: a first install's children must reach the other phone without
        // waiting for a Settings edit to trigger a sync.
        seeded.forEach {
            outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_CHILD, it.id, deleted = false, queuedAt = now))
        }
        onQueued()
    }

    private fun seed(id: String, name: String, order: Int, now: Long) = ChildEntity(
        id = id,
        name = name,
        birthDateEpochDay = LocalDate.of(2025, 12, 31).toEpochDay(),
        // the premature range = 210 days. 70 days of prematurity, which is what the adjusted-age maths depends on.
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
