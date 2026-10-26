package app.babylog.data.sync

import app.babylog.data.local.ChildDao
import app.babylog.data.local.EventDao
import app.babylog.data.local.OutboxDao
import app.babylog.data.local.OutboxEntity
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** What one drain attempt did, for the `Sync now` row and for the tests to assert on. */
data class DrainResult(
    val written: Int = 0,
    val tombstoned: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
    /** Still queued when the attempt stopped: what the Sync card says is waiting. */
    val remaining: Int = 0,
    /**
     * The gateway took the call and never answered, rather than failing.
     *
     * Worth its own flag because the sentence differs: a timeout may mean the phone is offline or that
     * the server is silent, and those are not the same news. See `NetworkStatus`.
     */
    val timedOut: Boolean = false,
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
 *
 * 4. **A write that never answers is a failure, not a wait.** Firestore closes its write stream
 *    `UNAVAILABLE` with no network and leaves the `Task` pending forever, so `awaitTask()` never
 *    returns, `syncNow()` never returns, and `drainLock` stays held — the button looked inert and could
 *    not resolve itself. Each write is therefore bounded by [writeTimeoutMs]. The timeout lives HERE and
 *    not in the gateway so the fake can prove it without a Firestore client.
 */
class OutboxDrainer(
    private val outboxDao: OutboxDao,
    private val childDao: ChildDao,
    private val eventDao: EventDao,
    private val gateway: SyncGateway,
    private val clock: Clock = Clock.systemUTC(),
    /** Generous for one document write, small enough that a parent is still looking at the screen. */
    private val writeTimeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS,
) {

    private enum class Outcome { WRITTEN, TOMBSTONED, SKIPPED }

    suspend fun drain(): DrainResult {
        var written = 0
        var tombstoned = 0
        var skipped = 0

        for (intent in outboxDao.pending()) {
            val outcome = try {
                withTimeoutOrNull(writeTimeoutMs) { perform(intent) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return DrainResult(
                    written = written,
                    tombstoned = tombstoned,
                    skipped = skipped,
                    error = failure.describe(),
                    remaining = outboxDao.pending().size,
                )
            }

            // Null means the gateway never answered. The intent stays exactly where it is, so the next
            // trigger retries it; a repeat write cannot duplicate a document because every write is a
            // merge keyed by the row's own id.
            if (outcome == null) {
                return DrainResult(
                    written = written,
                    tombstoned = tombstoned,
                    skipped = skipped,
                    error = NO_ANSWER,
                    remaining = outboxDao.pending().size,
                    timedOut = true,
                )
            }

            when (outcome) {
                Outcome.WRITTEN -> written++
                Outcome.TOMBSTONED -> tombstoned++
                Outcome.SKIPPED -> skipped++
            }
            outboxDao.delete(intent.id)
        }

        return DrainResult(written, tombstoned, skipped)
    }

    private suspend fun perform(intent: OutboxEntity): Outcome = when (intent.entityType) {
        ENTITY_CHILD -> {
            val child = childDao.byId(intent.entityId)
            if (child != null) {
                gateway.writeChild(child)
                Outcome.WRITTEN
            } else if (intent.op == OP_DELETE) {
                gateway.deleteChild(intent.entityId, clock.millis())
                Outcome.TOMBSTONED
            } else {
                // The row was permanently deleted and its own DELETE intent already carries the news,
                // so this pointer has nothing left to write.
                Outcome.SKIPPED
            }
        }

        ENTITY_EVENT -> {
            val event = eventDao.byId(intent.entityId)
            if (event != null) {
                gateway.writeEvent(event)
                Outcome.WRITTEN
            } else if (intent.op == OP_DELETE) {
                gateway.deleteEvent(intent.entityId, clock.millis())
                Outcome.TOMBSTONED
            } else {
                Outcome.SKIPPED
            }
        }

        else -> Outcome.SKIPPED
    }

    private fun Exception.describe(): String = message?.takeIf { it.isNotBlank() }
        ?: "Could not reach the server (${this::class.simpleName})"

    companion object {
        const val ENTITY_CHILD = "CHILD"
        const val ENTITY_EVENT = "EVENT"
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"

        /** Ten seconds for one document. Slow enough for a bad connection, short enough to notice. */
        const val DEFAULT_WRITE_TIMEOUT_MS = 10_000L

        /** The error a timed-out write reports; the Sync card turns it into one of two sentences. */
        const val NO_ANSWER = "The server did not answer."
    }
}

/** A queued write, named so the call sites do not spell the strings themselves. */
fun outboxIntent(entityType: String, entityId: String, deleted: Boolean, queuedAt: Long) = OutboxEntity(
    entityType = entityType,
    entityId = entityId,
    op = if (deleted) OutboxDrainer.OP_DELETE else OutboxDrainer.OP_UPSERT,
    queuedAt = queuedAt,
)
