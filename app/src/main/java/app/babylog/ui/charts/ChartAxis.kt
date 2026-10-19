package app.babylog.ui.charts

import androidx.compose.ui.unit.dp

/**
 * The axis maths shared by every chart in the app.
 *
 * Extracted when Trends arrived, deliberately: Growth's percentile chart and Trends' per-day charts ask
 * the same two questions (what range does this data need, where do the gridlines go), and a second copy
 * of the answer would be a second set of axis bugs — the one thing the first chart's own test
 * (`yTicks` returning only two lines for head circumference) already caught once.
 *
 * Both functions are pure and take plain numbers, so they are unit-tested on the JVM with no Compose
 * runtime involved.
 */

/** Bottom gutter for x labels, left gutter for y labels. Shared so the two charts' type lines up. */
internal val X_AXIS_GUTTER = 22.dp
internal val Y_AXIS_GUTTER = 34.dp

/**
 * A little headroom above and below the data, snapped to a round step so the axis reads as a scale.
 *
 * A marker exactly on the curve's edge is a real case (a newborn at the 2nd percentile) and clipping it
 * would look like the chart had lost the child.
 *
 * [floorAtZero] is what a per-day chart needs and a percentile chart must not have: eight diapers on a
 * Tuesday and zero on a Wednesday is a shape that must start at zero, while a weight curve starting at
 * zero would flatten every curve into a line.
 */
internal fun yRangeFor(values: List<Double>, floorAtZero: Boolean = false): ClosedFloatingPointRange<Float> {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return 0f..1f
    val low = if (floorAtZero) 0.0 else finite.min()
    val high = finite.max()
    val span = (high - low).takeIf { it > 1e-6 } ?: (high * 0.2).coerceAtLeast(1.0)
    val pad = span * 0.08
    return ((low - pad).coerceAtLeast(0.0)).toFloat()..(high + pad).toFloat()
}

/**
 * At most this many gridlines. Fewer than three reads as a box rather than a scale; more than six
 * crowds the plot.
 */
private const val MAX_TICKS = 6

/**
 * Horizontal gridlines, snapped to round values, which is what makes an axis readable.
 *
 * Chooses the SMALLEST round step that still fits within [MAX_TICKS]. The first version rounded the
 * ideal step (span / 4) up to the next "nice" value instead, and the ladder's jump from 5 to 10 meant a
 * head circumference spanning ~31-52 cm got a 10-unit step and only TWO lines. The asset-driven test
 * caught it; the synthetic one had a wide enough span to hide it.
 */
internal fun yTicks(range: ClosedFloatingPointRange<Float>): List<Double> {
    val span = (range.endInclusive - range.start).toDouble()
    if (span <= 0.0) return listOf(range.start.toDouble())

    val step = tickStep(range, span)
    val first = kotlin.math.ceil(range.start / step) * step
    val out = mutableListOf<Double>()
    var value = first
    while (value <= range.endInclusive + 1e-9) {
        out.add(value)
        value += step
    }
    return out
}

private fun tickStep(range: ClosedFloatingPointRange<Float>, span: Double): Double {
    val ladder = listOf(1.0, 2.0, 2.5, 5.0)
    var decade = 1e-3
    while (decade <= 1e6) {
        for (unit in ladder) {
            val candidate = unit * decade
            if (candidate <= 0.0) continue
            val first = kotlin.math.ceil(range.start / candidate)
            val last = kotlin.math.floor(range.endInclusive / candidate)
            val count = last - first + 1
            if (count in 1.0..MAX_TICKS.toDouble()) return candidate
        }
        decade *= 10.0
    }
    return span
}

/** Kept next to the axis maths because the growth chart's curve styling sorts on it too. */
