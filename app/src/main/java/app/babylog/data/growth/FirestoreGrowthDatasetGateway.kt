package app.babylog.data.growth

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class FirestoreGrowthDatasetGateway(private val firestore: FirebaseFirestore) : GrowthDatasetGateway {

    override suspend fun fetchManifest(): GrowthDatasetManifest {
        val snapshot = firestore.collection(COLLECTION).document(MANIFEST).get().awaitTask()
        val data = snapshot.data ?: throw IllegalStateException("growth manifest is missing")
        return GrowthDatasetManifest.parse(org.json.JSONObject(data).toString())
    }

    override suspend fun fetchDataset(): String {
        val snapshot = firestore.collection(COLLECTION).document(DATASET).get().awaitTask()
        val data = snapshot.data ?: throw IllegalStateException("growth dataset is missing")
        return data["data"] as? String ?: throw IllegalStateException("growth dataset has no data")
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase call failed"))
        }
    }

    companion object {
        const val COLLECTION = "reference"
        const val MANIFEST = "growth_manifest"
        const val DATASET = "growth_dataset"
    }
}