package app.babylog.data.sync

/**
 * Whether this phone currently has a network at all.
 *
 * It exists for exactly one sentence. A drain that gave up can mean two very different things — the
 * phone is offline, or the phone is online and the server never answered — and the Sync card must not
 * claim the first when the second is true. Guessing "waiting for a network connection" on a phone with
 * four bars is the same class of lie as a reminder chip that promises an alarm the scheduler will not
 * arm.
 *
 * A one-method interface rather than an `android.net` call inside [SyncRepository], so the JVM tests can
 * be offline on purpose and online on purpose without a Robolectric shadow.
 */
fun interface NetworkStatus {
    fun isOnline(): Boolean

    companion object {
        /** For the tests and for a build with no sync: never claims to know better than silence. */
        val AlwaysOnline = NetworkStatus { true }
    }
}
