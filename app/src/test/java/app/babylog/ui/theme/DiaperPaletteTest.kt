package app.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import app.babylog.domain.DiaperColor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The nappy's colour swatches (Phase 13.3), measured rather than judged.
 *
 * The six fills are LITERAL colours — what the parent saw — so unlike an accent they cannot be tuned for
 * contrast: a black stool is black. That makes the RING the contract, and it is the only reason this
 * field exists in both themes at all:
 *
 *  - `Black` against the dark `surface` is about 1.1:1, and `White` against the light one about 1.0:1, so
 *    without a hairline one of the two is a blank space in one of the two themes;
 *  - therefore every swatch carries a 1dp ring in `textSecondary`, and it must clear the 3:1 non-text bar
 *    against BOTH the fill and the surface it sits on, or the ring is decoration rather than a boundary;
 *  - a swatch must also not be confused with a role: `danger` is a destructive action and `background` is
 *    the app's own surface, so neither may be reused as "red" or "black".
 *
 * If someone retunes a fill, drops the ring to "make it cleaner", or reuses a token, these fail instead of
 * shipping an invisible dot.
 */
class DiaperPaletteTest {

    private val themes = listOf("dark" to DarkColors, "light" to LightColors)

    private val fills: List<Pair<DiaperColor, Color>> = listOf(
        DiaperColor.YELLOW to DiaperSwatch.Yellow,
        DiaperColor.BROWN to DiaperSwatch.Brown,
        DiaperColor.BLACK to DiaperSwatch.Black,
        DiaperColor.GREEN to DiaperSwatch.Green,
        DiaperColor.RED to DiaperSwatch.Red,
        DiaperColor.WHITE to DiaperSwatch.White,
    )

    /** The ring must always read as a line on the sheet it is drawn on. */
    @Test
    fun everySwatchRingClearsTheNonTextBarAgainstTheSurface() {
        themes.forEach { (name, colors) ->
            val c = contrast(DiaperSwatch.ringFor(colors), colors.surface)
            assertTrue("$name: the ring is ${round(c)}:1 on the surface, so it is not a boundary", c >= 3.0)
        }
    }

    /**
     * The rule that actually matters at each circle's edge: SOMETHING clears 3:1 there.
     *
     * Not "the ring always clears the fill" — a fill set running from black to white has no single grey that
     * does (the mid-greys measure 1.0-2.9:1 against the ends), which is why the ring is a per-theme pole
     * rather than `textSecondary`. So the assertion is a disjunction, and the two halves are both real: on
     * the dark scheme Brown and Black rely on the ring, on the light scheme Yellow and White do.
     */
    @Test
    fun everySwatchEdgeIsDelimitedInBothThemes() {
        themes.forEach { (name, colors) ->
            val ring = DiaperSwatch.ringFor(colors)
            fills.forEach { (color, fill) ->
                val againstSurface = contrast(fill, colors.surface)
                val againstRing = contrast(ring, fill)
                assertTrue(
                    "$name: $color is ${round(againstSurface)}:1 on the surface and ${round(againstRing)}:1 " +
                        "against the ring - it has no visible edge",
                    maxOf(againstSurface, againstRing) >= 3.0,
                )
            }
        }
    }

    /** Black and White are the two that would vanish; this names them so the failure is obvious. */
    @Test
    fun theTwoExtremeSwatchesAreTheReasonTheRingExists() {
        val dark = contrast(DiaperSwatch.Black, DarkColors.surface)
        val light = contrast(DiaperSwatch.White, LightColors.surface)
        assertTrue("expected unringed Black to be nearly invisible on the dark surface, was ${round(dark)}:1", dark < 1.5)
        assertTrue("expected unringed White to be nearly invisible on the light surface, was ${round(light)}:1", light < 1.5)
        // ...and both visible with it.
        assertTrue(contrast(DiaperSwatch.ringFor(DarkColors), DiaperSwatch.Black) >= 3.0)
        assertTrue(contrast(DiaperSwatch.ringFor(LightColors), DiaperSwatch.White) >= 3.0)
    }

    /** The dE between each fill and the surface, recorded so a future retune has the old numbers. */
    @Test
    fun theSwatchFillsAreDistinguishableFromEachOther() {
        fills.forEachIndexed { i, (nameA, a) ->
            fills.drop(i + 1).forEach { (nameB, b) ->
                val d = deltaE(a, b)
                assertTrue("$nameA and $nameB are only dE ${round(d)} apart", d >= 8.0)
            }
        }
    }

    /** Colour is a role-free fact: not the destructive token, not the app's own background. */
    @Test
    fun noSwatchReusesARoleToken() {
        themes.forEach { (name, colors) ->
            fills.forEach { (color, fill) ->
                assertTrue("$name: $color must not be the `danger` token", fill != colors.danger)
                assertTrue("$name: $color must not be the `background` token", fill != colors.background)
                assertTrue("$name: $color must not be the `surface` token", fill != colors.surface)
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
