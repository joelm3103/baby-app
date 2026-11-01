package app.babylog.data.sync

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.data.local.FoodEntity
import kotlinx.coroutines.flow.Flow

/** Everything the server holds, as one snapshot. Small enough to be a list: one family's log. */
data class RemoteDataset(
    val children: List<RemoteRow> = emptyList(),
    val events: List<RemoteRow> = emptyList(),
)

/**
 * The seam between this app and whoever holds the other phone's copy.
 *
 * One interface with a Firestore implementation and a fake: that is the entire JVM test story for sync.
 * There is no emulator in this project (Phase 0 dropped it) and Robolectric cannot reach Firestore, so
 * anything behind this interface is proved by a fake and only *Firestore itself* needs two phones.
 *
 * Every write is an upsert with `merge`, keyed by the row's own id, which is what makes a drain safe to
 * repeat: writing the same document twice leaves one document.
 */
interface SyncGateway {
    suspend fun writeChild(child: ChildEntity)

    suspend fun writeEvent(event: EventEntity)

    /** A tombstone for a child whose row is already gone locally, so there is no payload to send. */
    suspend fun deleteChild(id: String, updatedAt: Long)

    suspend fun deleteEvent(id: String, updatedAt: Long)

    // ---- the solids catalogue (ADR-0002) ----------------------------------
    //
    // Three writes, and none of them merges: `foods_pending` is a list of additions and
    // `foods_proposals` is a list of opinions, both keyed by the food's own id so a retried drain is a
    // repeat rather than a duplicate. The catalogue itself lives under `reference/`, which clients may
    // read and never write.

    /** Publish one parent's addition so the other phone can read the name. */
    suspend fun writePendingFood(food: FoodEntity, at: Long)

    /** Withdraw an addition this phone made. A pending document is an addition, so removing it is the news. */
    suspend fun deletePendingFood(id: String)

    /** Ask the owner to rename a catalogue entry. Changes nothing on any phone by itself. */
    suspend fun writeFoodProposal(id: String, from: String, to: String, at: Long)

    /** Used by the first-sign-in seed freshness check and by tests; one read, no listener. */
    suspend fun fetchAll(): RemoteDataset

    /** Live inbound changes. Emits a full dataset per snapshot, which is what the merge consumes. */
    fun observe(): Flow<RemoteDataset>
}
