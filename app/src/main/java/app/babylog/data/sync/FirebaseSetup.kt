package app.babylog.data.sync

import android.content.Context
import app.babylog.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * Builds the default Firebase app from values the build read out of a **local** `firebase.properties`.
 *
 * Why not the usual `com.google.gms.google-services` plugin plus `google-services.json`? Because that
 * plugin fails the build when the JSON is absent, and the JSON is gitignored (it carries the project's
 * keys). A fresh clone would then not even be able to run the unit tests. Reading the same three values
 * into `BuildConfig` keeps the secret out of the repository *and* keeps the build green without it: with
 * no properties file the app starts as the single-phone logger it has always been, and Settings says so.
 *
 * `firebase.properties` needs:
 *
 * ```
 * firebase.projectId=<project id>
 * firebase.appId=<mobilesdk app id, 1:...:android:...>
 * firebase.apiKey=<web API key>
 * firebase.webClientId=<OAuth web client id>   # only for Google Sign-In
 * firebase.allowedUids=<uid-one>,<uid-two>     # only for the plain-language refusal
 * ```
 */
object FirebaseSetup {

    /** True when this build was given enough to talk to Firebase. */
    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank()

    /** True when Google Sign-In has a client id to present. Hidden rather than broken when it does not. */
    val supportsGoogle: Boolean get() = isConfigured && BuildConfig.FIREBASE_WEB_CLIENT_ID.isNotBlank()

    /**
     * The two uids this log belongs to, for the refusal message.
     *
     * Empty means "not configured here", and the app then trusts the Firestore rules alone: the rules
     * are the real gate either way, this list only lets the app say *why* a third account was refused
     * instead of showing a vague read error.
     */
    val allowedUids: Set<String>
        get() = BuildConfig.FIREBASE_ALLOWED_UIDS
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** The default app, initialised once. Null when this build has no configuration. */
    fun app(context: Context): FirebaseApp? {
        if (!isConfigured) return null
        FirebaseApp.getApps(context).firstOrNull()?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .build()
        return Firebase.initializeSafely(context, options)
    }

    fun auth(context: Context): FirebaseAuth? = app(context)?.let { FirebaseAuth.getInstance(it) }

    fun firestore(context: Context): FirebaseFirestore? = app(context)?.let { app ->
        FirebaseFirestore.getInstance(app).also { firestore ->
            // MEMORY-ONLY cache, deliberately. With the default persistent cache, `set()` completes as
            // soon as the write is in Firestore's own local cache, so an offline drain would look
            // acknowledged and the outbox row would be deleted while nothing had reached the server.
            // Room + `outbox` is this app's one queue; Firestore must not grow a second one.
            runCatching {
                firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }
}

/** Indirection so a misconfigured project surfaces in Settings instead of crashing the launch. */
private object Firebase {
    fun initializeSafely(context: Context, options: FirebaseOptions): FirebaseApp? =
        runCatching { FirebaseApp.initializeApp(context, options) }.getOrNull()
}
