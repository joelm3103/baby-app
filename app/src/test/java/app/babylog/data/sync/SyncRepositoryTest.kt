package app.babylog.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.local.AppDatabase
import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.data.repo.SettingsRepository
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sign-in, the first-sign-in seed and the inbound merge.
 *
 * What this proves is the part that is expensive to get wrong on two real phones: a log that already
 * exists gets pushed rather than stranded, a newer local edit is never overwritten by an older server
 * copy, and a deletion travels (and never resurrects).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var gateway: FakeSyncGateway
    private lateinit var auth: FakeAuthenticator
    private lateinit var settings: SettingsRepository
    private lateinit var scope: CoroutineScope
    private lateinit var repo: SyncRepository
    private lateinit var tempDir: File
    private val t0 = 1_760_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(context.cacheDir, "sync-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "settings.preferences_pb")
        }
        settings = SettingsRepository(store)
        gateway = FakeSyncGateway()
        auth = FakeAuthenticator()
        repo = SyncRepository(
            authenticator = auth,
            gateway = gateway,
            drainer = OutboxDrainer(db.outboxDao(), db.childDao(), db.eventDao(), gateway, clock()),
            childDao = db.childDao(),
            eventDao = db.eventDao(),
            outboxDao = db.outboxDao(),
            settings = settings,
            scope = scope,
            clock = clock(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        tempDir.deleteRecursively()
    }



    @Test
    fun `a snapshot that arrives during a reset does not resurrect the wiped rows`() = runBlocking {
        seedLocalLog()
        val stream = MutableStateFlow(RemoteDataset())
        gateway.observeFlow = stream
        repo.signIn("parent-one@example.com", "secret")
        // NOT settle(): that joins every child of the scope, and the listener collects a StateFlow that
        // never completes — which is the point of a live stream. A few yields is all this needs.
        repeat(3) { yield() }
        // The other phone's copy of e1, as it looked BEFORE the wipe.
        val stale = RemoteDataset(events = listOf(RemoteRow("e1", t0, deleted = false, payload = emptyMap())))

        // What the phone caught in Phase 15: the wipe emptied the tables, the drain deleted the server
        // rows, and the listener then applied the pre-delete snapshot — 15 events back in a database with
        // an empty outbox. Detaching the listener for the duration is what closes that window.
        val stillEmptyDuringWipe = repo.withoutInboundApply {
            db.eventDao().deleteAll()
            stream.value = stale
            repeat(3) { yield() }
            val afterSnapshot = db.eventDao().allIncludingDeleted().size
            stream.value = RemoteDataset()
            repeat(3) { yield() }
            afterSnapshot
        }

        assertEquals(0, stillEmptyDuringWipe)
        assertTrue(db.eventDao().allIncludingDeleted().isEmpty())
    }

    // ---- Phase 15: the reset gate and the server wipe ----------------------

    @Test
    fun `a reset is allowed while signed out, so the tombstones wait for the next sign-in`() = runBlocking {
        seedLocalLog()

        // Nobody to tell yet. Refusing here would block a parent who is deliberately signed out, and
        // the queued tombstones are what stop those rows coming back at the next sign-in.
        assertEquals(ResetGate.Allowed, repo.resetGate())
    }

    @Test
    fun `a reset is refused when the queue cannot travel`() = runBlocking {
        seedLocalLog()
        repo.signIn("parent-one@example.com", "secret")
        settle()
        // Work that is waiting when the probe runs: an empty queue would drain successfully and the gate
        // would (correctly) allow the reset, which is not the case under test.
        db.outboxDao().enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, "e1", deleted = false, queuedAt = t0))
        gateway.failAfter = 0

        // A wipe the server never hears about is merged straight back in, so this must refuse rather
        // than perform the reset and let the next snapshot undo it.
        val gate = repo.resetGate()
        assertTrue(gate is ResetGate.Refused)
    }


    @Test
    fun `a reset is refused with an empty queue while the phone is offline`() = runBlocking {
        seedLocalLog()
        repo = repoWith(network = { false })
        repo.signIn("parent-one@example.com", "secret")
        settle()
        db.outboxDao().deleteEverything()

        // An empty queue drains "successfully" without touching the network, so the probe cannot tell the
        // difference between a reachable server and a phone in a tunnel. The connectivity check is what
        // stops a reset from queueing deletions that cannot travel.
        val gate = repo.resetGate()
        assertTrue(gate is ResetGate.Refused)
    }

    @Test
    fun `delete all server data targets rows only the server knows about`() = runBlocking {
        seedLocalLog()
        repo.signIn("parent-one@example.com", "secret")
        settle()
        db.outboxDao().deleteEverything()
        // A row the other phone logged and this one never received.
        gateway.inbound = RemoteDataset(
            children = listOf(RemoteRow("ghost-child", t0, deleted = false, payload = emptyMap())),
            events = listOf(RemoteRow("ghost-event", t0, deleted = false, payload = emptyMap())),
        )

        val queued = repo.reconcileRemote(emptySet(), emptySet())

        assertEquals(2, queued)
        val intents = db.outboxDao().pending()
        assertEquals(setOf("ghost-child", "ghost-event"), intents.map { it.entityId }.toSet())
        assertTrue(intents.all { it.op == "DELETE" })
    }

    @Test
    fun `reconciling keeps exactly the ids it is given`() = runBlocking {
        seedLocalLog()
        repo.signIn("parent-one@example.com", "secret")
        settle()
        db.outboxDao().deleteEverything()
        // The server still holds the two seeded children; an import of a file containing only e1's child
        // must tombstone the twin it does not contain, or the next snapshot brings it back.
        gateway.inbound = RemoteDataset(
            children = listOf(
                RemoteRow("first-twin", t0, deleted = false, payload = emptyMap()),
                RemoteRow("second-twin", t0, deleted = false, payload = emptyMap()),
                RemoteRow("imported-child", t0, deleted = false, payload = emptyMap()),
            ),
            events = listOf(
                RemoteRow("kept-event", t0, deleted = false, payload = emptyMap()),
                RemoteRow("dropped-event", t0, deleted = false, payload = emptyMap()),
            ),
        )

        val queued = repo.reconcileRemote(keepChildIds = setOf("imported-child"), keepEventIds = setOf("kept-event"))

        assertEquals(3, queued)
        val tombstoned = db.outboxDao().pending().map { it.entityId }.toSet()
        assertEquals(setOf("first-twin", "second-twin", "dropped-event"), tombstoned)
    }

    // ---- the first sign-in seed --------------------------------------------

    @Test
    fun `the first sign-in pushes the log that is already on this phone`() = runBlocking {
        seedLocalLog()

        repo.signIn("parent-one@example.com", "secret")
        settle()

        // Two children and two events that existed before anyone signed in.
        assertEquals(setOf("first-twin", "second-twin"), gateway.childWrites.map { it.id }.toSet())
        assertEquals(setOf("e1", "e2"), gateway.eventWrites.map { it.id }.toSet())
        // The locally deleted event travels as a tombstone rather than vanishing or resurrecting.
        assertTrue(gateway.eventWrites.single { it.id == "e2" }.deleted)
    }

    @Test
    fun `the seed runs once, however many times the app starts`() = runBlocking {
        seedLocalLog()

        repo.seedIfNeeded()
        val afterFirst = db.outboxDao().pending().size
        repo.seedIfNeeded()

        assertEquals(afterFirst, db.outboxDao().pending().size)
        assertTrue(settings.syncSeedDone.first())
    }

    @Test
    fun `the seed does not duplicate an intent that is already queued`() = runBlocking {
        seedLocalLog()
        // A write made since the last drain: the seed must adopt it, not add a second intent for it.
        db.outboxDao().enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, "e1", deleted = false, queuedAt = t0))

        repo.seedIfNeeded()

        assertEquals(4, db.outboxDao().pending().size)
        assertEquals(1, db.outboxDao().pending().count { it.entityId == "e1" })
    }

    // ---- inbound ------------------------------------------------------------

    @Test
    fun `a row this phone has never seen is inserted`() = runBlocking {
        repo.applyRemote(
            RemoteDataset(
                children = listOf(remoteChild("third-child", updatedAt = t0)),
                events = listOf(remoteEvent("e9", updatedAt = t0)),
            ),
        )

        assertNotNull(db.childDao().byId("third-child"))
        assertNotNull(db.eventDao().byId("e9"))
    }

    @Test
    fun `a newer remote edit overwrites the local row`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0))

        repo.applyRemote(RemoteDataset(events = listOf(remoteEvent("e1", amountMl = 150, updatedAt = t0 + 60_000))))

        assertEquals(150, db.eventDao().byId("e1")!!.amountMl)
    }

    @Test
    fun `a newer local edit is queued for upload instead of overwritten`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 150, updatedAt = t0 + 60_000))

        repo.applyRemote(RemoteDataset(events = listOf(remoteEvent("e1", amountMl = 90, updatedAt = t0))))

        // The local row is untouched, and the server hears about it.
        assertEquals(150, db.eventDao().byId("e1")!!.amountMl)
        val intent = db.outboxDao().pending().single()
        assertEquals("e1", intent.entityId)
        assertEquals("UPSERT", intent.op)
    }

    @Test
    fun `the same losing snapshot twice queues only one intent`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 150, updatedAt = t0 + 60_000))
        val snapshot = RemoteDataset(events = listOf(remoteEvent("e1", amountMl = 90, updatedAt = t0)))

        repo.applyRemote(snapshot)
        repo.applyRemote(snapshot)

        assertEquals(1, db.outboxDao().pending().size)
    }

    @Test
    fun `a remote tombstone soft-deletes the local row and keeps the tombstone`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0))

        repo.applyRemote(RemoteDataset(events = listOf(remoteTombstone("e1", updatedAt = t0 + 5_000))))

        val local = db.eventDao().byId("e1")!!
        assertTrue(local.deleted)
        // The tombstone keeps the remote timestamp: bumping it locally would make this phone look newer
        // than the deletion and push the row back.
        assertEquals(t0 + 5_000, local.updatedAt)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `a tombstone for an id this phone never had does not invent a row`() = runBlocking {
        repo.applyRemote(
            RemoteDataset(
                children = listOf(remoteTombstone("removed-child", updatedAt = t0)),
                events = listOf(remoteTombstone("removed-event", updatedAt = t0)),
            ),
        )

        assertNull(db.childDao().byId("removed-child"))
        assertNull(db.eventDao().byId("removed-event"))
        assertTrue(db.childDao().allIncludingDeleted().isEmpty())
    }

    @Test
    fun `a remote child tombstone archives the child here too`() = runBlocking {
        db.childDao().upsert(child("first-twin", t0))

        repo.applyRemote(RemoteDataset(children = listOf(remoteTombstone("first-twin", t0 + 1_000))))

        assertTrue(db.childDao().byId("first-twin")!!.deleted)
        assertTrue(db.childDao().observeAll().first().isEmpty())
    }

    @Test
    fun `a restore re-queues the restored log even though it had already been seeded`() = runBlocking {
        seedLocalLog()
        repo.signIn("parent-one@example.com", "secret")
        settle()
        assertTrue(db.outboxDao().pending().isEmpty())

        // What an import does: the rows are replaced and the queue is wiped with them.
        db.eventDao().deleteAll()
        db.eventDao().upsert(event("e5", amountMl = 200, updatedAt = t0))

        repo.reseedAfterRestore()

        // The restored row reaches the server; without the reseed it would have sat here forever while
        // Firestore still described the pre-restore log.
        assertTrue(gateway.eventWrites.any { it.id == "e5" })
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    // ---- sign-in states ----------------------------------------------------

    @Test
    fun `a third account is refused in plain language and shares nothing`() = runBlocking {
        auth.nextResult = SignInResult.Refused(Account("uid-three", "stranger@example.com"))
        seedLocalLog()

        repo.signIn("stranger@example.com", "secret")
        settle()

        assertTrue(repo.state.value is SyncState.Refused)
        // Nothing was seeded, so nothing was ever queued to leave this phone.
        assertTrue(gateway.writeCount == 0)
        assertTrue(repo.message.value!!.contains("stranger@example.com"))
    }

    @Test
    fun `a refused account is not left signed in`() = runBlocking {
        auth.nextResult = SignInResult.Refused(Account("uid-three", "stranger@example.com"))

        repo.signIn("stranger@example.com", "secret")
        settle()

        assertNull(auth.currentAccount)
    }

    @Test
    fun `signing out stops sharing but keeps the log`() = runBlocking {
        seedLocalLog()
        repo.signIn("parent-one@example.com", "secret")
        settle()

        repo.signOut()

        assertEquals(SyncState.SignedOut, repo.state.value)
        assertTrue(auth.signedOut)
        assertEquals(2, db.eventDao().allIncludingDeleted().size)
    }

    @Test
    fun `an unconfigured build is inert and says so`() = runBlocking {
        val unconfigured = SyncRepository(
            authenticator = UnconfiguredAuthenticator,
            gateway = null,
            drainer = null,
            childDao = db.childDao(),
            eventDao = db.eventDao(),
            outboxDao = db.outboxDao(),
            settings = settings,
            scope = scope,
        )
        seedLocalLog()

        unconfigured.signIn("parent-one@example.com", "secret")
        settle()

        assertEquals(SyncState.NotConfigured, unconfigured.state.value)
        assertNull(unconfigured.syncNow())
        // Nothing is seeded and nothing is sent: the row stays queued, which is what an offline phone
        // with no sync configured looks like.
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `pending writes counts what is waiting`() = runBlocking {
        seedLocalLog()

        repo.seedIfNeeded()

        assertEquals(4, repo.pendingWrites.first())
    }

    // ---- a drain that cannot reach the server -------------------------------

    /**
     * The Phase-13 finding, made into a test: the first drain fails (a client that is not ready yet,
     * which is what the phone showed as two seeded rows sitting in the outbox), and the queue still
     * empties without anyone tapping **Sync now** — because the listener drains once its first snapshot
     * has been applied.
     */
    @Test
    fun `the seed drains itself after the listener's first snapshot, with no Sync now`() = runBlocking {
        seedLocalLog()
        gateway.failOnce = true

        repo.signIn("parent-one@example.com", "secret")
        settle()

        assertTrue(
            "the queue must empty on its own: ${db.outboxDao().pending().size} left",
            db.outboxDao().pending().isEmpty(),
        )
        assertEquals(setOf("first-twin", "second-twin"), gateway.childWrites.map { it.id }.toSet())
        assertEquals(setOf("e1", "e2"), gateway.eventWrites.map { it.id }.toSet())
    }

    @Test
    fun `an offline phone is told it is waiting for a network connection`() = runBlocking {
        queueOneBottle()
        gateway.hangForever = true

        val subject = repoWith(NetworkStatus { false })
        val result = subject.syncNow()

        assertTrue(result!!.timedOut)
        assertEquals(1, result.remaining)
        assertEquals("Waiting for a network connection — 1 change queued.", subject.message.first())
    }

    @Test
    fun `a silent server on a connected phone is not blamed on the connection`() = runBlocking {
        queueOneBottle()
        gateway.hangForever = true

        val subject = repoWith(NetworkStatus { true })
        subject.syncNow()

        assertEquals("The server did not answer — 1 change queued.", subject.message.first())
    }

    @Test
    fun `a timed-out drain releases the lock, so the next Sync now runs`() = runBlocking {
        queueOneBottle()
        gateway.hangForever = true
        val patient = repoWith(NetworkStatus { false })
        patient.syncNow()

        // The point of the fix: with the Mutex still held this would hang instead of finishing.
        gateway.hangForever = false
        val second = withTimeout(5_000) { patient.syncNow() }

        assertTrue(second!!.ok)
        assertEquals(1, second.written)
        assertTrue(db.outboxDao().pending().isEmpty())
        assertEquals("Sent 1 change(s).", patient.message.first())
    }

    @Test
    fun `the queued count is pluralised`() = runBlocking {
        listOf("e1", "e2", "e3").forEach { id ->
            db.eventDao().upsert(event(id, amountMl = 90, updatedAt = t0))
            db.outboxDao().enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, id, deleted = false, queuedAt = t0))
        }
        gateway.hangForever = true

        val subject = repoWith(NetworkStatus { false })
        subject.syncNow()

        assertEquals("Waiting for a network connection — 3 changes queued.", subject.message.first())
    }

    // ---- helpers ------------------------------------------------------------


    /** The same repository, with a different idea of whether there is a network. */
    private fun repoWith(network: () -> Boolean): SyncRepository = SyncRepository(
        authenticator = auth,
        gateway = gateway,
        drainer = OutboxDrainer(db.outboxDao(), db.childDao(), db.eventDao(), gateway, clock()),
        childDao = db.childDao(),
        eventDao = db.eventDao(),
        outboxDao = db.outboxDao(),
        settings = settings,
        scope = scope,
        clock = clock(),
        network = NetworkStatus { network() },
    )

    private fun clock() = Clock.fixed(Instant.ofEpochMilli(t0), ZoneOffset.UTC)

    /** The same repository with a chosen network state and a timeout a test can sit through. */
    private fun repoWith(network: NetworkStatus, writeTimeoutMs: Long = 60): SyncRepository = SyncRepository(
        authenticator = auth,
        gateway = gateway,
        drainer = OutboxDrainer(
            db.outboxDao(), db.childDao(), db.eventDao(), gateway, clock(), writeTimeoutMs,
        ),
        childDao = db.childDao(),
        eventDao = db.eventDao(),
        outboxDao = db.outboxDao(),
        settings = settings,
        scope = scope,
        clock = clock(),
        network = network,
    )

    /** One bottle, already logged, already queued. */
    private suspend fun queueOneBottle() {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0))
        db.outboxDao().enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, "e1", deleted = false, queuedAt = t0))
    }

    /** Waits for the work `signIn` launches: the seed, the drain and the listener. */
    private suspend fun settle() {
        repeat(5) {
            yield()
            scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        }
    }

    private suspend fun seedLocalLog() {
        db.childDao().upsertAll(listOf(child("first-twin", t0), child("second-twin", t0)))
        db.eventDao().upsertAll(
            listOf(
                event("e1", amountMl = 90, updatedAt = t0),
                event("e2", amountMl = 120, updatedAt = t0, deleted = true),
            ),
        )
    }

    private fun child(id: String, updatedAt: Long) = ChildEntity(
        id = id,
        name = if (id == "first-twin") "First Twin" else "Second Twin",
        birthDateEpochDay = 20_454,
        sex = "BOY",
        updatedAt = updatedAt,
    )

    private fun event(id: String, amountMl: Int, updatedAt: Long, deleted: Boolean = false) = EventEntity(
        id = id,
        childId = "first-twin",
        type = "BOTTLE",
        startedAt = t0,
        amountMl = amountMl,
        createdBy = "parent-one",
        updatedAt = updatedAt,
        deleted = deleted,
    )

    private fun remoteChild(id: String, updatedAt: Long) = remoteRowOf(id, child(id, updatedAt).toSyncMap())!!

    private fun remoteEvent(id: String, amountMl: Int = 90, updatedAt: Long) =
        remoteRowOf(id, event(id, amountMl, updatedAt).toSyncMap())!!

    private fun remoteTombstone(id: String, updatedAt: Long) = remoteRowOf(id, tombstone(id, updatedAt))!!
}
