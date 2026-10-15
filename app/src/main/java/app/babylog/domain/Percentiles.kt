package app.babylog.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * WHO's LMS method: turn a measurement and its reference parameters into a z-score and a percentile.
 *
 * Nothing here is invented. LMS is published data (see `app/src/main/assets/growth/`) and the z-score
 * formula is the standard one:
 *
 *     z = ((X / M)^L - 1) / (L · S)     for L ≠ 0
 *     z = ln(X / M) / S                 for L = 0
 *
 * The `L = 0` case is not theoretical: the WHO tables contain L values at or very near zero at some
 * ages, and the naive formula divides by zero there and returns infinity or NaN without an error.
 *
 * Signatures take `l`, `m`, `s` rather than an LMS row type so this stays in `domain`, which sits
 * below the data layer: the maths must be testable with published values and no asset, no Room and no
 * Android.
 *
 * Every function that can be asked a meaningless question returns **null** rather than a number. A
 * percentile is a claim about a child; "0 kg" is in the 0.00th percentile of nothing, and a silent NaN
 * would eventually be rendered as text.
 */

/** The standard normal quantiles for the percentile curves the chart plots. Published constants. */
private val Z_FOR_CURVE = mapOf(
    2 to -2.0537489106318225,
    10 to -1.2815515655446004,
    25 to -0.6744897501960817,
    50 to 0.0,
    75 to 0.6744897501960817,
    90 to 1.2815515655446004,
    98 to 2.0537489106318225,
)

/** The curve bundle drawn behind the child's marker, low to high. */
val PERCENTILE_CURVES: List<Int> = Z_FOR_CURVE.keys.sorted()

/** z for one of [PERCENTILE_CURVES], or null for a percentile no curve is drawn at. */
fun zForCurve(percentile: Int): Double? = Z_FOR_CURVE[percentile]

/**
 * The z-score of [value] against the reference parameters, or null when the question is meaningless
 * (a non-positive measurement, or non-positive reference parameters).
 */
fun zScore(value: Double, l: Double, m: Double, s: Double): Double? {
    if (value <= 0.0 || m <= 0.0 || s <= 0.0) return null
    if (value.isNaN() || l.isNaN() || m.isNaN() || s.isNaN()) return null
    return if (abs(l) < 1e-9) {
        ln(value / m) / s
    } else {
        ((value / m).pow(l) - 1.0) / (l * s)
    }
}

/** The percentile (0–100) of [value], or null when it cannot be computed. */
fun percentile(value: Double, l: Double, m: Double, s: Double): Double? =
    zScore(value, l, m, s)?.let { normalCdf(it) * 100.0 }

/**
 * The inverse: the measurement at a given z. Used to draw a percentile curve.
 *
 *     X = M · (1 + L·S·z)^(1/L)     for L ≠ 0
 *     X = M · exp(S·z)              for L = 0
 */
fun valueAtZ(z: Double, l: Double, m: Double, s: Double): Double? {
    if (m <= 0.0 || s <= 0.0 || z.isNaN()) return null
    return if (abs(l) < 1e-9) {
        m * exp(s * z)
    } else {
        val base = 1.0 + l * s * z
        if (base <= 0.0) return null
        m * base.pow(1.0 / l)
    }
}

/**
 * The standard normal CDF, Φ(z).
 *
 * Abramowitz & Stegun 26.2.17 (absolute error < 7.5e-8), which is far tighter than the whole
 * percentage point a percentile is ever displayed to. Written out rather than pulled from a maths
 * library because the JDK has no `erf`.
 */
fun normalCdf(z: Double): Double {
    if (z.isNaN()) return Double.NaN
    if (z == 0.0) return 0.5
    val x = abs(z)
    val t = 1.0 / (1.0 + 0.2316419 * x)
    val poly = t * (
        0.319381530 + t * (
            -0.356563782 + t * (
                1.781477937 + t * (
                    -1.821255978 + t * 1.330274429
                    )
                )
            )
        )
    val upperTail = exp(-x * x / 2.0) / sqrt(2.0 * PI) * poly
    return if (z < 0) upperTail else 1.0 - upperTail
}

/**
 * `42nd`, for the callout card. Clamped to 1–100 so a value at either tail cannot render as `0th` or
 * as an unrounded float, and the teens are special-cased so 11th/12th/13th do not read as 11st/12nd.
 */
fun percentileLabel(percentile: Double): String {
    val n = percentile.roundToInt().coerceIn(1, 100)
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
