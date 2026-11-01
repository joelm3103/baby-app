package app.babylog.data.sync

import app.babylog.data.local.ChildDao
import app.babylog.data.local.EventDao
import app.babylog.data.local.FoodDao
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
 * The drain keeps the queue's order per row, but a write that never answers is DEFERRED to the end of
 * the pass rather than ending it. A wipe queues one tombstone per row — about 160 for one family's log —
 * and the first write on a cold connection is the one most likely to be slow; letting it stop the batch
 * left the other 159 reported as "still queued" under a summary that already claimed the server was
 * clear. So: try every intent, retry the unanswered ones once at the end, and report what is genuinely
 * left. An exception still ends the pass, because a systematic failure should not burn 160 attempts.
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
    /**
     * The catalogue queue's local row lookup, optional for an older caller that only drains events/children.
     * Any caller that can enqueue `FOOD_PENDING` or `FOOD_PROPOSAL` must provide it.
     */
    private val foodDao: FoodDao? = null,
) {

    private enum class Outcome { WRITTEN, TOMBSTONED, SKIPPED }

    suspend fun drain(): DrainResult {
        var written = 0
        var tombstoned = 0
        var skipped = 0
        val unanswered = mutableListOf<OutboxEntity>()

        /** True when the intent was answered and settled; false when the gateway stayed silent. */
        suspend fun attempt(intent: OutboxEntity): Boolean {
            val outcome = try {
                withTimeoutOrNull(writeTimeoutMs) { perform(intent) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw DrainAborted(failure.describe(), DrainResult(
                    written = written,
                    tombstoned = tombstoned,
                    skipped = skipped,
                    error = failure.describe(),
                    remaining = outboxDao.pending().size,
                ))
            }

            // Null means the gateway never answered. The intent stays exactly where it is, so the next
            // trigger retries it; a repeat write cannot duplicate a document because every write is a
            // merge keyed by the row's own id.
            if (outcome == null) return false

            when (outcome) {
                Outcome.WRITTEN -> written++
                Outcome.TOMBSTONED -> tombstoned++
                Outcome.SKIPPED -> skipped++
            }
            outboxDao.delete(intent.id)
            return true
        }

        try {
            for (intent in outboxDao.pending()) {
                if (!attempt(intent)) unanswered += intent
            }
            // One retry, at the end of the pass: a cold connection usually answers by then, and going
            // last keeps a row's own writes in the order they were queued.
            for (intent in unanswered.toList()) {
                if (attempt(intent)) unanswered -= intent
            }
        } catch (aborted: DrainAborted) {
            return aborted.result
        }

        val remaining = outboxDao.pending().size
        return DrainResult(
            written = written,
            tombstoned = tombstoned,
            skipped = skipped,
            error = if (unanswered.isEmpty()) null else NO_ANSWER,
            remaining = remaining,
            timedOut = unanswered.isNotEmpty(),
        )
    }

    /** Carries a hard failure out of the pass without pretending the batch finished. */
    private class DrainAborted(val description: String, val result: DrainResult) : Exception(description)

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

        /**
         * A parent's addition to the solids catalogue (`foods_pending`).
         *
         * The row is re-read, so the name that travels is the current one. A row the catalogue has since
         * published is SKIPPED rather than rewritten: `custom` is false there, and re-adding it to the
         * pending list would claim an addition the owner already made.
         */
        ENTITY_FOOD_PENDING -> {
            val food = foodDao?.byId(intent.entityId)
            if (intent.op == OP_DELETE) {
                gateway.deletePendingFood(intent.entityId)
                Outcome.TOMBSTONED
            } else if (food != null && food.custom) {
                gateway.writePendingFood(food, clock.millis())
                Outcome.WRITTEN
            } else {
                Outcome.SKIPPED
            }
        }

        /** A rename proposal for a catalogue entry, which changes nothing until the owner publishes. */
        ENTITY_FOOD_PROPOSAL -> {
            val food = foodDao?.byId(intent.entityId)
            val to = food?.localName
            if (food != null && to != null) {
                gateway.writeFoodProposal(food.id, food.name, to, clock.millis())
                Outcome.WRITTEN
            } else {
                // The rename was cleared before the drain ran. A proposal with no opinion is not a proposal.
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

        /** A parent's addition to the solids catalogue, written to `foods_pending` (ADR-0002). */
        const val ENTITY_FOOD_PENDING = "FOOD_PENDING"

        /** A rename this parent would like the owner to make, written to `foods_proposals`. */
        const val ENTITY_FOOD_PROPOSAL = "FOOD_PROPOSAL"
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
