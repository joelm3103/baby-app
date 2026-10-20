package app.babylog.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

/** The signed-in account, reduced to what the UI shows and the allow-list checks. */
data class Account(val uid: String, val email: String?)

sealed interface SignInResult {
    data class SignedIn(val account: Account) : SignInResult

    /**
     * Authenticated, but not one of the two accounts this log belongs to.
     *
     * A separate case rather than a message, because the app must do the honest thing: sign straight
     * back out. Holding a token for an account that may read nothing is how a parent ends up looking at
     * an empty log and concluding the app lost their data.
     */
    data class Refused(val account: Account) : SignInResult

    data class Failed(val message: String) : SignInResult
}

/**
 * The sign-in seam, for the same reason [SyncGateway] exists: Firebase cannot run in a JVM test, so the
 * logic that decides "signed in / refused / failed" is written against this and driven by a fake.
 */
interface AccountAuthenticator {
    val isConfigured: Boolean

    val currentAccount: Account?

    fun signOut()

    suspend fun signInWithEmail(email: String, password: String): SignInResult

    suspend fun signInWithGoogle(idToken: String): SignInResult
}

/** The behaviour of every un-configured build: a single-phone logger that says so. */
object UnconfiguredAuthenticator : AccountAuthenticator {
    override val isConfigured = false
    override val currentAccount: Account? = null
    override fun signOut() = Unit

    override suspend fun signInWithEmail(email: String, password: String): SignInResult =
        SignInResult.Failed(NOT_CONFIGURED)

    override suspend fun signInWithGoogle(idToken: String): SignInResult =
        SignInResult.Failed(NOT_CONFIGURED)

    const val NOT_CONFIGURED =
        "This build has no sync configured. Logging works normally; the log just stays on this phone."
}

/**
 * Firebase Auth, with the two-uid allow-list.
 *
 * Email/password is the primary route because it does not care what key the APK was signed with — which
 * matters, because the release keystore does not exist until Phase 14 and Google Sign-In will refuse a
 * build whose signing certificate it does not know.
 */
class FirebaseAuthenticator(
    private val auth: FirebaseAuth,
    private val allowedUids: Set<String>,
) : AccountAuthenticator {

    override val isConfigured = true

    override val currentAccount: Account?
        get() = auth.currentUser?.let { it.toAccount().takeIf { account -> isAllowed(account) } }

    override fun signOut() = auth.signOut()

    override suspend fun signInWithEmail(email: String, password: String): SignInResult {
        if (email.isBlank() || password.isEmpty()) {
            return SignInResult.Failed("Enter the account's email and password.")
        }
        return try {
            val user = auth.signInWithEmailAndPassword(email.trim(), password).awaitTask().user
            user?.toAccount()?.let(::gate) ?: SignInResult.Failed("Signed in, but Firebase returned no account.")
        } catch (failure: Exception) {
            SignInResult.Failed(describe(failure))
        }
    }

    override suspend fun signInWithGoogle(idToken: String): SignInResult {
        if (idToken.isBlank()) return SignInResult.Failed("Google did not return a token. Try again.")
        return try {
            val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .awaitTask()
                .user
            user?.toAccount()?.let(::gate) ?: SignInResult.Failed("Signed in, but Firebase returned no account.")
        } catch (failure: Exception) {
            SignInResult.Failed(describe(failure))
        }
    }

    /**
     * The allow-list, when the build was given one.
     *
     * Empty means "not configured here" and the Firestore rules are left as the only gate — which is the
     * real one either way. This check exists only so a third Google account gets a sentence instead of a
     * permission error on a screen that would otherwise look empty.
     */
    private fun isAllowed(account: Account): Boolean =
        allowedUids.isEmpty() || account.uid in allowedUids

    private fun gate(account: Account): SignInResult =
        if (isAllowed(account)) {
            SignInResult.SignedIn(account)
        } else {
            auth.signOut()
            SignInResult.Refused(account)
        }

    private fun FirebaseUser.toAccount() = Account(uid = uid, email = email)

    private fun describe(failure: Exception): String {
        val code = (failure as? FirebaseAuthException)?.errorCode
        return when (code) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD" -> "Wrong email or password."
            "ERROR_INVALID_EMAIL" -> "That does not look like an email address."
            "ERROR_USER_NOT_FOUND" -> "No account with that email."
            "ERROR_USER_DISABLED" -> "That account has been disabled."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a minute and try again."
            "ERROR_NETWORK_REQUEST_FAILED" -> "No connection. The app still logs offline; try again later."
            else -> failure.message?.takeIf { it.isNotBlank() } ?: "Sign-in failed."
        }
    }
}
