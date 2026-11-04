package app.babylog.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.babylog.R
import app.babylog.domain.EventType
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.artFor

/**
 * The "Filled Soft" card decoration (Phase 22): the activity's own illustration as a complete object
 * placed on the card, with scattered silhouette shapes and hearts spread across the card behind it.
 *
 * TWO LAYERS, ONE TONE. The illustration is drawn last, so it sits in front of the scatter, and it is
 * four to six times the area of any scattered shape. Both are drawn in [artFor] — the card fill moved one
 * measured step toward its own accent — and that single tone is not laziness: the ceiling is the same for
 * both layers, because the scatter is spread over the whole card, so the big value and a FEVER number can
 * sit on a scattered shape exactly as they can on the illustration. Measured, the largest step that keeps
 * every card token at 4.5:1 is 0.14 on both themes, and the binding pair is the Health card's fever
 * number (4.59:1), not the meta text. So the hierarchy between the two layers is SIZE AND ORDER, never
 * tone — raising the tone for a stronger "sticker" would weaken the one cue that marks a fever.
 *
 * Both shapes are drawn OPAQUE in that colour rather than as a translucent accent, because compositing
 * the accent at that alpha over the fill gives exactly this colour, and an opaque value keeps the card's
 * text guarantee a single number instead of an alpha stack. The softness comes from the tone's distance
 * from the fill, never from translucency.
 *
 * The decoration sits BEHIND the card's content — the log's numbers, labels and buttons are the data, and
 * none of them may be covered by a picture. "In front" here means in front of the other decoration.
 */
@Composable
fun Modifier.cardArtwork(type: EventType, accent: Color): Modifier {
    val art = BabyLogTheme.colors.artFor(accent)
    val density = LocalDensity.current
    val decoration = remember(type) { cardDecoration(type) }
    // One painter per DISTINCT drawable, in a stable order, then indexed back in the order the shapes are
    // drawn. Deliberately not a map keyed by resource id with a painterResource call behind an `if`: a
    // composable call made conditionally inside a loop is a slot-table footgun, and the `?:` that followed
    // it turned a missing entry into a silently undrawn shape — which is how a card shipped with half its
    // decoration missing. Nothing here can skip a shape.
    val assets = remember(decoration) {
        (listOf(decoration.sticker.res) + decoration.scatter.map { it.res }).distinct()
    }
    val assetPainters = assets.map { res -> painterResource(res) }
    fun painterFor(res: Int) = assetPainters[assets.indexOf(res)]
    return drawBehind {
        // Back layer: the scattered shapes. Positioned as a FRACTION of the card, so a short card and a
        // tall one are both covered — the same list serves a 110dp callout and a 400dp feed card.
        decoration.scatter.forEach { shape ->
            val side = with(density) { shape.sizeDp.dp.toPx() }
            translate(left = shape.xFraction * size.width, top = shape.yFraction * size.height) {
                with(painterFor(shape.res)) { draw(size = Size(side, side), colorFilter = ColorFilter.tint(art)) }
            }
        }
        // Front layer: the sticker, a complete object rather than a watermark. Anchored to the card's RIGHT
        // edge and measured down in dp, so it stays whole on every screen width instead of being cut by the
        // edge, and stays clear of the card's title row where the activity label and relative time live.
        val sticker = decoration.sticker
        val topPx = with(density) { sticker.topDp.dp.toPx() }
        // Clamped to the card, so a short card carries a smaller WHOLE sticker rather than the top half of a
        // big one: the Summary total cards are about 110dp tall, where a fixed 124dp object would be a crop.
        val side = with(density) { stickerSideDp(sticker.sizeDp, size.height / density.density, sticker.topDp).dp.toPx() }
        val left = (size.width - with(density) { (sticker.rightMarginDp + sticker.sizeDp).dp.toPx() })
            .coerceAtLeast(0f)
        translate(left = left, top = topPx) {
            with(painterFor(sticker.res)) { draw(size = Size(side, side), colorFilter = ColorFilter.tint(art)) }
        }
    }
}

/**
 * The activity's illustration as a placed object: which asset, how far its right edge sits from the
 * card's right edge, how far below the card's top it starts, and its side.
 *
 * Right-anchored rather than left-anchored because a sticker is an object with an edge, and an object
 * anchored to a corner keeps its margin on every phone; the first build anchored it as a fraction of the
 * width and let it run off the card, which reads as a cropped watermark rather than as a sticker.
 *
 * `topDp` starts below the title row on purpose: the activity label sits at the card's top left and the
 * relative time at its top right, and the sticker must not put its own tone behind either.
 */
data class CardSticker(
    @DrawableRes val res: Int,
    val rightMarginDp: Float,
    val topDp: Float,
    val sizeDp: Float,
)

/**
 * One shape at the back: which asset, where its top-left corner sits as a fraction of the card's width
 * and height, and its side.
 *
 * Fractions rather than dp for both axes, because this layer's job is to cover the whole card: the five
 * surfaces that use it range from a ~110dp callout to a ~400dp feed card, and a dp offset that graced a
 * tall card would fall off a short one. The consequence, accepted deliberately: the card's content sits
 * over some of these shapes — that is what "at the back" means, and it is why the set is spread rather
 * than clustered, so no single opaque control can hide the effect.
 */
data class CardScatter(
    @DrawableRes val res: Int,
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Float,
)

/** The whole decoration for one activity: the sticker in front, the scattered shapes behind it. */
data class CardDecoration(
    val sticker: CardSticker,
    val scatter: List<CardScatter>,
)

/** The activity's own illustration. One per type, so a card cannot borrow another's picture. */
@DrawableRes
fun EventType.artRes(): Int = when (this) {
    EventType.BOTTLE -> R.drawable.ic_soft_bottle
    EventType.SOLIDS -> R.drawable.ic_soft_bowl
    EventType.DIAPER -> R.drawable.ic_soft_diaper
    EventType.GROWTH -> R.drawable.ic_soft_giraffe
    EventType.HEALTH -> R.drawable.ic_soft_thermometer
    EventType.SLEEP -> R.drawable.ic_soft_moon
}

/** The shared decoration: a heart, which belongs to no activity and therefore to all of them. */
internal val Heart = R.drawable.ic_soft_heart

/** The Bottle card's second motif, reused as Growth's small one. */
internal val Pacifier = R.drawable.ic_soft_pacifier

/** The reference's third scattered motif: a head with a curl, used where an activity has no second object. */
internal val BabyFace = R.drawable.ic_soft_baby_face

/**
 * The card width and height the fractions and the `CardArtworkTest` bounds are written against: a 360dp
 * phone minus the two 16dp gutters, and a mid-sized feed card. They are nominal, not exact — the
 * assertions are inequalities, so a taller card spreads the scatter further rather than failing.
 */
const val CARD_REFERENCE_WIDTH_DP = 328f
const val CARD_REFERENCE_HEIGHT_DP = 260f

/** The sticker's side and its margin from the card's right edge. */
private const val STICKER_SIZE_DP = 124f
private const val STICKER_MARGIN_DP = 10f

/** Below the title row, so neither the activity label nor the relative time sits on the illustration. */
private const val STICKER_TOP_DP = 46f

/** A clamp floor and a bottom margin, so the sticker is a whole object on the shortest card too. */
private const val STICKER_MIN_SIDE_DP = 44f
private const val STICKER_BOTTOM_MARGIN_DP = 8f

/**
 * The sticker's side on a card of this height: the preferred size, clamped so the object stays WHOLE.
 *
 * A short card is not a smaller card with a cropped picture — it is a card with a smaller picture. The
 * Summary total cards measure about 110dp, where the preferred 124dp object would show only its top half
 * and read as the cropped watermark the redesign exists to replace. The floor keeps a card that is barely
 * taller than the title row from losing the illustration altogether.
 */
fun stickerSideDp(preferredDp: Float, cardHeightDp: Float, topDp: Float): Float =
    minOf(preferredDp, (cardHeightDp - topDp - STICKER_BOTTOM_MARGIN_DP).coerceAtLeast(STICKER_MIN_SIDE_DP))

/**
 * The decoration for one activity: one sticker, then eight scattered shapes.
 *
 * Hand-placed and CONSTANT rather than random, because a card that redrew its decoration on every scroll
 * would read as a glitch, and because a JVM test can then assert the set covers the card, stays inside it
 * and never collides with itself. The same list is used by the dashboard card, the Summary total, the
 * Trends summary, the Growth callout and the Health card, so one activity looks like itself everywhere —
 * the property the tiled pattern had, and the reason it is not replaced with a per-screen decision.
 *
 * The positions are a fixed skeleton with the motifs varied per activity: shapes at six different heights
 * with at least one in each third of the card, none inside the sticker's footprint at the reference size,
 * and no two overlapping. Scattered sizes run 22-30dp.
 */
fun cardDecoration(type: EventType): CardDecoration = CardDecoration(
    sticker = CardSticker(type.artRes(), rightMarginDp = STICKER_MARGIN_DP, topDp = STICKER_TOP_DP, sizeDp = STICKER_SIZE_DP),
    scatter = when (type) {
        EventType.BOTTLE -> listOf(
            CardScatter(Pacifier, 0.06f, 0.05f, 30f),
            CardScatter(Heart, 0.46f, 0.08f, 24f),
            CardScatter(Heart, 0.24f, 0.20f, 26f),
            CardScatter(BabyFace, 0.08f, 0.34f, 28f),
            CardScatter(Heart, 0.40f, 0.46f, 22f),
            CardScatter(Heart, 0.10f, 0.60f, 26f),
            CardScatter(Heart, 0.30f, 0.76f, 22f),
            CardScatter(Heart, 0.62f, 0.88f, 24f),
        )
        EventType.SOLIDS -> listOf(
            CardScatter(Heart, 0.10f, 0.06f, 26f),
            CardScatter(Heart, 0.48f, 0.10f, 22f),
            CardScatter(type.artRes(), 0.20f, 0.22f, 28f),
            CardScatter(Heart, 0.06f, 0.42f, 24f),
            CardScatter(BabyFace, 0.36f, 0.44f, 26f),
            CardScatter(Heart, 0.12f, 0.66f, 22f),
            CardScatter(Heart, 0.34f, 0.78f, 26f),
            CardScatter(Heart, 0.66f, 0.86f, 22f),
        )
        EventType.DIAPER -> listOf(
            CardScatter(Heart, 0.08f, 0.08f, 24f),
            CardScatter(Heart, 0.44f, 0.06f, 26f),
            CardScatter(BabyFace, 0.24f, 0.24f, 28f),
            CardScatter(Heart, 0.06f, 0.40f, 22f),
            CardScatter(Heart, 0.38f, 0.52f, 24f),
            CardScatter(Heart, 0.10f, 0.64f, 26f),
            CardScatter(Heart, 0.28f, 0.80f, 22f),
            CardScatter(Heart, 0.58f, 0.90f, 24f),
        )
        EventType.GROWTH -> listOf(
            CardScatter(Heart, 0.10f, 0.06f, 24f),
            CardScatter(Pacifier, 0.46f, 0.10f, 28f),
            CardScatter(Heart, 0.22f, 0.22f, 22f),
            CardScatter(Heart, 0.06f, 0.38f, 26f),
            CardScatter(BabyFace, 0.34f, 0.50f, 26f),
            CardScatter(Heart, 0.12f, 0.62f, 22f),
            CardScatter(Heart, 0.30f, 0.76f, 24f),
            CardScatter(Heart, 0.60f, 0.88f, 22f),
        )
        EventType.HEALTH -> listOf(
            CardScatter(Heart, 0.08f, 0.06f, 26f),
            CardScatter(Heart, 0.42f, 0.10f, 24f),
            CardScatter(BabyFace, 0.20f, 0.24f, 26f),
            CardScatter(Heart, 0.36f, 0.40f, 24f),
            CardScatter(Heart, 0.06f, 0.44f, 22f),
            CardScatter(Heart, 0.10f, 0.68f, 26f),
            CardScatter(Heart, 0.32f, 0.80f, 22f),
            CardScatter(Heart, 0.64f, 0.90f, 24f),
        )
        EventType.SLEEP -> listOf(
            CardScatter(Heart, 0.10f, 0.06f, 24f),
            CardScatter(Heart, 0.44f, 0.08f, 26f),
            CardScatter(Heart, 0.22f, 0.26f, 22f),
            CardScatter(BabyFace, 0.06f, 0.36f, 28f),
            CardScatter(Heart, 0.38f, 0.48f, 24f),
            CardScatter(Heart, 0.12f, 0.60f, 22f),
            CardScatter(Heart, 0.30f, 0.78f, 26f),
            CardScatter(Heart, 0.62f, 0.92f, 22f),
        )
    },
)
