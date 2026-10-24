package app.babylog.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.babylog.R
import app.babylog.domain.EventType
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.patternFor

/**
 * One glyph per activity type, used twice: tiled as a tone-on-tone watermark behind a card, and small
 * and solid beside a chip's label. Two uses, one asset each, so the pattern on a card and the chip that
 * controls that card can never show different pictures.
 *
 * A `when` rather than a map: this is exhaustive by construction, so adding an activity type fails the
 * build here instead of shipping a card with no glyph. `ActivityGlyphTest` pins that too.
 *
 * The assets are stroked on a 24dp viewport and carry no colour of their own — the tint arrives at
 * runtime, because one glyph has to be the card's pattern colour and the chip's accent.
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
 * The card's glyph pattern, as a brush that repeats the activity's own glyph across the card.
 *
 * Tone-on-tone: the colour is [patternFor] — the card's fill moved one measured step toward its accent
 * — never white, because a white pattern over a 26% tint is either illegible (dark) or invisible (light).
 * See [app.babylog.ui.theme.PATTERN_ALPHA] for the numbers.
 *
 * The tile is rasterised ONCE per type, tint and density and then held in `remember`: the alternative,
 * drawing ~24 vectors per card per frame, is a scroll-time cost with no visual gain. The shader repeats
 * it at 1:1 device pixels, so the pattern's spacing is the same in dp on every screen.
 */
@Composable
fun rememberActivityPattern(type: EventType, accent: Color): ShaderBrush {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tint = BabyLogTheme.colors.patternFor(accent)
    val tile = remember(type, tint, density) { buildTile(context, type.glyphRes(), tint, density) }
    return remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
}

/**
 * Draws the activity's glyph pattern behind this element's content, clipped by whatever clip is already
 * in the modifier chain — the card's rounded rect, in practice.
 *
 * Behind the content on purpose: the pattern is texture, and every text token on the card was measured
 * against it rather than the other way round.
 */
@Composable
fun Modifier.activityPattern(type: EventType, accent: Color): Modifier {
    val brush = rememberActivityPattern(type, accent)
    return drawBehind { drawRect(brush) }
}

/** Glyph size inside the tile. Small enough that the pattern reads as texture, not as a repeated logo. */
private val GlyphSize = 26.dp

/**
 * Horizontal distance between glyphs, and the constraint that sets it: with the second row offset by
 * half a pitch, a glyph may be at most half a pitch wide (`Pitch / 2 >= GlyphSize + x`), or the offset
 * row would run off the tile's edge and be skipped on every other row.
 */
private val Pitch = 64.dp

/** Vertical distance between rows. A tile is two of these, so the half-pitch offset can be baked in. */
private val RowPitch = 36.dp

private fun buildTile(context: Context, @DrawableRes res: Int, tint: Color, density: Density): ImageBitmap {
    val width = with(density) { Pitch.roundToPx() }
    val height = with(density) { (RowPitch * 2).roundToPx() }
    val glyph = with(density) { GlyphSize.roundToPx() }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val drawable = context.getDrawable(res)?.mutate() ?: return bitmap.asImageBitmap()
    drawable.setTint(tint.toArgb())

    // Two rows in one tile, the second offset by half a pitch. Baked in rather than drawn per frame:
    // the half-step is what turns a grid into a diagonal field, and it is the one thing a naive
    // repeated shader cannot express.
    val rows = with(density) {
        arrayOf(
            4.dp.toPx() to 5.dp.toPx(),
            (4.dp + Pitch / 2).toPx() to (5.dp + RowPitch).toPx(),
        )
    }
    rows.forEach { (xPx, yPx) ->
        val x = xPx.toInt()
        val y = yPx.toInt()
        // A glyph that would cross the tile's edge is skipped rather than wrapped: with the geometry
        // above neither does (4 + 26 = 30 <= 32 and 36 + 26 = 62 <= 64, 5 + 26 = 31 and 41 + 26 = 67 <=
        // 72), and a wrap would draw it twice on the seam.
        if (x + glyph > width || y + glyph > height) return@forEach
        drawable.setBounds(x, y, x + glyph, y + glyph)
        drawable.draw(canvas)
    }
    return bitmap.asImageBitmap()
}
