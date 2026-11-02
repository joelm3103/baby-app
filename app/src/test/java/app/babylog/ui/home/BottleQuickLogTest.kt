package app.babylog.ui.home

import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What one tap on a bottle preset writes.
 *
 * The card's quick pills borrow the Diaper path, and the whole of that path's decision is here: which activity,
 * which amount, and what time. It is a separate function from the ViewModel because `HomeViewModel.state` is
 * Room-backed and Robolectric cannot collect a Room-backed Flow, so a test that went through the ViewModel would
 * hang instead of asserting anything.
 */
class BottleQuickLogTest {

    private val at = 1_700_000_000_000L

    @Test
    fun aPresetWritesAFeedAtThatAmountAndThatMoment() {
        val draft = quickBottleDraft(120, at)
        assertNotNull(draft)
        assertEquals(EventType.BOTTLE, draft!!.type)
        assertEquals(120, draft.amountMl)
        assertEquals(at, draft.startedAt)
        // Nothing else is invented: a one-tap feed has no brand, no meal and no note.
        assertNull(draft.formulaBrand)
        assertNull(draft.note)
        assertNull(draft.mealSlot)
    }

    @Test
    fun theAmountsAtTheEdgesOfTheRangeAreAccepted() {
        assertEquals(1, quickBottleDraft(1, at)!!.amountMl)
        assertEquals(
            SettingsRepository.MAX_BOTTLE_PRESET_ML,
            quickBottleDraft(SettingsRepository.MAX_BOTTLE_PRESET_ML, at)!!.amountMl,
        )
    }

    @Test
    fun anAmountThatIsNotABottleIsRefusedRatherThanWritten() {
        // A nought-millilitre feed is a mis-tap, and a four-litre one is a typo. Both are refused at the write
        // even though Settings already refuses to store them: this is the second gate, and the one that runs
        // when a stored preference was edited by hand.
        assertNull(quickBottleDraft(0, at))
        assertNull(quickBottleDraft(-30, at))
        assertNull(quickBottleDraft(SettingsRepository.MAX_BOTTLE_PRESET_ML + 1, at))
    }
}
