package app.babylog.data.repo

import app.babylog.data.local.EventDao
import app.babylog.data.local.FoodDao
import app.babylog.data.local.OutboxEntity
import app.babylog.data.local.OutboxDao
import app.babylog.data.local.toDomain
import app.babylog.data.local.toEntity
import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The ONLY place that writes events. Everything else reads.
 *
 * Every write also queues an outbox row. Sync does not exist until Phase 12, but queueing from day one
 * is what makes offline-first real rather than retrofitted: the pending queue is a record of intent,
 * and the drain reads the *current* row when it runs rather than a stale serialised copy.
 *
 * `clock` and `newId` are injected so tests are deterministic.
 */
class EventRepository(
    private val eventDao: EventDao,
    private val outboxDao: OutboxDao,
    private val foodDao: FoodDao,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private fun now(): Long = clock.millis()

    // ---- reads ------------------------------------------------------------

    fun observeForChildren(childIds: List<String>): Flow<List<BabyEvent>> =
        eventDao.observeForChildren(childIds).map { it.toDomain() }

    fun observeRecentByType(
        type: EventType,
        childIds: List<String>,
        limit: Int = 3,
    ): Flow<List<BabyEvent>> =
        eventDao.observeRecentByType(type.name, childIds, limit).map { it.toDomain() }

    fun observeLatest(type: EventType, childIds: List<String>): Flow<BabyEvent?> =
        eventDao.observeLatest(type.name, childIds).map { it?.toDomain() }

    fun observeRunning(): Flow<List<BabyEvent>> = eventDao.observeRunning().map { it.toDomain() }

    // ---- writes -----------------------------------------------------------

    /**
     * Logs one draft against several children at once — the "Both" case. Every row shares the same
     * `startedAt` and `updatedAt`, so the two children's timelines agree on when the feed happened.
     */
    suspend fun log(childIds: List<String>, draft: EventDraft, createdBy: String): List<BabyEvent> {
        require(childIds.isNotEmpty()) { "log() needs at least one child" }
        val at = now()
        val events = childIds.distinct().map { childId ->
            draft.toEvent(id = newId(), childId = childId, createdBy = createdBy, updatedAt = at)
        }
        eventDao.upsertAll(events.map { it.toEntity() })
        events.forEach { queue(it) }

        if (draft.type == EventType.SOLIDS && draft.foodIds.isNotEmpty()) {
            draft.foodIds.forEach { foodDao.touch(it, at) }
        }
        return events
    }

    suspend fun update(event: BabyEvent): BabyEvent {
        val updated = event.copy(updatedAt = now())
        eventDao.upsert(updated.toEntity())
        queue(updated)
        return updated
    }

    /** Soft delete, returning the tombstone so the UI can offer Undo. */
    suspend fun softDelete(event: BabyEvent): BabyEvent {
        val at = now()
        eventDao.softDelete(event.id, at)
        val tombstone = event.copy(deleted = true, updatedAt = at)
        queue(tombstone)
        return tombstone
    }

    /** Undo is a flag flip, not a re-insert — which is why deletes are tombstones in the first place. */
    suspend fun undoDelete(event: BabyEvent): BabyEvent {
        val at = now()
        eventDao.restore(event.id, at)
        val restored = event.copy(deleted = false, updatedAt = at)
        queue(restored)
        return restored
    }

    /**
     * Starts a sleep timer, skipping any child who already has one running. Stacking timers is not a
     * feature: it produces negative durations and a card that cannot show a single truth.
     * Returns an empty list when everyone in scope was already running.
     */
    suspend fun startSleepTimer(childIds: List<String>, createdBy: String): List<BabyEvent> {
        val busy = eventDao.observeRunning().first()
            .filter { it.type == EventType.SLEEP.name }
            .map { it.childId }
            .toSet()
        val targets = childIds.distinct().filterNot { it in busy }
        if (targets.isEmpty()) return emptyList()
        return log(targets, EventDraft(type = EventType.SLEEP, startedAt = now()), createdBy)
    }

    /** Stops the running sleep timer for one child. Returns null if there was not one. */
    suspend fun stopSleepTimer(childId: String): BabyEvent? {
        val running = eventDao.observeRunning().first()
            .firstOrNull { it.childId == childId && it.type == EventType.SLEEP.name }
            ?: return null
        val domain = running.toDomain() ?: return null
        return update(domain.copy(endedAt = now()))
    }

    private suspend fun queue(event: BabyEvent) {
        outboxDao.enqueue(
            OutboxEntity(
                entityType = "EVENT",
                entityId = event.id,
                op = if (event.deleted) "DELETE" else "UPSERT",
                queuedAt = now(),
            ),
        )
    }
}
