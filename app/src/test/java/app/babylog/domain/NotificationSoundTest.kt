package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one thing about notification sound this app decides.
 *
 * Everything here is a mapping from what the notification manager reports to five words, and the words are the
 * only text on the Settings row — which is the same gap the Phase 19 phone pass found in `2 childs`: a green build
 * checks the type, not the sentence. So the label of every state is pinned here, not just the mapping.
 *
 * The importance cases are not hypothetical: Phase 32's device pass set the channel to `Silent` on a Samsung phone
 * and the row went on reading `Custom`, because Android kept the picked tone in the channel's sound field and
 * dropped the importance instead.
 */
class NotificationSoundTest {

    private val default = "content://settings/system/notification_sound"

    /** `IMPORTANCE_DEFAULT`: a fresh channel, and the only importance that makes a sound. */
    private val audible = 3

    @Test
    fun noChannelReadsAsManagedByAndroid() {
        // Below API 26, or a manager that refused the read. The words admit the app cannot see the value rather
        // than claiming a value it does not know — and they are true either way, because Android does own it.
        assertEquals(
            NotificationSoundState.UNKNOWN,
            notificationSoundState(
                channelExists = false,
                importance = audible,
                soundUri = default,
                defaultUri = default,
            ),
        )
        assertEquals("Managed by Android", NotificationSoundState.UNKNOWN.label)
    }

    @Test
    fun aChannelWithNoSoundIsSilent() {
        // `sound == null` is how the platform says silenced, and it is not the same answer as "no channel": one is
        // the parent's choice, the other is the app having nothing to report.
        assertEquals(
            NotificationSoundState.SILENT,
            notificationSoundState(
                channelExists = true,
                importance = audible,
                soundUri = null,
                defaultUri = default,
            ),
        )
        assertEquals("Silent", NotificationSoundState.SILENT.label)
    }

    @Test
    fun aLowImportanceIsSilentEvenWithAToneStillSet() {
        // The defect the device pass found. Samsung's `Silent` radio leaves the channel's sound pointing at the tone
        // that was chosen and moves its importance to LOW, where Android plays nothing. Reading the URI alone said
        // `Custom` for a channel that makes no sound; the importance is the fact, the URI is what used to be true.
        assertEquals(
            NotificationSoundState.SILENT,
            notificationSoundState(
                channelExists = true,
                importance = 2,
                soundUri = "content://media/external/audio/media/192008?title=Heavy%20Bell&canonical=1",
                defaultUri = default,
            ),
        )
        // IMPORTANCE_MIN is quieter still and equally silent.
        assertEquals(
            NotificationSoundState.SILENT,
            notificationSoundState(
                channelExists = true,
                importance = 1,
                soundUri = default,
                defaultUri = default,
            ),
        )
    }

    @Test
    fun aBlockedChannelIsNotSilence() {
        // Notifications off for the channel: no reminder arrives at all. Saying `Silent` would promise a quiet
        // arrival that will never happen, so this is the one state that has to be louder than the sound question.
        assertEquals(
            NotificationSoundState.BLOCKED,
            notificationSoundState(
                channelExists = true,
                importance = 0,
                soundUri = default,
                defaultUri = default,
            ),
        )
        assertEquals("Blocked", NotificationSoundState.BLOCKED.label)
    }

    @Test
    fun theSystemSoundIsTheDefault() {
        assertEquals(
            NotificationSoundState.DEFAULT,
            notificationSoundState(
                channelExists = true,
                importance = audible,
                soundUri = default,
                defaultUri = default,
            ),
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
                importance = audible,
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
            notificationSoundState(
                channelExists = true,
                importance = audible,
                soundUri = default,
                defaultUri = null,
            ),
        )
    }

    @Test
    fun everyLabelIsDistinctAndSentenceCase() {
        // The menu summary and the row both print these words with no other context, so two states sharing a label
        // would be a row that cannot say which situation the parent is in.
        val labels = NotificationSoundState.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(
            listOf("Silent", "Android's default", "Custom", "Blocked", "Managed by Android"),
            labels,
        )
    }
}
