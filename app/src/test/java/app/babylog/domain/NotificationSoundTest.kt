package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one thing about notification sound this app decides.
 *
 * Everything here is a mapping from what the notification manager reports to four words, and the words are the
 * only text on the Settings row — which is the same gap the Phase 19 phone pass found in `2 childs`: a green build
 * checks the type, not the sentence. So the label of every state is pinned here, not just the mapping.
 */
class NotificationSoundTest {

    private val default = "content://settings/system/notification_sound"

    @Test
    fun noChannelReadsAsManagedByAndroid() {
        // Below API 26, or a manager that refused the read. The words admit the app cannot see the value rather
        // than claiming a value it does not know — and they are true either way, because Android does own it.
        assertEquals(
            NotificationSoundState.UNKNOWN,
            notificationSoundState(channelExists = false, soundUri = default, defaultUri = default),
        )
        assertEquals("Managed by Android", NotificationSoundState.UNKNOWN.label)
    }

    @Test
    fun aChannelWithNoSoundIsSilent() {
        // `sound == null` is how the platform says silenced, and it is not the same answer as "no channel": one is
        // the parent's choice, the other is the app having nothing to report.
        assertEquals(
            NotificationSoundState.SILENT,
            notificationSoundState(channelExists = true, soundUri = null, defaultUri = default),
        )
        assertEquals("Silent", NotificationSoundState.SILENT.label)
    }

    @Test
    fun theSystemSoundIsTheDefault() {
        assertEquals(
            NotificationSoundState.DEFAULT,
            notificationSoundState(channelExists = true, soundUri = default, defaultUri = default),
        )
        assertEquals("Android's default", NotificationSoundState.DEFAULT.label)
    }

    @Test
    fun anyOtherSoundIsCustom() {
        // A bundled OEM tone, a picked file — from the app's side these are one state, because the app did not choose
        // any of them and cannot name them.
        assertEquals(
            NotificationSoundState.CUSTOM,
            notificationSoundState(
                channelExists = true,
                soundUri = "content://media/internal/audio/media/42",
                defaultUri = default,
            ),
        )
        assertEquals("Custom", NotificationSoundState.CUSTOM.label)
    }

    @Test
    fun aDefaultThatCannotBeReadStillSeparatesSilentFromChosen() {
        // The default URI is a platform constant, but a null one must not turn a chosen sound into the default:
        // `soundUri != defaultUri` is what decides, and one null never equals a real URI.
        assertEquals(
            NotificationSoundState.CUSTOM,
            notificationSoundState(channelExists = true, soundUri = default, defaultUri = null),
        )
    }
}
