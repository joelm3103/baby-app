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
import app.babylog.ui.charts.X_AXIS_GUTTER
import app.babylog.ui.charts.Y_AXIS_GUTTER
import app.babylog.ui.charts.yRangeFor
import app.babylog.ui.charts.yTicks
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

/**
 * A little headroom above and below the data, snapped to a round step so the axis reads as a scale.
 *
 * A marker exactly on the curve's edge is a real case (a newborn at the 2nd percentile) and clipping it
 * would look like the chart had lost the child.
 */
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

/** Month labels: every month under a year, then every three, so they never collide. */
internal fun xLabelStep(xMaxDays: Double): Double = when {
    xMaxDays <= 400.0 -> 30.4375
    else -> 91.3125
}
