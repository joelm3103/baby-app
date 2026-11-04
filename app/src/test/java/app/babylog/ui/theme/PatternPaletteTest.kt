package app.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import app.babylog.domain.EVENT_DISPLAY_ORDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The card's artwork and its gradient are drawn BEHIND every word on the card, so both are colour
 * decisions that can quietly break accessibility. These assertions are the numbers `Tokens.kt` records
 * in its comments, counted rather than eyeballed — the same reason the fractions are written down there.
 *
 * It was rewritten for Phase 22 rather than deleted: the tiled glyph pattern became a filled
 * illustration over a gradient, and the old pattern assertions would have kept passing while guarding
 * nothing. The bars below are unchanged where they still apply, and the two new ones cover the numbers
 * the phase actually introduced.
 *
 * Four things must stay true, in both themes, for all six activities:
 *
 *  1. the artwork is **visible** against the fill it sits on (dE >= 7 — below that it is a smudge, which
 *     is what an earlier 0.10 attempt measured);
 *  2. the artwork does **not vanish** where it crosses the gradient's lower stop (dE >= 4, above the
 *     ~2.3 CIE76 just-noticeable difference with margin, so a shape running off the bottom of a card
 *     softens rather than disappearing);
 *  3. the gradient is **perceptible** at all (dE >= 3 between its two stops);
 *  4. every text token on the card still clears **AA (4.5:1)** on the artwork *and* on both stops — not
 *     just on the flat tint — and the chip's glyph keeps its **3:1** non-text bar.
 *
 * Measured at this commit, the minima are: 7.4 (1), 4.2 (2), 3.2 (3), 4.50:1 for `textOnCard` on the
 * dark Bottle card's artwork and 5.05:1 on any lower stop. If someone retunes the accents, widens the
 * tint, raises the gradient step or lifts the alpha "to make it softer", these fail rather than shipping
 * a card that is pretty and unreadable.
 */
class PatternPaletteTest {

    private val themes = listOf("dark" to DarkColors, "light" to LightColors)

    private fun accents(colors: BabyLogColors): List<Color> = EVENT_DISPLAY_ORDER.map(colors::accentFor)

    @Test
    fun theArtworkIsVisibleOnEveryCard() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val d = deltaE(colors.cardFor(accent), colors.artFor(accent))
                assertTrue(
                    "$name: the artwork is only dE ${round(d)} from the card fill, so it is a smudge",
                    d >= 7.0,
                )
            }
        }
    }

    @Test
    fun theArtworkSurvivesTheGradient() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val lower = colors.cardStopsFor(accent).last()
                val d = deltaE(colors.artFor(accent), lower)
                assertTrue(
                    "$name: the artwork is dE ${round(d)} from the gradient's lower stop, so it vanishes there",
                    d >= 4.0,
                )
            }
        }
    }

    @Test
    fun theGradientIsPerceptible() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val stops = colors.cardStopsFor(accent)
                val d = deltaE(stops.first(), stops.last())
                assertTrue("$name: the gradient is only dE ${round(d)}, which is not a gradient", d >= 3.0)
            }
        }
    }

    /** The anchor cannot drift: the gradient's top stop is the 26% tint every other number was taken against. */
    @Test
    fun theTopStopIsTheMeasuredTint() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val stops = colors.cardStopsFor(accent)
                assertEquals("$name: the gradient must have exactly two stops", 2, stops.size)
                assertEquals("$name: the gradient's top stop moved off the measured tint", colors.cardFor(accent), stops.first())
            }
        }
    }

    @Test
    fun metaTextClearsAaOnTheArtworkAndBothStops() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                surfacesOnACard(colors, accent).forEach { (where, fill) ->
                    val c = contrast(colors.textOnCard, fill)
                    assertTrue("$name: textOnCard is ${round(c)}:1 $where, below AA", c >= 4.5)
                }
            }
        }
    }

    @Test
    fun theLabelClearsAaOnTheArtworkAndBothStops() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val label = colors.labelFor(accent)
                surfacesOnACard(colors, accent).forEach { (where, fill) ->
                    val c = contrast(label, fill)
                    assertTrue("$name: the label is ${round(c)}:1 $where, below AA", c >= 4.5)
                }
            }
        }
    }

    /** The big number uses `textPrimary`, which must survive the artwork too — it is the loudest text. */
    @Test
    fun thePrimaryValueClearsAaOnTheArtworkAndBothStops() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                surfacesOnACard(colors, accent).forEach { (where, fill) ->
                    val c = contrast(colors.textPrimary, fill)
                    assertTrue("$name: textPrimary is ${round(c)}:1 $where, below AA", c >= 4.5)
                }
            }
        }
    }

    /**
     * A chip's glyph is a graphic, not text, so the bar is 3:1 — but it is a *small* graphic beside a
     * label, which is exactly the case where an accent that only just passes reads as a smudge.
     */
    @Test
    fun chipGlyphsClearTheNonTextBar() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val off = contrast(accent, colors.surfaceRaised)
                val on = contrast(accent, colors.onAccent)
                assertTrue("$name: the unselected chip glyph measures ${round(off)}:1", off >= 3.0)
                assertTrue("$name: the selected chip glyph measures ${round(on)}:1", on >= 3.0)
            }
        }
    }

    /** Everything a word on a card can be sitting on: the artwork, and each of the gradient's stops. */
    private fun surfacesOnACard(colors: BabyLogColors, accent: Color): List<Pair<String, Color>> =
        listOf("on the artwork" to colors.artFor(accent)) +
            colors.cardStopsFor(accent).mapIndexed { index, stop ->
                "on gradient stop $index" to stop
            }

    private fun round(v: Double) = (v * 100).toInt() / 100.0

    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** CIE76, on the same D65 white the accents were derived against. */
    private fun deltaE(a: Color, b: Color): Double {
        val la = lab(a)
        val lb = lab(b)
        return sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
    }

    private fun lab(color: Color): DoubleArray {
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
        val y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.0
        val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883

        fun f(t: Double): Double = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }
}
