package app.babylog.ui.common

import app.babylog.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card decoration is a mapping plus a layout, and neither fails to compile when it is wrong: a `when`
 * only protects the branches that exist, and a placement can put a shape off the card, shrink it to a
 * blob, or hide it behind the illustration without anything complaining.
 *
 * What is pinned here, and why each one is worth a test rather than a comment:
 *
 *  - **one illustration per activity, never shared** — two activities showing the same picture is a card
 *    that lies about which activity it is, the same defect `ActivityGlyphTest` guards for the chips;
 *  - **the card artwork is never the chip glyph** — the two families exist for two jobs (a silhouette at
 *    card size, a 1.7dp outline at 16dp), and "restoring" the glyph to the card is exactly the change this
 *    phase made;
 *  - **the decoration is deterministic** — a card that redrew it as it scrolled would read as a glitch;
 *  - **the sticker is a whole object** — inside the card with a margin on every width, and starting below
 *    the title row so it never puts its tone behind the activity label or the relative time. The first
 *    build anchored it as a fraction of the width and let it run off the card edge, which reads as a
 *    cropped watermark;
 *  - **the scatter covers the whole card** — at least six shapes with one in each third of the height.
 *    This is the correction the owner asked for on 2026-11-02: the first build kept every scattered shape
 *    inside the top 76dp "safe band", so the decoration only ever appeared in the title area;
 *  - **at most two shapes fall inside the sticker's footprint** — the rest must be seen. A shape drawn
 *    under the opaque sticker is drawn and invisible, and a set of five that hid two of themselves is
 *    exactly what shipped into the first device pass;
 *  - **a measured size floor per kind of shape** — at 16dp the giraffe, the nappy, the pacifier and the
 *    head merge into featureless blobs, while the heart and the bowl still read.
 *
 * The bounds use [CARD_REFERENCE_WIDTH_DP] and [CARD_REFERENCE_HEIGHT_DP] as a nominal card. A taller card
 * spreads the scatter further rather than failing, so every assertion that depends on the card's size is an
 * inequality.
 */
class CardArtworkTest {

    private fun stickers(type: EventType) = cardDecoration(type).sticker

    private fun scatter(type: EventType) = cardDecoration(type).scatter

    private fun box(shape: CardScatter): FloatArray = floatArrayOf(
        shape.xFraction * CARD_REFERENCE_WIDTH_DP,
        shape.yFraction * CARD_REFERENCE_HEIGHT_DP,
        shape.xFraction * CARD_REFERENCE_WIDTH_DP + shape.sizeDp,
        shape.yFraction * CARD_REFERENCE_HEIGHT_DP + shape.sizeDp,
    )

    @Test
    fun everyActivityHasItsOwnSticker() {
        val ids = EventType.entries.map { it.artRes() }
        assertTrue("every activity must resolve to an artwork drawable", ids.none { it == 0 })
        assertEquals("two activity types must never share one illustration", ids.size, ids.toSet().size)
        EventType.entries.forEach { type ->
            assertEquals("${type.name}: the sticker must be the activity's own illustration", type.artRes(), stickers(type).res)
        }
    }

    @Test
    fun theCardArtworkIsNeverTheChipGlyph() {
        EventType.entries.forEach { type ->
            assertNotEquals(
                "${type.name}: the card artwork and the chip glyph are the same asset",
                type.glyphRes(),
                type.artRes(),
            )
        }
    }

    @Test
    fun theDecorationIsDeterministic() {
        EventType.entries.forEach { type ->
            assertEquals("${type.name}: the decoration moved between two reads", cardDecoration(type), cardDecoration(type))
        }
        assertNotEquals(
            "two activities must not draw the same layout",
            cardDecoration(EventType.BOTTLE),
            cardDecoration(EventType.DIAPER),
        )
    }

    @Test
    fun theStickerIsAWholeObjectInsideTheCard() {
        EventType.entries.forEach { type ->
            val sticker = stickers(type)
            assertTrue(
                "${type.name}: the sticker is ${sticker.sizeDp}dp, which is not an object on a card",
                sticker.sizeDp in 100f..150f,
            )
            assertTrue(
                "${type.name}: the sticker sits ${sticker.rightMarginDp}dp from the right edge",
                sticker.rightMarginDp in 0f..24f,
            )
            assertTrue(
                "${type.name}: the sticker starts at ${sticker.topDp}dp, inside the title row where the label and the relative time live",
                sticker.topDp >= 40f,
            )
            assertTrue(
                "${type.name}: the sticker starts at ${sticker.topDp}dp, in the lower half of a short card",
                sticker.topDp + sticker.sizeDp <= CARD_REFERENCE_HEIGHT_DP,
            )
        }
    }

    @Test
    fun theStickerShrinksRatherThanBeingCroppedOnAShortCard() {
        EventType.entries.forEach { type ->
            val sticker = stickers(type)
            val tall = stickerSideDp(sticker.sizeDp, CARD_REFERENCE_HEIGHT_DP, sticker.topDp)
            assertEquals("${type.name}: a mid-sized card must carry the sticker at full size", sticker.sizeDp, tall, 0.01f)
            // A Summary total card is about 110dp; the sticker must fit inside it rather than be sliced.
            val short = stickerSideDp(sticker.sizeDp, 110f, sticker.topDp)
            assertTrue("${type.name}: a 110dp card gets a ${short}dp sticker", short < sticker.sizeDp)
            assertTrue(
                "${type.name}: a 110dp card cannot fit its sticker (${sticker.topDp} + $short)",
                sticker.topDp + short <= 110f,
            )
            assertTrue("${type.name}: a card is never left without an illustration", stickerSideDp(sticker.sizeDp, 20f, sticker.topDp) >= 44f)
        }
    }

    @Test
    fun theScatterCoversTheWholeCard() {
        EventType.entries.forEach { type ->
            val shapes = scatter(type)
            assertTrue("${type.name}: only ${shapes.size} scattered shapes", shapes.size >= 6)
            val heights = shapes.map { it.yFraction }
            assertTrue("${type.name}: nothing scattered in the card's top third", heights.any { it <= 1f / 3f })
            assertTrue("${type.name}: nothing scattered in the card's middle third", heights.any { it > 1f / 3f && it < 2f / 3f })
            assertTrue("${type.name}: nothing scattered in the card's bottom third", heights.any { it >= 2f / 3f })
        }
    }

    @Test
    fun mostScatteredShapesAreClearOfTheSticker() {
        EventType.entries.forEach { type ->
            val sticker = stickers(type)
            val stickerLeft = CARD_REFERENCE_WIDTH_DP - sticker.rightMarginDp - sticker.sizeDp
            val stickerTop = sticker.topDp
            val stickerBottom = sticker.topDp + sticker.sizeDp
            val inside = scatter(type).count { shape ->
                val b = box(shape)
                b[2] > stickerLeft && b[0] < CARD_REFERENCE_WIDTH_DP - sticker.rightMarginDp &&
                    b[3] > stickerTop && b[1] < stickerBottom
            }
            assertTrue(
                "${type.name}: $inside of ${scatter(type).size} scattered shapes sit inside the sticker and cannot be seen",
                inside <= 2,
            )
        }
    }

    @Test
    fun noTwoScatteredShapesOverlap() {
        EventType.entries.forEach { type ->
            val boxes = scatter(type).map { it to box(it) }
            boxes.forEachIndexed { index, (a, ba) ->
                boxes.drop(index + 1).forEach { (b, bb) ->
                    val overlap = !(ba[2] <= bb[0] || bb[2] <= ba[0] || ba[3] <= bb[1] || bb[3] <= ba[1])
                    assertTrue(
                        "${type.name}: two scattered shapes overlap at ${a.xFraction}/${a.yFraction} and ${b.xFraction}/${b.yFraction}",
                        !overlap,
                    )
                }
            }
        }
    }

    @Test
    fun everyScatteredShapeIsBigEnoughToRead() {
        EventType.entries.forEach { type ->
            scatter(type).forEach { shape ->
                assertTrue(
                    "${type.name}: a scattered shape is ${shape.sizeDp}dp, below the 20dp floor it was measured against",
                    shape.sizeDp >= 20f,
                )
                assertTrue(
                    "${type.name}: a scattered shape is ${shape.sizeDp}dp, a second sticker rather than texture",
                    shape.sizeDp <= 40f,
                )
            }
        }
    }

    @Test
    fun everyScatteredShapeStaysOnTheCard() {
        EventType.entries.forEach { type ->
            scatter(type).forEach { shape ->
                assertTrue(
                    "${type.name}: a shape starts at x ${shape.xFraction} of the card's width",
                    shape.xFraction >= 0f && shape.xFraction <= 1f,
                )
                assertTrue("${type.name}: a shape starts at y ${shape.yFraction} of the card's height", shape.yFraction in 0f..0.92f)
                val bottom = shape.yFraction + shape.sizeDp / CARD_REFERENCE_HEIGHT_DP
                assertTrue("${type.name}: a shape ends at ${bottom} of the card's height", bottom <= 1.02f)
            }
        }
    }
}
