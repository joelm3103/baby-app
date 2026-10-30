package app.babylog.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.babylog.domain.displayName
import app.babylog.domain.TrendLine
import app.babylog.domain.TrendRange
import app.babylog.domain.TrendSeries
import app.babylog.domain.UnitPreferences
import app.babylog.domain.formatTrendTotal
import app.babylog.domain.trendAxisUnit
import app.babylog.domain.trendHeadline
import app.babylog.domain.trendHeadlineUnit
import app.babylog.ui.home.ChoiceChip
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.labelFor
import java.time.ZoneId

/**
 * One activity's per-day chart: the legend, the sentence about empty days, and the summary card.
 *
 * Laid out as `LazyColumn` items, like every other screen here, so the top scrim and the bottom inset
 * work the same way they do everywhere else. The window chips are the first item rather than a pinned
 * row: this pane has no header of its own, and a pinned second row would eat the height a chart needs.
 */
@Composable
fun TrendPane(
    series: TrendSeries,
    units: UnitPreferences,
    childLabel: String,
    zone: ZoneId,
    onSelectRange: (TrendRange) -> Unit,
    accent: Color,
    contentPadding: PaddingValues,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    // Two shades of the activity's own hue, not a second hue: the accent family is the design contract's,
    // and a per-child palette would invent one. Growth's trajectory lines already do exactly this.
    val lineColors = listOf(accent, colors.labelFor(accent))

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item { RangeChips(range = series.range, onSelectRange = onSelectRange) }

        if (!series.hasData) {
            item {
                Text(
                    text = "No ${series.type.displayName().lowercase()} logged ${series.range.windowText}. " +
                        "The chart fills in as the log does.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            return@LazyColumn
        }

        item {
            TrendChart(
                series = series,
                units = units,
                lineColors = lineColors,
                accent = accent,
                gridColor = colors.divider,
                axisTextColor = colors.textSecondary,
                zone = zone,
            )
        }

        item {
            Column {
                if (series.multipleChildren) {
                    // A sample of the LINE, not a dot: the second child is told apart by its dashes as much
                    // as by its shade, and a swatch that showed only the colour would hide half the cue.
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        series.lines.forEachIndexed { index, line ->
                            LegendEntry(
                                color = lineColors.getOrElse(index) { accent },
                                lineIndex = index,
                                label = line.childName,
                            )
                        }
                    }
                }
                Text(
                    text = "${series.type.displayName()} · ${series.range.windowText} · " +
                        trendAxisUnit(series.metric, units, series.range.bucket) +
                        if (childLabel.isNotEmpty()) " \u00b7 $childLabel" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = if (series.multipleChildren) 8.dp else 2.dp),
                )
                Text(
                    // Said plainly, because a bucket nobody logged is drawn as a zero and the chart
                    // cannot tell that apart from a bucket with genuinely nothing.
                    text = "${series.range.bucket.pluralCapitalised} with no records count as zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item { TrendSummaryCard(series = series, units = units, zone = zone, accent = accent) }
    }
}

/**
 * The window chips. Shared by every pane, so the three ranges are in the same place at the same size
 * whatever is being looked at.
 */
@Composable
fun RangeChips(range: TrendRange, onSelectRange: (TrendRange) -> Unit) {
    val colors = BabyLogTheme.colors
    // FlowRow, not Row: five chips (`1W · 2W · 1M · 1Y · All`) are a shape that fits a 411dp phone and
    // not a 360dp one, and a range row that silently cuts `All` off is a control the parent cannot reach.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrendRange.entries.forEach { option ->
            ChoiceChip(
                label = option.label,
                selected = range == option,
                // navActive, exactly as Summary's range chips use: a range is not an activity, so it must
                // not borrow one of the six hues.
                accent = colors.navActive,
                // `2W` aloud is not "2 weeks", and this chip's only label is the abbreviation.
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = option.spoken
                },
                onClick = { onSelectRange(option) },
            )
        }
    }
}

@Composable
private fun LegendEntry(color: Color, lineIndex: Int, label: String) {
    val colors = BabyLogTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Drawn rather than a rounded Box, so the sample carries the same dash as the line it names.
        Canvas(modifier = Modifier.size(width = 22.dp, height = 10.dp)) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = 4f,
                pathEffect = linePathEffect(lineIndex),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * One block per child, so Both mode cannot present a merged average as if it spoke for both — the same
 * rule the Health card's columns and Growth's callouts follow.
 *
 * The big value is the AVERAGE DAY, not the total: "how much does he usually take" is the question a
 * chart answers, and a 14-day total is a number nobody acts on. It is averaged over every day in the
 * window, so a day off the log pulls it down rather than being quietly skipped.
 */
@Composable
private fun TrendSummaryCard(
    series: TrendSeries,
    units: UnitPreferences,
    zone: ZoneId,
    accent: Color,
) {
    val colors = BabyLogTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.cardFor(accent))
            .padding(Spacing.cardPadding),
    ) {
        Text(
            text = series.type.displayName(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.labelFor(accent),
        )
        series.lines.forEachIndexed { index, line ->
            if (index > 0) {
                HorizontalDivider(
                    color = colors.textOnCard.copy(alpha = 0.25f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            ChildSummary(line = line, series = series, units = units, zone = zone, accent = accent)
        }
    }
}

@Composable
private fun ChildSummary(
    line: TrendLine,
    series: TrendSeries,
    units: UnitPreferences,
    zone: ZoneId,
    accent: Color,
) {
    val colors = BabyLogTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = line.childName,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textOnCard,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = "${line.bucketsRecorded} of ${series.bucketCount} ${series.range.bucket.plural} logged",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textOnCard,
        )
    }
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
        Text(
            text = trendHeadline(series.metric, line.averagePerBucket, units),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.labelFor(accent),
        )
        Text(
            text = "   ${trendHeadlineUnit(series.metric, units, series.range.bucket)}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textOnCard,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    SummaryRow("Total", formatTrendTotal(series.metric, line.total, units))
    line.bestBucket?.let { best ->
        SummaryRow(
            "Best ${series.range.bucket.noun}",
            "${formatTrendTotal(series.metric, best.value, units)} \u00b7 " +
                bucketLabel(best.bucketStart, series.range.bucket, zone),
        )
    }
}

/**
 * Supporting text on a TINTED card uses `textOnCard`, never `textSecondary` — the tint is strong enough
 * that `textSecondary` measures 3.06:1 on it. Same rule the totals cards follow.
 */
@Composable
private fun SummaryRow(label: String, value: String) {
    val colors = BabyLogTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textOnCard,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textOnCard,
        )
    }
}
