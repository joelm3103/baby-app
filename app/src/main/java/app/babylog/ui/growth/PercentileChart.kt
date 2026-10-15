package app.babylog.ui.growth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * One percentile chart, drawn by hand.
 *
 * Deliberately a primitive: it takes curves, an optional set of markers, two ranges and two label
 * functions, and knows nothing about WHO, growth or children. Phase 12's Trends charts are the reason
 * — a second chart style would be a second set of axis bugs — and the reason there is no charting
 * dependency: the app's tokens are the design contract, and a library's defaults would fight them.
 *
 * Text goes through [TextMeasurer] rather than `drawText` on a `Painter`, so labels are real text with
 * the app's typography and can be measured before they are drawn.
 */
@Composable
fun PercentileChart(
    curves: List<PercentileCurve>,
    markers: List<GrowthMarker>,
    trajectories: List<GrowthTrajectory>,
    trajectoryColors: List<Color>,
    xMaxDays: Double,
    unit: ChartUnit,
    accent: Color,
    gridColor: Color,
    axisTextColor: Color,
    markerRingColor: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = axisTextColor, fontSize = 10.sp)

    // The child's own points are part of the range, so a pre-term 1.37 kg cannot push the axis off the
    // bottom or be clipped by the plot box.
    val allValues = curves.flatMap { curve -> curve.points.map { it.value } } +
        markers.map { it.value }
    val yRange = yRangeFor(allValues)

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            drawChart(
                curves = curves,
                markers = markers,
                trajectories = trajectories,
                trajectoryColors = trajectoryColors,
                xMaxDays = xMaxDays,
                yRange = yRange,
                unit = unit,
                accent = accent,
                gridColor = gridColor,
                axisTextColor = axisTextColor,
                markerRingColor = markerRingColor,
                textMeasurer = textMeasurer,
                tickStyle = tickStyle,
                legendStyle = tickStyle,
            )
        }
    }
}

private val CHART_HEIGHT = 260.dp

/** Bottom gutter for the month labels, left gutter for the value labels. */
private val X_AXIS_GUTTER = 22.dp
private val Y_AXIS_GUTTER = 34.dp

/**
 * A little headroom above and below the data, snapped to a round step so the axis reads as a scale.
 *
 * A marker exactly on the curve's edge is a real case (a newborn at the 2nd percentile) and clipping it
 * would look like the chart had lost the child.
 */
internal fun yRangeFor(values: List<Double>): ClosedFloatingPointRange<Float> {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return 0f..1f
    val low = finite.min()
    val high = finite.max()
    val span = (high - low).takeIf { it > 1e-6 } ?: (high * 0.2).coerceAtLeast(1.0)
    val pad = span * 0.08
    return ((low - pad).coerceAtLeast(0.0)).toFloat()..(high + pad).toFloat()
}

private fun DrawScope.drawChart(
    curves: List<PercentileCurve>,
    markers: List<GrowthMarker>,
    trajectories: List<GrowthTrajectory>,
    trajectoryColors: List<Color>,
    xMaxDays: Double,
    yRange: ClosedFloatingPointRange<Float>,
    unit: ChartUnit,
    accent: Color,
    gridColor: Color,
    axisTextColor: Color,
    markerRingColor: Color,
    textMeasurer: TextMeasurer,
    tickStyle: TextStyle,
    legendStyle: TextStyle,
) {
    val left = Y_AXIS_GUTTER.toPx()
    val bottom = size.height - X_AXIS_GUTTER.toPx()
    val right = size.width
    val top = 0f
    val plotWidth = (right - left).coerceAtLeast(1f)
    val plotHeight = (bottom - top).coerceAtLeast(1f)

    fun x(day: Double) = left + (day / xMaxDays.coerceAtLeast(1.0)).toFloat() * plotWidth
    fun y(value: Double): Float {
        val span = (yRange.endInclusive - yRange.start).takeIf { it > 0f } ?: 1f
        return bottom - ((value.toFloat() - yRange.start) / span) * plotHeight
    }

    // ---- grid and y labels -------------------------------------------------------------------
    val ticks = yTicks(yRange)
    for (tick in ticks) {
        val tickY = y(tick)
        drawLine(
            color = gridColor,
            start = Offset(left, tickY),
            end = Offset(right, tickY),
            strokeWidth = 1f,
        )
        val label = textMeasurer.measure(unit.format(tick), tickStyle)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = (left - label.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                y = tickY - label.size.height / 2f,
            ),
        )
    }

    // ---- curves ------------------------------------------------------------------------------
    // The middle curve is drawn solid and the rest dashed, so the 50th percentile reads as the
    // reference line rather than one of seven equals.
    for (curve in curves.sortedBy { abs(it.percentile - 50) }) {
        if (curve.points.size < 2) continue
        val path = Path()
        curve.points.forEachIndexed { index, point ->
            val px = x(point.axis)
            val py = y(point.value)
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        val isMedian = curve.percentile == 50
        drawPath(
            path = path,
            color = if (isMedian) accent.copy(alpha = 0.9f) else accent.copy(alpha = 0.45f),
            style = Stroke(
                width = if (isMedian) 2.5f else 1.5f,
                pathEffect = if (isMedian) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            ),
        )
    }

    // ---- the children's own lines ---------------------------------------------------------------
    // Drawn over the reference and under the markers: the bundle is the backdrop, the line is the child.
    // Each segment is decided separately, so the join into a pre-term point is dashed while the rest of
    // the line stays solid - one unbroken story, with the one join that is not real growth flagged.
    trajectories.forEachIndexed { index, trajectory ->
        val color = trajectoryColors.getOrElse(index) { markerRingColor }
        trajectory.points.zipWithNext { from, to ->
            drawLine(
                color = color,
                start = Offset(x(from.axis), y(from.value)),
                end = Offset(x(to.axis), y(to.value)),
                strokeWidth = 3f,
                pathEffect = if (segmentIsDashed(from, to)) {
                    PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                } else {
                    null
                },
            )
        }
    }

    // ---- the children ------------------------------------------------------------------------
    for (marker in markers) {
        val px = x(marker.axis).coerceIn(left, right)
        val py = y(marker.value).coerceIn(top, bottom)
        // A hollow marker, per the design contract: the fill is the card, the ring is the child.
        drawCircle(color = markerRingColor, radius = 9f, center = Offset(px, py), style = Stroke(3f))
        drawCircle(color = markerRingColor, radius = 3f, center = Offset(px, py))
        // The badge, so two children on one chart are told apart by more than position.
        val badge = textMeasurer.measure(marker.badge, legendStyle)
        drawText(
            textLayoutResult = badge,
            topLeft = Offset(
                x = (px + 12f).coerceAtMost(right - badge.size.width),
                y = (py - badge.size.height - 2f).coerceAtLeast(top),
            ),
        )
    }

    // ---- x labels ----------------------------------------------------------------------------
    val monthStepDays = xLabelStep(xMaxDays)
    var day = 0.0
    while (day <= xMaxDays) {
        val label = textMeasurer.measure(monthLabel(day), tickStyle)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = (x(day) - label.size.width / 2f).coerceIn(0f, right - label.size.width),
                y = bottom + 6.dp.toPx(),
            ),
        )
        day += monthStepDays
    }
}

/**
 * At most this many gridlines. Fewer than three reads as a box rather than a scale; more than six
 * crowds a 260dp-tall chart.
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

/** Month labels: every month under a year, then every three, so they never collide. */
internal fun xLabelStep(xMaxDays: Double): Double = when {
    xMaxDays <= 400.0 -> 30.4375
    else -> 91.3125
}
