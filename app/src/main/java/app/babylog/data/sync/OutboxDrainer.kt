package app.babylog.data.sync

import app.babylog.data.local.ChildDao
import app.babylog.data.local.EventDao
import app.babylog.data.local.OutboxDao
import app.babylog.data.local.OutboxEntity
import java.time.Clock
import kotlinx.coroutines.CancellationException

/** What one drain attempt did, for the `Sync now` row and for the tests to assert on. */
data class DrainResult(
    val written: Int = 0,
    val tombstoned: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

/**
 * Drains queued writes to Firestore.
 *
 * Three rules, each of which was a bug waiting to happen:
 *
 * 1. **The intent is a pointer, never a payload.** Each row is re-read from Room at drain time, so an
 *    edit made after queueing is the version that travels.
 * 2. **The queue entry is deleted only after the write is acked.** A failed drain leaves the intent
 *    exactly where it was and the next trigger retries it; a repeat write cannot duplicate a document
 *    because the id is the row's own id and every write is a merge.
 * 3. **A delete needs no payload.** If the row is still here (a soft delete) its full document travels
 *    with `deleted = true`; if the row is already gone (a permanent delete) only an id-and-timestamp
 *    tombstone is written. Either way the other phone learns the row is deleted.
 *
 * The drain stops at the first failure instead of skipping ahead: the queue is ordered by when it was
 * written, and a later write to the same row must not overtake the earlier one on the wire.
 */
class OutboxDrainer(
    private val outboxDao: OutboxDao,
    private val childDao: ChildDao,
    private val eventDao: EventDao,
    private val gateway: SyncGateway,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun drain(): DrainResult {
        var written = 0
        var tombstoned = 0
        var skipped = 0

        for (intent in outboxDao.pending()) {
            try {
                when (intent.entityType) {
                    ENTITY_CHILD -> {
                        val child = childDao.byId(intent.entityId)
                        if (child != null) {
                            gateway.writeChild(child)
                            written++
                        } else if (intent.op == OP_DELETE) {
                            gateway.deleteChild(intent.entityId, clock.millis())
                            tombstoned++
                        } else {
                            // The row was permanently deleted and its own DELETE intent already
                            // carries the news, so this pointer has nothing left to write.
                            skipped++
                        }
                    }

                    ENTITY_EVENT -> {
                        val event = eventDao.byId(intent.entityId)
                        if (event != null) {
                            gateway.writeEvent(event)
                            written++
                        } else if (intent.op == OP_DELETE) {
                            gateway.deleteEvent(intent.entityId, clock.millis())
                            tombstoned++
                        } else {
                            skipped++
                        }
                    }

                    else -> skipped++
                }
                outboxDao.delete(intent.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return DrainResult(written, tombstoned, skipped, error = failure.describe())
            }
        }

        return DrainResult(written, tombstoned, skipped)
    }

    private fun Exception.describe(): String = message?.takeIf { it.isNotBlank() }
        ?: "Could not reach the server (${this::class.simpleName})"

    companion object {
        const val ENTITY_CHILD = "CHILD"
        const val ENTITY_EVENT = "EVENT"
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
    }
}

/** A queued write, named so the call sites do not spell the strings themselves. */
fun outboxIntent(entityType: String, entityId: String, deleted: Boolean, queuedAt: Long) = OutboxEntity(
    entityType = entityType,
    entityId = entityId,
    op = if (deleted) OutboxDrainer.OP_DELETE else OutboxDrainer.OP_UPSERT,
    queuedAt = queuedAt,
)
