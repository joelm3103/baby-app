package app.babylog.data.sync

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
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

    /** Used by the first-sign-in seed freshness check and by tests; one read, no listener. */
    suspend fun fetchAll(): RemoteDataset

    /** Live inbound changes. Emits a full dataset per snapshot, which is what the merge consumes. */
    fun observe(): Flow<RemoteDataset>
}
