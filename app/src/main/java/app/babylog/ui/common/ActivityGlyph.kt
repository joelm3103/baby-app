package app.babylog.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import app.babylog.R
import app.babylog.domain.DiaperConsistency
import app.babylog.domain.EventType
import app.babylog.ui.growth.GrowthMetric

/**
 * One glyph per activity type, drawn as small stroke line-art inside a chip.
 *
 * The card's own decoration is NOT this: since Phase 22 a card carries the filled illustrations in
 * [app.babylog.ui.common.cardArtwork], and these six remain for the controls — the activity chips on
 * Summary and Trends, the sheet title bars and the entry rows — where a 16dp control wants a 1.7dp
 * outline rather than a silhouette. Two jobs, two asset families, and `ActivityGlyphTest` pins that the
 * chip glyph and the card artwork can never be the same drawable.
 *
 * A `when` rather than a map: this is exhaustive by construction, so adding an activity type fails the
 * build here instead of shipping a card with no glyph. `ActivityGlyphTest` pins that too.
 *
 * The assets are stroked on a 24dp viewport and carry no colour of their own — the tint arrives at
 * runtime, because one glyph has to be a chip's accent and a title bar's label colour.
 */
@DrawableRes
fun EventType.glyphRes(): Int = when (this) {
    EventType.BOTTLE -> R.drawable.ic_glyph_bottle
    EventType.SOLIDS -> R.drawable.ic_glyph_solids
    EventType.DIAPER -> R.drawable.ic_glyph_diaper
    EventType.GROWTH -> R.drawable.ic_glyph_growth
    EventType.HEALTH -> R.drawable.ic_glyph_health
    EventType.SLEEP -> R.drawable.ic_glyph_sleep
}

/** The same glyph as a painter, for the chips and anywhere a one-off icon is drawn. */
@Composable
fun EventType.glyphPainter(): Painter = painterResource(glyphRes())

/**
 * The shape a consistency is drawn as (Phase 13.3).
 *
 * Same `when`-not-a-map reasoning as [glyphRes]: adding a consistency fails the build here rather than
 * shipping a chip with a blank square. These are FILLED silhouettes rather than the stroke line-art the
 * activity glyphs use, because a 1.7dp outline at 16dp inside a chip muddies; the shape has to carry the
 * meaning (pebbles are pebbles) and the word beside it names it. Colour never carries it, so the field
 * survives a colour-blind parent and a monochrome screenshot alike.
 */
@DrawableRes
fun DiaperConsistency.stoolRes(): Int = when (this) {
    DiaperConsistency.SOLID -> R.drawable.ic_stool_solid
    DiaperConsistency.MUSHY -> R.drawable.ic_stool_mushy
    DiaperConsistency.RUNNY -> R.drawable.ic_stool_runny
    DiaperConsistency.MUCOUSY -> R.drawable.ic_stool_mucousy
    DiaperConsistency.HARD -> R.drawable.ic_stool_hard
    DiaperConsistency.PEBBLES -> R.drawable.ic_stool_pebbles
    DiaperConsistency.DIARRHEA -> R.drawable.ic_stool_diarrhea
}

/**
 * The shape a growth METRIC is drawn as.
 *
 * Not [glyphRes]: an activity glyph says which activity, and all three metric chips belong to the same
 * activity — the same picture three times would distinguish nothing, which is why the owner asked for
 * per-metric marks instead. Scale, an up arrow off a floor, and a head with a curl; `BMI_FOR_AGE` exists
 * in the bundled dataset but has no chip, so there is no fourth.
 *
 * Stroke line-art at 1.7dp, the same weight as the activity glyphs, because these sit in the same kind of
 * chip and a filled silhouette here would read as one of the nappy consistencies.
 */
@DrawableRes
fun GrowthMetric.iconRes(): Int = when (this) {
    GrowthMetric.WEIGHT -> R.drawable.ic_metric_weight
    GrowthMetric.LENGTH -> R.drawable.ic_metric_length
    GrowthMetric.HEAD -> R.drawable.ic_metric_head
}

/** The same metric mark as a painter, for the chips. */
@Composable
fun GrowthMetric.iconPainter(): Painter = painterResource(iconRes())
