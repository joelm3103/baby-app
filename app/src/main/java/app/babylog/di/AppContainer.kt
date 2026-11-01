package app.babylog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.babylog.data.local.AppDatabase
import app.babylog.data.local.toDomain
import app.babylog.data.local.FoodCatalogueParser
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.FoodRepository
import app.babylog.data.repo.MaintenanceRepository
import app.babylog.data.reminders.ReminderAnchors
import app.babylog.data.reminders.ReminderChildren
import app.babylog.data.reminders.ReminderDeps
import app.babylog.data.reminders.ReminderNotifications
import app.babylog.data.reminders.ReminderScheduler
import app.babylog.data.reminders.ReminderStore
import app.babylog.data.reminders.Reminders
import app.babylog.data.repo.SettingsRepository
import app.babylog.data.growth.GrowthReference
import app.babylog.data.growth.GrowthReferenceParser
import app.babylog.data.growth.GrowthDatasetRepository
import app.babylog.data.growth.FirestoreGrowthDatasetGateway
import app.babylog.data.medications.MedicationCatalogueParser
import app.babylog.data.sync.AccountAuthenticator
import app.babylog.data.sync.AndroidNetworkStatus
import app.babylog.data.sync.FirebaseAuthenticator
import app.babylog.data.sync.FirebaseSetup
import app.babylog.data.sync.FirestoreGateway
import app.babylog.data.sync.FoodCatalogueGateway
import app.babylog.data.sync.OutboxDrainer
import app.babylog.data.sync.SyncGateway
import app.babylog.data.sync.SyncRepository
import app.babylog.data.sync.UnconfiguredAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.babylog.domain.EventType
import app.babylog.domain.ReminderKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
            // Every logged event can move an anchor, so the alarms are recomputed right after the write.
            onLogged = { refreshReminders() },
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

    /** One Firestore handle, exposed through two seams: the log's, and the catalogue's (ADR-0002). */
    private val firestoreGateway: FirestoreGateway? by lazy {
        if (!firebaseReady) null else FirebaseSetup.firestore(context)?.let { firestore ->
            FirestoreGateway(firestore) { authenticator.currentAccount?.uid }
        }
    }

    private val syncGateway: SyncGateway? get() = firestoreGateway

    private val foodCatalogueGateway: FoodCatalogueGateway? get() = firestoreGateway

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
                foodDao = database.foodDao(),
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
            foodCatalogue = foodCatalogueGateway,
            foods = foods,
            network = AndroidNetworkStatus(context),
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
            outboxDao = database.outboxDao(),
            settings = settings,
            loadCatalogue = {
                context.assets.open(FoodCatalogueParser.ASSET_PATH)
                    .bufferedReader()
                    .use { it.readText() }
                    .let(FoodCatalogueParser::parse)
            },
            // Deferred to call time: `sync` is built after this object, and the drain only needs to be
            // requested once a food has been queued.
            onQueued = { sync.requestDrain() },
        )
    }

    // ---- reminders (Phase 13.4) -------------------------------------------

    /**
     * A reminder notification that was tapped, waiting for the UI to open the right sheet.
     *
     * A flow on the container rather than an Intent extra read inside Compose, because the tap can arrive while
     * the activity is already running (`singleTop` + `onNewIntent`), which means the composable has to be TOLD
     * rather than asked once at construction.
     */
    val pendingSheetRequest = MutableStateFlow<SheetRequest?>(null)

    /**
     * The reminder engine, wired to the same database and preferences the UI uses.
     *
     * The three adapters below exist so the engine can be tested without Room or DataStore (see
     * [app.babylog.data.reminders.ReminderDeps]); on a phone they are just `first()` on the flows the app
     * already observes.
     */
    val reminders: Reminders by lazy {
        Reminders(
            deps = ReminderDeps(
                store = object : ReminderStore {
                    override suspend fun configs() = settings.reminders.first()

                    override suspend fun stampAnchor(key: ReminderKey, at: Long) {
                        val config = settings.reminders.first()[key] ?: return
                        if (config.anchorAt == null) settings.setReminder(key, config.copy(anchorAt = at))
                    }

                    override suspend fun toInterval(key: ReminderKey) {
                        val config = settings.reminders.first()[key] ?: return
                        settings.setReminder(
                            key,
                            config.copy(
                                mode = app.babylog.domain.ReminderMode.AFTER_LAST,
                                timesOfDay = emptyList(),
                            ),
                        )
                    }
                },
                anchors = object : ReminderAnchors {
                    override suspend fun latestAnchor(type: EventType, childId: String): Long? =
                        database.eventDao().latestOnce(type.name, childId)?.startedAt
                },
                children = object : ReminderChildren {
                    override suspend fun all() = database.childDao().all().map { it.toDomain() }
                },
                scheduler = ReminderScheduler(context),
                night = { settings.nightWindow.first() },
            ),
            notifications = ReminderNotifications(context),
            timeFormat = { settings.timeFormat.first() },
        )
    }

    /**
     * Arms every reminder. Called once per process start and after anything that moves an anchor.
     *
     * Fire-and-forget on the container's IO scope: nothing on screen depends on it, and a reminder that takes
     * 20ms longer than the first frame is not a problem worth blocking the first frame for.
     */
    /** Called from `MainActivity` for a fresh intent and for `onNewIntent`. */
    fun requestSheet(request: SheetRequest) {
        pendingSheetRequest.value = request
    }

    fun refreshReminders() {
        scope.launch {
            runCatching {
                reminders.ensureChannel()
                reminders.refresh()
            }
        }
    }

    val growthDatasetRepository: GrowthDatasetRepository by lazy {
        GrowthDatasetRepository(
            context = context,
            dataStore = dataStore,
            gateway = if (firebaseReady) FirebaseSetup.firestore(context)?.let(::FirestoreGrowthDatasetGateway) else null,
            bundledJson = context.assets.open(GrowthReferenceParser.ASSET_PATH)
                .bufferedReader().use { it.readText() },
        )
    }

    /**
     * The growth reference this app reads: a cached newer dataset if one was accepted, otherwise the
     * bundled asset. Parsed once per session.
     */
    suspend fun growth(): GrowthReference = growthDatasetRepository.current()

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

/** An activity's logging sheet, for one child, requested from outside the UI — a reminder's tap. */
data class SheetRequest(val type: EventType, val childId: String)
