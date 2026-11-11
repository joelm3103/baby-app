package app.babylog.ui.common

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.babylog.data.sync.ManualSyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a pull's answer feels like, proved without a phone.
 *
 * The device pass can establish that a buzz happens; only this can establish that a refusal and a success are
 * DIFFERENT buzzes, which is the whole point of adding a fifth verb. `Haptics` takes its `HapticFeedback` through
 * the constructor, so a recorder stands in for the platform and the mapping is a fact rather than a hope.
 */
class SyncHapticsTest {

    private class Recorder : HapticFeedback {
        val felt = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            felt += hapticFeedbackType
        }
    }

    private fun report(outcome: ManualSyncOutcome): HapticFeedbackType {
        val recorder = Recorder()
        Haptics(recorder).report(outcome)
        // Exactly one buzz per answer: two would read as a stutter and three as a pattern.
        assertEquals("one answer, one buzz", 1, recorder.felt.size)
        return recorder.felt.single()
    }

    @Test
    fun `a completed sync confirms`() {
        assertEquals(HapticFeedbackType.Confirm, report(ManualSyncOutcome.SYNCED))
    }

    @Test
    fun `a sync inside the cooldown is refused`() {
        assertEquals(HapticFeedbackType.Reject, report(ManualSyncOutcome.COOLDOWN))
    }

    @Test
    fun `an overlapping sync is refused`() {
        assertEquals(HapticFeedbackType.Reject, report(ManualSyncOutcome.ALREADY_RUNNING))
    }

    @Test
    fun `a pull while signed out is refused`() {
        assertEquals(HapticFeedbackType.Reject, report(ManualSyncOutcome.NOT_SIGNED_IN))
    }

    @Test
    fun `the refusal is not the confirmation, and not the toggle either`() {
        // The reason Phase 30 added a verb: a refusal that felt like a save, or like a switch flipping, would
        // carry no information at all.
        assertEquals(
            setOf(HapticFeedbackType.Confirm, HapticFeedbackType.Reject),
            ManualSyncOutcome.entries.map { report(it) }.toSet(),
        )
    }
}
