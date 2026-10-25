package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The colour and consistency codec (Phase 13.3).
 *
 * Two claims matter more than the round trip. Empty must encode to NULL, so "not recorded" has one
 * representation rather than two. And an unknown name must be DROPPED rather than failing, because the
 * other phone may be a newer build — the same asymmetry `Mappers.enumOrNull` documents: show the nappy
 * without the colour rather than hide the nappy.
 */
class CodedValuesTest {

    private val colors = DiaperColor.entries.toTypedArray()
    private val consistency = DiaperConsistency.entries.toTypedArray()

    @Test
    fun `an empty list encodes to null, not to an empty string`() {
        assertNull(encodeCodes(emptyList<DiaperColor>()))
    }

    @Test
    fun `a round trip preserves order and drops duplicates`() {
        val encoded = encodeCodes(listOf(DiaperColor.BROWN, DiaperColor.GREEN, DiaperColor.BROWN))
        assertEquals("BROWN,GREEN", encoded)
        assertEquals(listOf(DiaperColor.BROWN, DiaperColor.GREEN), decodeCodes(encoded, colors))
    }

    @Test
    fun `decoding null is empty`() {
        assertEquals(emptyList<DiaperConsistency>(), decodeCodes(null, consistency))
        assertEquals(emptyList<DiaperConsistency>(), decodeCodes("  ", consistency))
    }

    @Test
    fun `an unknown name is dropped and its siblings survive`() {
        // "PURPLE" is a future build's colour; the rest of the row must still read.
        assertEquals(
            listOf(DiaperColor.YELLOW, DiaperColor.RED),
            decodeCodes("YELLOW,PURPLE,RED", colors),
        )
    }

    @Test
    fun `every consistency value survives a round trip`() {
        val encoded = encodeCodes(DiaperConsistency.entries)
        assertEquals(DiaperConsistency.entries, decodeCodes(encoded, consistency))
    }
}
