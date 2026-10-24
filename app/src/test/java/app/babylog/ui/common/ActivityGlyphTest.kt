package app.babylog.ui.common

import app.babylog.domain.EVENT_DISPLAY_ORDER
import app.babylog.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glyph set is a mapping, and a mapping can be incomplete without anything failing to compile —
 * `when` only protects the branches that exist. These two assertions are the cheap half of "every
 * activity looks like itself": a type with no drawable, or two types sharing one, is a card that lies
 * about which activity it is.
 */
class ActivityGlyphTest {

    @Test
    fun everyActivityTypeHasItsOwnGlyph() {
        val ids = EventType.entries.map { it.glyphRes() }
        assertTrue("every activity type must resolve to a drawable", ids.none { it == 0 })
        assertEquals(
            "two activity types must never share one glyph",
            ids.size,
            ids.toSet().size,
        )
    }

    /**
     * The card order is a separate list from the enum. A new type added to the enum but not to the order
     * would get a glyph and no card, so the two are held together here.
     */
    @Test
    fun theDashboardOrderCoversEveryType() {
        assertEquals(EventType.entries.toSet(), EVENT_DISPLAY_ORDER.toSet())
    }
}
