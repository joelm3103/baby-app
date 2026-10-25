package app.babylog.ui.home

import app.babylog.domain.DiaperKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The Diaper card's one-tap path (Phase 13.3).
 *
 * The card offers Wet and Dirty and nothing else, because "both" is a two-part answer and a single tap
 * must not invent the part the parent did not give. That is a rule about which list the pills iterate, so
 * it is asserted rather than left to be "obvious" from the composable — `DiaperKind.entries` is the
 * tempting one-liner that would silently re-add the third pill.
 */
class DiaperQuickKindsTest {

    @Test
    fun `the card offers exactly Wet and Dirty`() {
        assertEquals(listOf(DiaperKind.WET, DiaperKind.DIRTY), DIAPER_QUICK_KINDS)
    }

    @Test
    fun `a quick pill can never write MIXED`() {
        assertFalse("a quick tap must not answer both at once", DiaperKind.MIXED in DIAPER_QUICK_KINDS)
    }
}
