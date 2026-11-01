package app.babylog.data.sync

import app.babylog.data.local.ChildDao
import app.babylog.data.local.EventDao
import app.babylog.data.local.OutboxDao
import app.babylog.data.repo.FoodRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.SyncRow
import app.babylog.domain.SyncVerdict
import app.babylog.domain.resolveSync
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the Sync card in Settings renders. */
sealed interface SyncState {
    /** This build was given no Firebase configuration: a single-phone logger, and it says so. */
    data object NotConfigured : SyncState

    data object SignedOut : SyncState

    data class SignedIn(val email: String?) : SyncState

    /** Signed in to Firebase but not one of the two accounts this log belongs to. */
    data class Refused(val email: String?) : SyncState
}

/** Whether a reset may run now, and why not when it may not. */
sealed interface ResetGate {
    data object Allowed : ResetGate

    /** The drain could not reach the server, so a wipe now would be merged straight back in. */
    data class Refused(val message: String) : ResetGate
}

/**
 * The one place that decides what a sign-in, a write or an inbound change means for the log.
 *
 * Ordering, which is what makes the two phones converge:
 *
 * 1. On the first sign-in the local log is **seeded** into the outbox, so a log that already exists on
 *    this phone reaches Firestore instead of being stranded on the device that happened to log it.
 * 2. The drain runs, pushing queued writes.
 * 3. The inbound listener attaches. Remote rows are applied by [resolveSync]: later `updatedAt` wins, a
 *    deletion wins a tie, and a row the local phone holds a newer copy of is queued back into the
 *    outbox rather than overwritten.
 *
 * Convergence, not caution: an inbound row that loses to the local one becomes an outbox intent, the
 * drain writes it, the listener sees the write, and the two copies now agree — so the next comparison is
 * `IDENTICAL` and the ping-pong stops.
 */
class SyncRepository(
    private val authenticator: AccountAuthenticator,
    private val gateway: SyncGateway?,
    private val drainer: OutboxDrainer?,
    private val childDao: ChildDao,
    private val eventDao: EventDao,
    private val outboxDao: OutboxDao,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    /**
     * Whether the phone has a network, for the one sentence a failed drain can honestly say.
     *
     * Injected rather than read here: `ConnectivityManager` is an Android fact, `SyncRepository` is
     * not, and the two sentences need to be provable on the JVM.
     */
    private val network: NetworkStatus = NetworkStatus.AlwaysOnline,
    /**
     * Whether the Google button should be shown at all.
     *
     * A build with a Firebase project but no OAuth web client id can sign in with email and password;
     * showing a Google button that cannot work would be the app lying about what it can do.
     */
    val googleSignInAvailable: Boolean = false,
    /**
     * The published solids catalogue and the pending additions (ADR-0002), or null on a build with no
     * Firebase configuration. Kept beside the event listener on purpose: they share one job, so
     * [withoutInboundApply] pauses all three at once.
     */
    private val foodCatalogue: FoodCatalogueGateway? = null,
    /** Where a catalogue snapshot is folded in, and where a first-attach foods seed is queued from. */
    private val foods: FoodRepository? = null,
) {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** One line of feedback for the Sync card: what the last drain did, or why sign-in failed. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Drives "N waiting to sync" and lets the user see the drain actually drained. */
    val pendingWrites: Flow<Int> = outboxDao.observeCount()

    private val drainLock = Mutex()
    private var listenerJob: Job? = null

    private fun initialState(): SyncState = when {
        !authenticator.isConfigured || gateway == null -> SyncState.NotConfigured
        authenticator.currentAccount != null -> SyncState.SignedIn(authenticator.currentAccount?.email)
        else -> SyncState.SignedOut
    }

    /**
     * Called once, at app start.
     *
     * A previously signed-in parent gets the drain and the listener back without touching Settings:
     * a phone that was already sharing the log must not stop sharing because the app was restarted.
     */
    fun start() {
        if (_state.value is SyncState.SignedIn) {
            attach()
        }
    }

    suspend fun signIn(email: String, password: String) {
        val result = authenticator.signInWithEmail(email, password)
        applySignIn(result)
    }

    suspend fun signInWithGoogle(idToken: String) {
        val result = authenticator.signInWithGoogle(idToken)
        applySignIn(result)
    }

    /**
     * A sign-in that already succeeded must not be thrown away because the screen that started it went
     * away.
     *
     * This is the Phase-13 finding the phone reported as "the seed sits in the outbox": the account is
     * authenticated on the server, but the attach that seeds, drains and listens happens after a
     * suspension point, so a `viewModelScope` cancelled in that window (the parent leaving Settings)
     * loses the listener *and* the drain, and the two seeded rows wait for the next write to notice.
     */
    private suspend fun applySignIn(result: SignInResult) = withContext(NonCancellable) { handle(result) }

    fun signOut() {
        listenerJob?.cancel()
        listenerJob = null
        authenticator.signOut()
        _state.value = if (authenticator.isConfigured) SyncState.SignedOut else SyncState.NotConfigured
        _message.value = "Signed out. Logging still works offline; this phone keeps its own copy."
    }

    /**
     * Push whatever is queued, now.
     *
     * Safe to call concurrently: one drain at a time, so two triggers cannot interleave writes from the
     * same queue.
     */
    suspend fun syncNow(): DrainResult? {
        val drainer = drainer ?: return null
        val result = drainLock.withLock { drainer.drain() }
        _message.value = messageFor(result)
        return result
    }

    /**
     * The one line of feedback the Sync card shows.
     *
     * The three failure shapes are deliberately distinct. A silent `Sync now` was the Phase-13 finding:
     * the button looked inert because the drain had suspended, and the message line kept whatever the
     * previous drain said. Now a drain either reports what it sent, or names the queue it could not
     * move — and a timeout says *which* timeout, because "waiting for a network connection" over four
     * bars would be a lie.
     */
    private fun messageFor(result: DrainResult): String = when {
        result.error == null && result.written + result.tombstoned == 0 -> "Up to date."
        result.error == null -> "Sent ${result.written + result.tombstoned} change(s)."
        result.timedOut && !network.isOnline() ->
            "Waiting for a network connection — ${queued(result.remaining)} queued."
        result.timedOut ->
            "The server did not answer — ${queued(result.remaining)} queued."
        else -> result.error ?: "Could not reach the server."
    }

    /** "1 change" / "3 changes", so the card never reads `1 change(s)`. */
    private fun queued(count: Int): String = "$count change${if (count == 1) "" else "s"}"

    /** The after-a-write hook. Fire-and-forget: the intent is already safe in the outbox. */
    fun requestDrain() {
        if (_state.value !is SyncState.SignedIn) return
        scope.launch { syncNow() }
    }

    private suspend fun handle(result: SignInResult) {
        when (result) {
            is SignInResult.SignedIn -> {
                _state.value = SyncState.SignedIn(result.account.email)
                _message.value = "Signed in as ${result.account.email ?: result.account.uid}."
                attach()
            }

            is SignInResult.Refused -> {
                _state.value = SyncState.Refused(result.account.email)
                _message.value = "That account (${result.account.email ?: result.account.uid}) is not one " +
                    "of the two this log belongs to, so sync stays off. Sign in with one of the two."
            }

            is SignInResult.Failed -> {
                _message.value = result.message
            }
        }
    }

    private fun attach() {
        val gateway = gateway ?: return
        listenerJob?.cancel()
        listenerJob = scope.launch {
            try {
                seedIfNeeded()
                // The foods this phone typed before the catalogue was published go out once, so the other
                // phone can finally read their names (ADR-0002).
                foods?.seedPendingFoodsOnce()
                syncNow()
                // The catalogue and the pending additions are observed in the SAME job as the log, so a
                // reset pauses all of them: a snapshot arriving mid-wipe would otherwise put foods back
                // into an emptied table, which is the exact bug Phase 15's device pass found for events.
                coroutineScope {
                    launch { observeEvents(gateway) }
                    foodCatalogue?.let { catalogue ->
                        launch { observeFoodCatalogue(catalogue) }
                        launch { observePendingFoods(catalogue) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _message.value = "Sync could not start: ${failure.message ?: "unknown error"}"
            }
        }
    }

    private suspend fun observeEvents(gateway: SyncGateway) {
        var first = true
        gateway.observe()
            .catch { failure -> _message.value = "Listening stopped: ${failure.message ?: "unknown error"}" }
            .collect { snapshot ->
                applyRemote(snapshot)
                // One drain once the first snapshot has landed. The drain above can run before the client is
                // ready, and a snapshot that lost to a newer local row queues new intent — so "the queue
                // emptied on its own" has to be true AFTER the listener, not only before it.
                if (first) {
                    first = false
                    syncNow()
                }
            }
    }

    private suspend fun observeFoodCatalogue(catalogue: FoodCatalogueGateway) {
        catalogue.observeCatalogue()
            .catch { failure ->
                _message.value = "The food catalogue stopped updating: ${failure.message ?: "unknown error"}"
            }
            .collect { published -> foods?.applyPublished(published) }
    }

    private suspend fun observePendingFoods(catalogue: FoodCatalogueGateway) {
        catalogue.observePending()
            .catch { failure ->
                _message.value = "The food list stopped updating: ${failure.message ?: "unknown error"}"
            }
            .collect { pending -> foods?.applyPending(pending) }
    }

    /**
     * May a reset run now?
     *
     * A reset queues a tombstone for every row and then deletes the rows, so if the queue cannot travel,
     * the server keeps the log and the next snapshot merges it straight back in — a reset that undoes
     * itself. Hence the probe: drain first, and refuse if that drain could not reach the server.
     *
     * Signed out is NOT a refusal. There is no queue to move yet, the tombstones stay in the outbox, and
     * they drain at the next sign-in — which is what stops a reset while signed out from resurrecting the
     * log. A build with no sync at all (`drainer == null`) is allowed for the same reason: nothing else
     * holds a copy.
     */
    suspend fun resetGate(): ResetGate {
        if (drainer == null || _state.value !is SyncState.SignedIn) return ResetGate.Allowed
        val probe = syncNow() ?: return ResetGate.Allowed
        if (!probe.ok) return ResetGate.Refused(messageFor(probe))
        // A successful drain proves the server answered — but only if there was something to send. An EMPTY
        // queue drains "successfully" with no network call at all, so a reset on a plane would queue
        // deletions that cannot travel while the server keeps the log. That is the case this check exists
        // for, and why the probe alone is not enough.
        return if (network.isOnline()) {
            ResetGate.Allowed
        } else {
            ResetGate.Refused("Waiting for a network connection.")
        }
    }

    /**
     * Make the server match a keep set: tombstone every remote row the caller does NOT list.
     *
     * This is the one primitive behind both a reset and an import, because they are the same problem
     * asked with different sets — after a reset the keep set is empty, after an import it is the file's
     * ids. Emptying a phone and leaving the server full is what made an import appear to merge: the
     * deleted rows were never deleted remotely, so the next snapshot handed them back.
     *
     * No new gateway method: the outbox already travels tombstones by id, and a bulk collection delete
     * would not do — the other phone's listener does not read "document absent" as a deletion.
     *
     * Returns how many intents were queued. Throws if the server cannot be read: neither caller may go on
     * to rewrite the local copy on a guess about what the server holds. Ids already queued are skipped, so
     * a reset that reconciles and then deletes locally queues each row once.
     */
    suspend fun reconcileRemote(keepChildIds: Set<String>, keepEventIds: Set<String>): Int {
        val gateway = gateway ?: return 0
        if (_state.value !is SyncState.SignedIn) return 0
        val remote = gateway.fetchAll()
        val at = clock.millis()
        val alreadyQueued = outboxDao.pending().map { it.entityId }.toSet()
        var queued = 0
        remote.children.map { it.id }
            .filterNot { it in keepChildIds || it in alreadyQueued }
            .forEach { id ->
                outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_CHILD, id, deleted = true, queuedAt = at))
                queued++
            }
        remote.events.map { it.id }
            .filterNot { it in keepEventIds || it in alreadyQueued }
            .forEach { id ->
                outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, id, deleted = true, queuedAt = at))
                queued++
            }
        return queued
    }

    /**
     * Drain until the queue is empty, or until [passes] attempts have been made.
     *
     * One pass is not enough after a wipe: a batch of ~160 tombstones will not cross a cold connection in
     * one go, and reporting the rest as "still queued" while also claiming the server was clear is how the
     * owner was told two contradictory things. The bound is what stops a genuinely offline phone from
     * spinning; whatever is left is reported as left.
     */
    suspend fun drainUntilIdle(passes: Int = 3): DrainResult? {
        var last: DrainResult? = null
        repeat(passes) {
            last = syncNow() ?: return null
            if (last!!.remaining == 0) return last
        }
        return last
    }

    /**
     * Run a replace — a reset or an import — with the inbound listener detached, then put it back.
     *
     * The phone found this the hard way in Phase 15's device pass: a local reset wiped the tables and
     * drained its tombstones, and the listener then applied a snapshot it had taken *before* the deletes
     * landed — re-inserting 15 events into the emptied database with an empty outbox, so the phone showed
     * a log the server no longer had and would never send again. An import had the same hole, which is why
     * the seeded twins came back beside the imported children.
     *
     * Cancelling and JOINING the listener is what closes that window: no `applyRemote` can be in flight
     * while the replace runs, and the re-attach below starts from a snapshot taken after it.
     */
    suspend fun <T> withoutInboundApply(block: suspend () -> T): T {
        val job = listenerJob
        listenerJob = null
        job?.cancelAndJoin()
        return try {
            block()
        } finally {
            // Re-attach only when there is a server to listen to. `attach()` drains before it observes,
            // so any tombstone the block left queued travels before a snapshot can be applied.
            if (_state.value is SyncState.SignedIn) attach()
        }
    }

    /**
     * Re-upload everything after a backup restore.
     *
     * `replaceAll` wipes the tables *and* the queue, so without this the restored log would sit on this
     * phone while Firestore still held the pre-restore version — and the next snapshot would merge the
     * old rows back in. The seed flag is put back, the queue is rebuilt from the restored rows, and the
     * drain runs once.
     *
     * Known limitation, recorded in PLAN.md: server documents for rows the restore does not contain are
     * not deleted. The database the file came from is the one that wins, not "absence means delete".
     */
    suspend fun reseedAfterRestore() {
        settings.resetSyncSeed()
        seedIfNeeded()
        // The restored file is the truth: every food it carries has to reach `foods_pending` again, or the
        // other phone keeps the pre-restore list beside a log it no longer matches.
        foods?.queueAllPendingFoods()
        syncNow()
    }

    /**
     * Push the log that already exists on this phone, once.
     *
     * Guarded by a one-way flag rather than "is the queue empty", for the same reason the children seed
     * is: an emptied queue is exactly what a finished drain looks like, and re-seeding on every launch
     * would rewrite the whole log to the server each time.
     *
     * Tombstones go as deletes. A locally deleted row that never reaches Firestore is a row the other
     * phone still shows — and one this phone would then re-receive as live.
     */
    internal suspend fun seedIfNeeded() {
        if (settings.syncSeedDone.first()) return
        val at = clock.millis()
        val alreadyQueued = outboxDao.pending().map { it.entityId }.toSet()

        childDao.allIncludingDeleted()
            .filterNot { it.id in alreadyQueued }
            .forEach {
                outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_CHILD, it.id, it.deleted, at))
            }
        eventDao.allIncludingDeleted()
            .filterNot { it.id in alreadyQueued }
            .forEach {
                outboxDao.enqueue(outboxIntent(OutboxDrainer.ENTITY_EVENT, it.id, it.deleted, at))
            }

        settings.markSyncSeedDone()
    }

    /**
     * Apply one server snapshot to the local database.
     *
     * A tombstone for an id this phone has never seen is deliberately **not** an instruction to create
     * a deleted row: there is nothing to delete, and inventing a placeholder child from a document that
     * carries no name would be worse than ignoring it.
     */
    internal suspend fun applyRemote(remote: RemoteDataset) {
        remote.children.forEach { row -> applyChild(row) }
        remote.events.forEach { row -> applyEvent(row) }
    }

    private suspend fun applyChild(row: RemoteRow) {
        val local = childDao.byId(row.id)
        val verdict = resolveSync(local?.let { SyncRow(it.updatedAt, it.deleted) }, row.toSyncRow())
        when (verdict) {
            SyncVerdict.TAKE_REMOTE -> if (row.deleted) {
                if (local != null && !local.deleted) childDao.softDelete(row.id, row.updatedAt)
            } else {
                childFromSyncMap(row.payload)?.let { childDao.upsert(it) }
            }

            SyncVerdict.KEEP_LOCAL -> enqueueIfAbsent(OutboxDrainer.ENTITY_CHILD, row.id, local?.deleted == true)

            SyncVerdict.IDENTICAL -> Unit
        }
    }

    private suspend fun applyEvent(row: RemoteRow) {
        val local = eventDao.byId(row.id)
        val verdict = resolveSync(local?.let { SyncRow(it.updatedAt, it.deleted) }, row.toSyncRow())
        when (verdict) {
            SyncVerdict.TAKE_REMOTE -> if (row.deleted) {
                if (local != null && !local.deleted) eventDao.softDelete(row.id, row.updatedAt)
            } else {
                eventFromSyncMap(row.payload)?.let { eventDao.upsert(it) }
            }

            SyncVerdict.KEEP_LOCAL -> enqueueIfAbsent(OutboxDrainer.ENTITY_EVENT, row.id, local?.deleted == true)

            SyncVerdict.IDENTICAL -> Unit
        }
    }

    /**
     * Queue a local row that beat the server's copy — unless an intent is already waiting for it.
     *
     * Without that check, a snapshot arriving while a drain is in flight would add a second intent for
     * the same row, and the queue would grow by one per snapshot.
     */
    private suspend fun enqueueIfAbsent(entityType: String, entityId: String, deleted: Boolean) {
        if (outboxDao.countFor(entityId) > 0) return
        outboxDao.enqueue(outboxIntent(entityType, entityId, deleted, clock.millis()))
    }

    private fun RemoteRow.toSyncRow() = SyncRow(updatedAt = updatedAt, deleted = deleted)
}
