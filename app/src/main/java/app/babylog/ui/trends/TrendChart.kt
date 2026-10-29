package app.babylog.ui.trends

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
import app.babylog.domain.TrendBucket
import app.babylog.domain.TrendMetric
import app.babylog.domain.TrendSeries
import app.babylog.domain.UnitPreferences
import app.babylog.domain.formatTrendNumber
import app.babylog.domain.trendDisplayValue
import app.babylog.ui.charts.X_AXIS_GUTTER
import app.babylog.ui.charts.Y_AXIS_GUTTER
import app.babylog.ui.charts.ticksWithDistinctLabels
import app.babylog.ui.charts.yRangeFor
import app.babylog.ui.charts.yTicks
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A per-day trend chart: days along the bottom, one line per child.
 *
 * Lines rather than bars, deliberately. Both children are on the chart at once — that is the default
 * mode of this app — and two bars per day across thirty days is 60 bars in the width of a phone, each
 * too thin to read or tap. Two lines stay legible at 7 and at 30 days, and the app's other chart
 * (Growth) is already a line chart, so a parent learns one visual language.
 *
 * The axis maths comes from `ui/charts/ChartAxis.kt`, shared with Growth. What is new here is only the
 * x axis, which counts DAYS rather than months, and the zero floor: eight nappies on a Tuesday and none
 * on a Wednesday is a shape that must start at zero, where a weight curve must not.
 *
 * A day with no records is drawn as zero rather than as a gap. See `domain/Trends.kt` for why the app
 * refuses to guess the difference between "no nappies" and "nobody logged one", and the caption under
 * this chart for how it says so on screen.
 */
@Composable
fun TrendChart(
    series: TrendSeries,
    units: UnitPreferences,
    lineColors: List<Color>,
    accent: Color,
    gridColor: Color,
    axisTextColor: Color,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = axisTextColor, fontSize = 10.sp)

    val plotted = series.lines.map { line ->
        line.points.map { point -> trendDisplayValue(series.metric, point.value, units) }
    }
    // Zero is included in the range on purpose: a chart of nappies per day that floats between 4 and 9
    // makes a normal week look like a crisis.
    val yRange = yRangeFor(plotted.flatten(), floorAtZero = true)

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(TREND_CHART_HEIGHT)) {
            drawTrends(
                series = series,
                units = units,
                plotted = plotted,
                yRange = yRange,
                lineColors = lineColors,
                accent = accent,
                gridColor = gridColor,
                textMeasurer = textMeasurer,
                tickStyle = tickStyle,
                zone = zone,
            )
        }
    }
}

/**
 * Shorter than Growth's 260dp on purpose: this is one of six tabs a parent flicks between, and a chart
 * that fills the screen is a chart whose summary card is below the fold.
 */
private val TREND_CHART_HEIGHT = 200.dp

private fun DrawScope.drawTrends(
    series: TrendSeries,
    units: UnitPreferences,
    plotted: List<List<Double>>,
    yRange: ClosedFloatingPointRange<Float>,
    lineColors: List<Color>,
    accent: Color,
    gridColor: Color,
    textMeasurer: TextMeasurer,
    tickStyle: TextStyle,
    zone: ZoneId,
) {
    val left = Y_AXIS_GUTTER.toPx()
    val bottom = size.height - X_AXIS_GUTTER.toPx()
    val right = size.width
    val top = 0f
    val plotWidth = (right - left).coerceAtLeast(1f)
    val plotHeight = (bottom - top).coerceAtLeast(1f)
    val bucketCount = series.buckets.size

    // A single day has no span to interpolate across, so its one point is centred rather than mapped to
    // NaN. Seven is the smallest range the UI offers, but the maths must not depend on that.
    fun x(index: Int): Float =
        if (bucketCount <= 1) left + plotWidth / 2f
        else left + index.toFloat() / (bucketCount - 1) * plotWidth

    fun y(displayValue: Double): Float {
        val span = (yRange.endInclusive - yRange.start).takeIf { it > 0f } ?: 1f
        return bottom - ((displayValue.toFloat() - yRange.start) / span) * plotHeight
    }

    // ---- grid and y labels ---------------------------------------------------------------------
    // A count axis is ticked in whole things: "0.4 meals" is a category error, not extra precision.
    // Found on the phone in light mode, where a one-meal day produced a 0.2-step axis. Every axis is then
    // filtered so no two gridlines can read the same number — `0.1 / 0 / 0` was what a volume series with
    // nothing recorded drew before `yRangeFor` stopped inventing a span for it.
    val ticks = ticksWithDistinctLabels(
        ticks = if (series.metric == TrendMetric.COUNT) integerTicks(yRange) else yTicks(yRange),
        label = ::formatTrendNumber,
    )
    for (tick in ticks) {
        val tickY = y(tick)
        drawLine(color = gridColor, start = Offset(left, tickY), end = Offset(right, tickY), strokeWidth = 1f)
        val label = textMeasurer.measure(formatTrendNumber(tick), tickStyle)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = (left - label.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                y = tickY - label.size.height / 2f,
            ),
        )
    }

    // ---- the lines, one per child --------------------------------------------------------------
    // The second child's line is DASHED, not just a lighter shade. Two shades of one hue were not enough
    // to tell the twins apart on the phone — the accent family is the design contract's and a second hue
    // is not available — so the distinction is carried by shape, exactly as the fever marker is.
    series.lines.forEachIndexed { lineIndex, _ ->
        val color = lineColors.getOrElse(lineIndex) { accent }
        val values = plotted.getOrElse(lineIndex) { emptyList() }
        val path = Path()
        values.forEachIndexed { index, value ->
            val px = x(index)
            val py = y(value)
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (values.size >= 2) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.5f, pathEffect = linePathEffect(lineIndex)),
            )
        }
        // A dot per day, not only per recorded day: the dot is where the line's 0 came from, and a
        // missing dot beside a 0 would imply the app had data it does not have.
        values.forEachIndexed { index, value ->
            drawCircle(color = color, radius = 3f, center = Offset(x(index), y(value)))
        }
    }

    // ---- x labels ------------------------------------------------------------------------------
    // Anchored to the newest day, so "today" is always named and the gaps between labels are uniform.
    // Appending the last day to a step of 2 put two labels one day apart — 21dp apart on a phone, for a
    // label that measures ~33dp — and they overlapped. The unit test caught it, not the phone.
    for (index in labelIndices(bucketCount)) {
        val label = textMeasurer.measure(
            bucketLabel(series.buckets[index], series.range.bucket, zone),
            tickStyle,
        )
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = (x(index) - label.size.width / 2f).coerceIn(0f, right - label.size.width),
                y = bottom + 6.dp.toPx(),
            ),
        )
    }
}

/**
 * Where the date labels go, counted back from the newest day.
 *
 * Every day up to a week, then every two, then every five — chosen from the label's own width rather
 * than a count. A `d MMM` label measures roughly 33dp at 10sp and the plot is ~294dp wide on a 360dp
 * phone, so one label per day at a week sits ~49dp apart and reads cleanly, while every two at a
 * fortnight and every five at a month keep the closest pair from colliding.
 */
internal fun labelIndices(dayCount: Int): List<Int> {
    if (dayCount <= 0) return emptyList()
    val step = labelStep(dayCount)
    val out = mutableListOf<Int>()
    var index = dayCount - 1
    while (index >= 0) {
        out.add(index)
        index -= step
    }
    return out.reversed()
}

internal fun labelStep(bucketCount: Int): Int = when {
    bucketCount <= 7 -> 1
    bucketCount <= 14 -> 2
    bucketCount <= 31 -> 5
    // A year of weeks or all time in months cannot use a fixed step: five would draw 10+ labels into
    // ~294dp. Past a month the step grows with the count, keeping at most six labels on the axis.
    else -> kotlin.math.ceil(bucketCount / 6.0).toInt()
}

/** Solid for the first child, dashed for the second. Shared with the legend so the two cannot disagree. */
internal fun linePathEffect(lineIndex: Int) = when (lineIndex) {
    0 -> null
    else -> PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
}

/**
 * Gridlines for a count, in whole numbers.
 *
 * A count's natural step can fall below one (a single meal in a 14-day window gives `yTicks` a 0.2 step),
 * and an axis reading `0.4` above a bar of one meal is a number that cannot exist. When the whole-number
 * ticks collapse to fewer than two lines the axis falls back to 0 and the rounded maximum, so a chart
 * always has a scale rather than a box.
 */
internal fun integerTicks(range: ClosedFloatingPointRange<Float>): List<Double> {
    val whole = yTicks(range).filter { it == kotlin.math.floor(it) && it >= 0.0 }
    if (whole.size >= 2) return whole
    val top = kotlin.math.ceil(range.endInclusive.toDouble()).coerceAtLeast(1.0)
    return listOf(0.0, top)
}

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

internal fun dayLabel(dayStart: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate().format(dayFormat)

/**
 * The x label for one bucket.
 *
 * A day and a week name a date (`12 Oct`), because where a week starts is what the point means. A month
 * names only the month: `1 Oct` under a monthly bucket would read as a single day's total.
 */
internal fun bucketLabel(bucketStart: Long, bucket: TrendBucket, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(bucketStart).atZone(zone).toLocalDate()
    return when (bucket) {
        TrendBucket.DAY, TrendBucket.WEEK -> date.format(dayFormat)
        TrendBucket.MONTH -> date.format(monthFormat)
    }
}
