package app.babylog.domain

/**
 * What a parent will find the reminder notification's sound set to, in the words Settings shows.
 *
 * Five states, and the ones that matter are the ones a build cannot see: a channel on the system default, a
 * channel that has been silenced, and a channel whose sound URI is still set while nothing plays look identical
 * from inside the app unless the channel's own fields are read back. That read needs a `Context` and a
 * `NotificationManager`, so it happens in `data/reminders`; **this file is the comparison only**, which is why it
 * takes strings and an int rather than a `Uri` and a `NotificationChannel`, and is JVM-testable with no device.
 */
enum class NotificationSoundState(val label: String) {
    /**
     * The channel makes no sound, whether because it has none or because its importance is low enough that
     * Android never plays one.
     *
     * The second half is not a guess: on the owner's phone, choosing `Silent` on Android's channel page left the
     * channel's sound pointing at the tone that had been picked and dropped its importance instead. Reading only
     * the sound reported a chosen tone for a channel that makes no sound at all — found in Phase 32's device pass.
     */
    SILENT("Silent"),

    /** The channel's sound is the system default, which is what a fresh install gets. */
    DEFAULT("Android's default"),

    /** The channel's sound is a URI of the parent's choosing, picked on Android's own channel page. */
    CUSTOM("Custom"),

    /**
     * Notifications are off for the channel, so no reminder will arrive at all — a different fact from silence,
     * and the one a parent must not have to infer from a row that reads like a tone.
     */
    BLOCKED("Blocked"),

    /**
     * Below API 26, or the channel could not be read at all.
     *
     * The words state the fact rather than admitting ignorance: on any phone this app runs on, the sound IS
     * managed by Android — this state only means the app cannot say which one it is.
     */
    UNKNOWN("Managed by Android"),
}

/** `NotificationManager.IMPORTANCE_NONE`, restated so this file needs no Android import. */
private const val IMPORTANCE_NONE = 0

/** `NotificationManager.IMPORTANCE_LOW`, below which no sound is played; `IMPORTANCE_MIN` is 1. */
private const val IMPORTANCE_LOW = 2

/**
 * Which of the five states a channel is in.
 *
 * [soundUri] is `NotificationChannel.sound` as a string, or null for a channel with no sound; [defaultUri] is
 * `Settings.System.DEFAULT_NOTIFICATION_URI` as a string; [importance] is `NotificationChannel.importance`.
 * Both URIs are nullable and both null is a legitimate pair, which is why [channelExists] is a separate argument
 * rather than being inferred: "this channel has no sound" and "this app has no channel" are different sentences,
 * and only the caller knows which it is holding.
 *
 * The order is the answer, and the first three tests are Android's precedence rather than this app's preference:
 * a blocked channel notifies nobody whatever its sound says, and a channel at or below [IMPORTANCE_LOW] plays
 * nothing however its sound field reads — which is exactly the case that made the row lie before the device pass.
 *
 * Deliberately NOT a `when` over `Uri` equality with a fallback to CUSTOM on anything unexpected: a sound that is
 * not the default and not absent, on a channel that will actually make a noise, is by definition one somebody chose.
 */
fun notificationSoundState(
    channelExists: Boolean,
    importance: Int,
    soundUri: String?,
    defaultUri: String?,
): NotificationSoundState = when {
    !channelExists -> NotificationSoundState.UNKNOWN
    importance == IMPORTANCE_NONE -> NotificationSoundState.BLOCKED
    importance <= IMPORTANCE_LOW -> NotificationSoundState.SILENT
    soundUri == null -> NotificationSoundState.SILENT
    soundUri == defaultUri -> NotificationSoundState.DEFAULT
    else -> NotificationSoundState.CUSTOM
}
