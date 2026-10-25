package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-part diaper answer (Phase 13.3).
 *
 * The sheet asks "was it wet?" and "was it dirty?" independently, and `MIXED` is only how "both" is
 * spelled in the column it has occupied since schema v1. These tests are the reason that mapping lives in
 * the domain rather than in a `Modifier.clickable`.
 */
class DiaperKindTest {

    @Test
    fun `the three stored values read back as the two answers`() {
        assertTrue(DiaperKind.WET.wet)
        assertFalse(DiaperKind.WET.dirty)
        assertFalse(DiaperKind.DIRTY.wet)
        assertTrue(DiaperKind.DIRTY.dirty)
        assertTrue(DiaperKind.MIXED.wet)
        assertTrue(DiaperKind.MIXED.dirty)
    }

    @Test
    fun `of maps every legal pair and refuses the illegal one`() {
        assertEquals(DiaperKind.WET, DiaperKind.of(wet = true, dirty = false))
        assertEquals(DiaperKind.DIRTY, DiaperKind.of(wet = false, dirty = true))
        assertEquals(DiaperKind.MIXED, DiaperKind.of(wet = true, dirty = true))

        // "Neither" is not a nappy anyone changes at 3am, and there is no fourth value to put it in.
        val refused = runCatching { DiaperKind.of(wet = false, dirty = false) }
        assertTrue("neither must be unrepresentable", refused.isFailure)
        assertEquals(IllegalArgumentException::class.java, refused.exceptionOrNull()!!::class.java)
    }

    @Test
    fun `tapping Wet adds it, and tapping again removes it`() {
        assertEquals(DiaperKind.MIXED, DiaperKind.toggle(DiaperKind.DIRTY, tapWet = true))
        assertEquals(DiaperKind.DIRTY, DiaperKind.toggle(DiaperKind.MIXED, tapWet = true))
    }

    @Test
    fun `tapping Dirty adds it, and tapping again removes it`() {
        assertEquals(DiaperKind.MIXED, DiaperKind.toggle(DiaperKind.WET, tapWet = false))
        assertEquals(DiaperKind.WET, DiaperKind.toggle(DiaperKind.MIXED, tapWet = false))
    }

    @Test
    fun `the last selected answer cannot be switched off`() {
        // No disabled Save button to explain, and no unrepresentable state to store: the chip just stays on.
        assertEquals(DiaperKind.WET, DiaperKind.toggle(DiaperKind.WET, tapWet = true))
        assertEquals(DiaperKind.DIRTY, DiaperKind.toggle(DiaperKind.DIRTY, tapWet = false))
    }
}
