package app.babylog.data.sync

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firestore, behind [SyncGateway].
 *
 * Two collections, `children` and `events`, whose document id is the row's own client-generated id.
 * That is not tidiness: it is what makes the drain idempotent. A retried write lands on the same
 * document instead of a second one, so "sent twice" can never mean "logged twice".
 *
 * Thin by design. The mapping lives in `SyncDocuments.kt` where a JVM test can drive it; this class is
 * a shell around `set(map, merge)` and `snapshot.data`, which only real Firestore can verify.
 */
class FirestoreGateway(private val firestore: FirebaseFirestore) : SyncGateway {

    override suspend fun writeChild(child: ChildEntity) {
        write(COLLECTION_CHILDREN, child.id, child.toSyncMap())
    }

    override suspend fun writeEvent(event: EventEntity) {
        write(COLLECTION_EVENTS, event.id, event.toSyncMap())
    }

    override suspend fun deleteChild(id: String, updatedAt: Long) {
        write(COLLECTION_CHILDREN, id, tombstone(id, updatedAt))
    }

    override suspend fun deleteEvent(id: String, updatedAt: Long) {
        write(COLLECTION_EVENTS, id, tombstone(id, updatedAt))
    }

    override suspend fun fetchAll(): RemoteDataset = RemoteDataset(
        children = readCollection(COLLECTION_CHILDREN),
        events = readCollection(COLLECTION_EVENTS),
    )

    /**
     * Both collections in one flow, so the merge always sees a consistent pair.
     *
     * The first emission waits for both initial snapshots: applying children without events would look
     * like "every event is new on the server" and is exactly the kind of half-state that makes a naive
     * merge oscillate.
     */
    override fun observe(): Flow<RemoteDataset> = callbackFlow {
        val children = mutableMapOf<String, RemoteRow>()
        val events = mutableMapOf<String, RemoteRow>()
        var childrenLoaded = false
        var eventsLoaded = false

        fun emitIfBothLoaded() {
            if (childrenLoaded && eventsLoaded) {
                trySend(RemoteDataset(children = children.values.toList(), events = events.values.toList()))
            }
        }

        val childRegistration = firestore.collection(COLLECTION_CHILDREN)
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) {
                    close(failure)
                    return@addSnapshotListener
                }
                children.clear()
                snapshot?.documents.orEmpty().forEach { doc -> doc.toRemoteRow()?.let { children[it.id] = it } }
                childrenLoaded = true
                emitIfBothLoaded()
            }

        val eventRegistration = firestore.collection(COLLECTION_EVENTS)
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) {
                    close(failure)
                    return@addSnapshotListener
                }
                events.clear()
                snapshot?.documents.orEmpty().forEach { doc -> doc.toRemoteRow()?.let { events[it.id] = it } }
                eventsLoaded = true
                emitIfBothLoaded()
            }

        awaitClose {
            childRegistration.remove()
            eventRegistration.remove()
        }
    }

    private suspend fun write(collection: String, id: String, map: SyncMap) {
        firestore.collection(collection).document(id).set(map, SetOptions.merge()).awaitTask()
    }

    private suspend fun readCollection(collection: String): List<RemoteRow> =
        firestore.collection(collection).get().awaitTask().documents.mapNotNull { it.toRemoteRow() }

    private fun DocumentSnapshot.toRemoteRow(): RemoteRow? =
        data?.let { remoteRowOf(id, it) }

    companion object {
        const val COLLECTION_CHILDREN = "children"
        const val COLLECTION_EVENTS = "events"
    }
}

/**
 * Google Play services hands back `Task`, not a coroutine.
 *
 * Twelve lines here instead of another dependency (`kotlinx-coroutines-play-services`) whose version has
 * to be kept in step with the Kotlin the AGP plugin compiles.
 */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase call failed with no exception"),
            )
        }
    }
}
