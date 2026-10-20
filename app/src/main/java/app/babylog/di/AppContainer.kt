package app.babylog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.babylog.data.local.AppDatabase
import app.babylog.data.local.FoodCatalogueParser
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.FoodRepository
import app.babylog.data.repo.MaintenanceRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.data.growth.GrowthReference
import app.babylog.data.growth.GrowthReferenceParser
import app.babylog.data.medications.MedicationCatalogueParser
import app.babylog.data.sync.AccountAuthenticator
import app.babylog.data.sync.FirebaseAuthenticator
import app.babylog.data.sync.FirebaseSetup
import app.babylog.data.sync.FirestoreGateway
import app.babylog.data.sync.OutboxDrainer
import app.babylog.data.sync.SyncGateway
import app.babylog.data.sync.SyncRepository
import app.babylog.data.sync.UnconfiguredAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Manual dependency container. No Hilt/Koin: the graph is five objects deep and never changes shape,
 * so a framework would be ceremony that also slows the build down.
 */
class AppContainer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase by lazy { AppDatabase.build(context) }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("settings")
        }
    }

    val events: EventRepository by lazy {
        EventRepository(
            eventDao = database.eventDao(),
            outboxDao = database.outboxDao(),
            foodDao = database.foodDao(),
            // A tap drains within a second when signed in; when signed out this is a no-op and the
            // intent waits, which is what offline-first means here.
            onQueued = { sync.requestDrain() },
        )
    }

    val children: ChildRepository by lazy {
        ChildRepository(
            childDao = database.childDao(),
            outboxDao = database.outboxDao(),
            onQueued = { sync.requestDrain() },
        )
    }

    val settings: SettingsRepository by lazy { SettingsRepository(dataStore) }

    /** Whole-database operations: backup, restore, archive and permanent delete. */
    val maintenance: MaintenanceRepository by lazy {
        MaintenanceRepository(database = database, onQueued = { sync.requestDrain() })
    }

    /**
     * Whether this build can talk to Firebase at all.
     *
     * Resolved once and off the launch path: a project that is configured but unreachable must still
     * leave the app a working single-phone logger, not a blank screen.
     */
    private val firebaseReady: Boolean by lazy {
        FirebaseSetup.isConfigured && runCatching { FirebaseSetup.app(context) }.getOrNull() != null
    }

    private val syncGateway: SyncGateway? by lazy {
        if (!firebaseReady) null else FirebaseSetup.firestore(context)?.let(::FirestoreGateway)
    }

    private val authenticator: AccountAuthenticator by lazy {
        val auth = if (firebaseReady) FirebaseSetup.auth(context) else null
        if (auth == null) {
            UnconfiguredAuthenticator
        } else {
            FirebaseAuthenticator(auth, FirebaseSetup.allowedUids)
        }
    }

    private val outboxDrainer: OutboxDrainer? by lazy {
        syncGateway?.let {
            OutboxDrainer(
                outboxDao = database.outboxDao(),
                childDao = database.childDao(),
                eventDao = database.eventDao(),
                gateway = it,
            )
        }
    }

    /** Two accounts, one shared log. Constructible on every build; inert without configuration. */
    val sync: SyncRepository by lazy {
        SyncRepository(
            authenticator = authenticator,
            gateway = syncGateway,
            drainer = outboxDrainer,
            childDao = database.childDao(),
            eventDao = database.eventDao(),
            outboxDao = database.outboxDao(),
            settings = settings,
            scope = scope,
            googleSignInAvailable = FirebaseSetup.supportsGoogle,
        )
    }

    /**
     * The solids catalogue. The asset is read on `seedIfNeeded`, never at construction: the list is
     * only needed once the picker or the first-run seed asks for it.
     */
    val foods: FoodRepository by lazy {
        FoodRepository(
            foodDao = database.foodDao(),
            loadCatalogue = {
                context.assets.open(FoodCatalogueParser.ASSET_PATH)
                    .bufferedReader()
                    .use { it.readText() }
                    .let(FoodCatalogueParser::parse)
            },
        )
    }

    private var cachedGrowth: GrowthReference? = null
    /**
     * The bundled growth reference, parsed once.
     *
     * Suspending and cached rather than a `by lazy` val: the asset is ~490 KB of JSON, and parsing it on
     * the main thread would be a visible stall. Cached because a percentile must come from one dataset
     * for the whole session - two parses would be two copies of the truth.
     */
    suspend fun growth(): GrowthReference = cachedGrowth ?: withContext(Dispatchers.IO) {
        cachedGrowth ?: GrowthReferenceParser.parse(
            context.assets.open(GrowthReferenceParser.ASSET_PATH)
                .bufferedReader()
                .use { it.readText() },
        ).also { cachedGrowth = it }
    }

    private var cachedMedications: List<String>? = null

    /**
     * The bundled medicine NAMES for the health sheet's picker. Parsed once and cached.
     *
     * Suspending rather than a `by lazy` val for the same reason as [growth]: the asset is read from
     * disk, and a file read on the main thread is a stall the first time a sheet opens. Small enough
     * (~2 KB) that it does not need the on-demand treatment the 490 KB growth reference gets.
     */
    suspend fun medications(): List<String> = cachedMedications ?: withContext(Dispatchers.IO) {
        cachedMedications ?: MedicationCatalogueParser.parse(
            context.assets.open(MedicationCatalogueParser.ASSET_PATH)
                .bufferedReader()
                .use { it.readText() },
        ).also { cachedMedications = it }
    }
}
