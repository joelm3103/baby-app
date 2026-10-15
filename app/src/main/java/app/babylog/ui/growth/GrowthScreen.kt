package app.babylog.ui.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.babylog.domain.EventType
import app.babylog.domain.percentileLabel
import app.babylog.ui.home.ChoiceChip
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.accentFor
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.labelFor

/**
 * The growth percentile chart, as the content of Trends' Growth tab.
 *
 * This was a full-screen overlay reached from the Activity card. The owner moved the chart into Trends,
 * where every activity will eventually have a chart, so it is now a pane inside that screen: no Close
 * button and no `BackHandler`, because the tab and the shell own the navigation.
 *
 * The dataset identity line is not decoration: a percentile a parent might act on must say which tables
 * produced it, and the Project's own revision date has not moved since 2006.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowthChartPane(
    state: GrowthUiState,
    onSelectMetric: (GrowthMetric) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val colors = BabyLogTheme.colors
    val accent = colors.accentFor(EventType.GROWTH)
    val chart = state.chart

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GrowthMetric.entries.forEach { option ->
                    ChoiceChip(
                        label = option.label,
                        selected = state.metric == option,
                        accent = accent,
                        onClick = { onSelectMetric(option) },
                    )
                }
            }
        }

        if (chart == null) {
            item {
                Text(
                    text = "Reading the growth reference\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            return@LazyColumn
        }

        if (!chart.hasData) {
            item {
                Text(
                    text = "No ${chart.metric.label.lowercase()} measurements yet. " +
                        "Use + Add measurement on the Growth card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            item {
                PercentileChart(
                    curves = chart.curves,
                    markers = chart.markers,
                    trajectories = chart.trajectories,
                    // Two shades from the activity's own hue rather than a second hue: the accent family
                    // is the design contract's, and a per-child palette would invent one.
                    trajectoryColors = listOf(accent, colors.labelFor(accent)),
                    xMaxDays = chart.xMaxDays,
                    unit = chart.unit,
                    accent = accent,
                    gridColor = colors.divider,
                    axisTextColor = colors.textSecondary,
                    markerRingColor = colors.textPrimary,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = buildString {
                        append("Age in months \u00b7 ${chart.unit.label} \u00b7 ${chart.referenceLabel}")
                        if (chart.trajectories.isNotEmpty()) {
                            append(" \u00b7 the line is ")
                            append(chart.trajectories.joinToString(" and ") { it.badge })
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            item {
                chart.latest?.let { Callout(it, chart.unit, accent) }
            }

            item {
                Text(
                    text = chart.identityLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                Text(
                    text = "Measurements",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }

            items(chart.markers) { marker ->
                MeasurementRow(marker, chart, accent)
            }
        }

        item {
            Text(
                text = "Percentiles are read from the bundled WHO reference and are not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/** The newest measurement: date, value, percentile and age — or why it has no percentile. */
@Composable
private fun Callout(marker: GrowthMarker, unit: ChartUnit, accent: Color) {
    val colors = BabyLogTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardFor(accent))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${unit.format(marker.value)} ${unit.label}",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.labelFor(accent),
            )
            Text(
                text = "   ${measuredOnLabel(marker.measuredOn)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textOnCard,
            )
        }
        Text(
            text = if (marker.percentile != null) {
                "${percentileLabel(marker.percentile)} percentile"
            } else {
                "No percentile"
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.labelFor(accent),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = buildString {
                append(marker.childName)
                if (marker.axis > 0) {
                    append(" \u00b7 ")
                    append(marker.ageLabel)
                    if (marker.wasAdjusted) append(" (adjusted)")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textOnCard,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (marker.percentile == null) {
            Text(
                text = marker.noPercentileReason.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textOnCard,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MeasurementRow(marker: GrowthMarker, chart: GrowthChartData, accent: Color) {
    val colors = BabyLogTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The badge only when more than one child is in scope; a single-child list does not need it.
        if (chart.markers.map { it.childId }.distinct().size > 1) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = marker.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
            }
        }
        Text(
            // Labelled with the metric's code, so a value reads the same way wherever it appears.
            text = "${chart.metric.code} ${chart.unit.format(marker.value)} ${chart.unit.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Text(
            text = marker.percentile?.let { " \u00b7 ${percentileLabel(it)}" } ?: " \u00b7 no percentile",
            style = MaterialTheme.typography.bodyMedium,
            // A row with no percentile says so. The full reason is in the callout when this row is the
            // newest one, and in PRETERM_NOTE.
            color = if (marker.percentile != null) colors.labelFor(accent) else colors.textSecondary,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = measuredOnLabel(marker.measuredOn),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
