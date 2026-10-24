package app.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import app.babylog.domain.EVENT_DISPLAY_ORDER
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The card glyph pattern is drawn BEHIND every word on the card, so it is a colour decision that can
 * quietly break accessibility. These assertions are the numbers the pattern was derived from, counted
 * rather than eyeballed — the same reason `Tokens.kt` records its fractions in comments.
 *
 * Three things must stay true, in both themes, for all six activities:
 *
 *  1. the pattern is **visible** against the card fill (dE >= 7 — below that it is a smudge, which is
 *     what the earlier 0.10 attempt measured at on the sleep and growth cards);
 *  2. every text token on the card still clears **AA (4.5:1) on the pattern**, not just on the tint;
 *  3. the chip's glyph clears the **3:1 non-text bar** when off, and against `onAccent` when on.
 *
 * If someone retunes the accents, lowers the tint, or pulls `PATTERN_ALPHA` down to "make it subtler",
 * these fail rather than shipping a card that is pretty and unreadable.
 */
class PatternPaletteTest {

    private val themes = listOf("dark" to DarkColors, "light" to LightColors)

    private fun accents(colors: BabyLogColors): List<Color> = EVENT_DISPLAY_ORDER.map(colors::accentFor)

    @Test
    fun thePatternIsVisibleOnEveryCard() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val d = deltaE(colors.cardFor(accent), colors.patternFor(accent))
                assertTrue(
                    "$name: the pattern is only dE ${round(d)} from the card fill, so it is a smudge",
                    d >= 7.0,
                )
            }
        }
    }

    @Test
    fun metaTextClearsAaOnThePatternAndOnThePlainTint() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val onPattern = contrast(colors.textOnCard, colors.patternFor(accent))
                val onTint = contrast(colors.textOnCard, colors.cardFor(accent))
                assertTrue(
                    "$name: textOnCard is ${round(onPattern)}:1 on the pattern, below AA",
                    onPattern >= 4.5,
                )
                assertTrue(
                    "$name: textOnCard is ${round(onTint)}:1 on the tint, below AA",
                    onTint >= 4.5,
                )
            }
        }
    }

    @Test
    fun theLabelClearsAaOnThePatternAndOnThePlainTint() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val label = colors.labelFor(accent)
                val onPattern = contrast(label, colors.patternFor(accent))
                val onTint = contrast(label, colors.cardFor(accent))
                assertTrue("$name: the label is ${round(onPattern)}:1 on the pattern", onPattern >= 4.5)
                assertTrue("$name: the label is ${round(onTint)}:1 on the tint", onTint >= 4.5)
            }
        }
    }

    /** The big number uses `textPrimary`, which must survive the pattern too — it is the loudest text. */
    @Test
    fun thePrimaryValueClearsAaOnThePattern() {
        themes.forEach { (name, colors) ->
            accents(colors).forEach { accent ->
                val c = contrast(colors.textPrimary, colors.patternFor(accent))
                assertTrue("$name: textPrimary is ${round(c)}:1 on the pattern", c >= 4.5)
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
                val on = contrast(colors.onAccent, accent)
                assertTrue("$name: the unselected chip glyph measures ${round(off)}:1", off >= 3.0)
                assertTrue("$name: the selected chip glyph measures ${round(on)}:1", on >= 3.0)
            }
        }
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
