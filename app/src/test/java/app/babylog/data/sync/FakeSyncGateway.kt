package app.babylog.data.sync

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The whole server, in a list.
 *
 * Everything about sync that can be decided without a network is decided here: how many writes a drain
 * made, what the latest version of a row looked like when it left, and what happens when a write fails.
 * Firestore itself is only proved on the two phones.
 */
class FakeSyncGateway : SyncGateway {

    val childWrites = mutableListOf<ChildEntity>()
    val eventWrites = mutableListOf<EventEntity>()
    val childTombstones = mutableListOf<Pair<String, Long>>()
    val eventTombstones = mutableListOf<Pair<String, Long>>()

    /** How many writes succeed before the next one throws. [Int.MAX_VALUE] means never fail. */
    var failAfter: Int = Int.MAX_VALUE
    var failure: Exception = IllegalStateException("The server is unreachable.")

    /**
     * Fail exactly one write, then succeed.
     *
     * This is how a test makes the pre-listener drain fail and still expects the queue to empty: the
     * retry that matters is the one after the first inbound snapshot.
     */
    var failOnce: Boolean = false

    /**
     * Accept the call and never answer — what Firestore actually does with no network.
     *
     * Distinct from [failAfter]: a failure throws, a hang suspends forever, and the difference is the
     * whole Phase-14 finding (the drain never returned, so `drainLock` stayed held).
     */
    var hangForever: Boolean = false

    /** What `fetchAll` (and therefore the first-sign-in seed) reports. */
    var inbound: RemoteDataset = RemoteDataset()

    /**
     * The live stream, when a test needs more than one snapshot.
     *
     * Default null, so `observe()` stays a single `flowOf(inbound)` and the existing tests read as they
     * always did. A reset test needs to push a snapshot *while* the wipe is running, which one emission
     * cannot express.
     */
    var observeFlow: kotlinx.coroutines.flow.MutableStateFlow<RemoteDataset>? = null

    private var successes = 0

    val writeCount: Int
        get() = childWrites.size + eventWrites.size + childTombstones.size + eventTombstones.size

    override suspend fun writeChild(child: ChildEntity) {
        record()
        childWrites += child
    }

    override suspend fun writeEvent(event: EventEntity) {
        record()
        eventWrites += event
    }

    override suspend fun deleteChild(id: String, updatedAt: Long) {
        record()
        childTombstones += id to updatedAt
    }

    override suspend fun deleteEvent(id: String, updatedAt: Long) {
        record()
        eventTombstones += id to updatedAt
    }

    override suspend fun fetchAll(): RemoteDataset = inbound

    override fun observe(): Flow<RemoteDataset> = observeFlow ?: flowOf(inbound)

    private suspend fun record() {
        if (hangForever) awaitCancellation()
        if (failOnce) {
            failOnce = false
            throw failure
        }
        if (successes >= failAfter) throw failure
        successes++
    }
}

/** Sign-in, without Firebase. */
class FakeAuthenticator(
    override var isConfigured: Boolean = true,
    private var account: Account? = null,
    /** When set, the next sign-in returns this instead of succeeding. */
    var nextResult: SignInResult? = null,
) : AccountAuthenticator {

    var signedOut = false
        private set

    override val currentAccount: Account? get() = account

    override fun signOut() {
        account = null
        signedOut = true
    }

    override suspend fun signInWithEmail(email: String, password: String): SignInResult =
        (nextResult ?: SignInResult.SignedIn(Account("uid-one", email))).also { result ->
            if (result is SignInResult.SignedIn) account = result.account
        }

    override suspend fun signInWithGoogle(idToken: String): SignInResult = signInWithEmail("google@example.com", idToken)
}
