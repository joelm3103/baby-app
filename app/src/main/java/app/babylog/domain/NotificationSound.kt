package app.babylog.domain

/**
 * What a parent will find the reminder notification's sound set to, in the words Settings shows.
 *
 * Four states, and the two that matter are the ones a build cannot see: a channel whose sound is the system
 * default and a channel that has been silent since the parent switched it there look identical from inside the
 * app unless the URI is read back and compared. That read needs a `Context` and a `NotificationManager`, so it
 * happens in `data/reminders`; **this file is the comparison only**, which is why it takes strings rather than
 * a `Uri` and is JVM-testable with no device.
 */
enum class NotificationSoundState(val label: String) {
    /** The channel exists and its sound is nothing: the parent silenced reminders. */
    SILENT("Silent"),

    /** The channel's sound is the system default, which is what a fresh install gets. */
    DEFAULT("Android's default"),

    /** The channel's sound is a URI of the parent's choosing, picked on Android's own channel page. */
    CUSTOM("Custom"),

    /**
     * Below API 26, or the channel could not be read at all.
     *
     * The words state the fact rather than admitting ignorance: on any phone this app runs on, the sound IS
     * managed by Android — this state only means the app cannot say which one it is.
     */
    UNKNOWN("Managed by Android"),
}

/**
 * Which of the four states a channel's sound is in.
 *
 * [soundUri] is `NotificationChannel.sound` as a string, or null for a silenced channel; [defaultUri] is
 * `Settings.System.DEFAULT_NOTIFICATION_URI` as a string. Both are nullable and BOTH null is the same answer as
 * `soundUri == null`, which is why [channelExists] is a separate argument rather than being inferred: "this
 * channel has no sound" and "this app has no channel" are different sentences, and only the caller knows which
 * it is holding.
 *
 * Deliberately NOT a `when` over `Uri` equality with a fallback to CUSTOM on anything unexpected: a sound that
 * is not the default and not absent is, by definition, one somebody chose.
 */
fun notificationSoundState(
    channelExists: Boolean,
    soundUri: String?,
    defaultUri: String?,
): NotificationSoundState = when {
    !channelExists -> NotificationSoundState.UNKNOWN
    soundUri == null -> NotificationSoundState.SILENT
    soundUri == defaultUri -> NotificationSoundState.DEFAULT
    else -> NotificationSoundState.CUSTOM
}
