package app.babylog.ui.growth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * Growth: the percentile chart for one measurement, the callout for the newest one, and the log of
 * every measurement behind it.
 *
 * Drawn as a sibling of the `Scaffold` (like `EventListOverlay`), so it covers the bottom navigation bar
 * and carries its own `BackHandler`; the bottom nav stays at three items, as the design contract
 * requires.
 *
 * The dataset identity line is not decoration: a percentile a parent might act on must say which tables
 * produced it, and the Project's own revision date has not moved since 2006.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowthScreen(
    state: GrowthUiState,
    onSelectMetric: (GrowthMetric) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BabyLogTheme.colors
    val accent = colors.accentFor(EventType.GROWTH)
    val chart = state.chart

    BackHandler(enabled = true, onBack = onDismiss)

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardFor(accent))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = colors.labelFor(accent),
                    )
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "Growth",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.labelFor(accent),
                    )
                    if (state.childLabel.isNotEmpty()) {
                        Text(
                            text = state.childLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.labelFor(accent),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
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
                            text = "Reading the growth reference…",
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
                            xMaxDays = chart.xMaxDays,
                            unit = chart.unit,
                            accent = accent,
                            gridColor = colors.divider,
                            axisTextColor = colors.textSecondary,
                            markerRingColor = colors.textPrimary,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            text = "Age in months · ${chart.unit.label} · ${chart.referenceLabel}",
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
                        text = "Percentiles are read from the bundled WHO reference and are not " +
                            "medical advice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
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
                    append(" · ")
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
            text = "${chart.unit.format(marker.value)} ${chart.unit.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Text(
            text = marker.percentile?.let { " · ${percentileLabel(it)}" }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.labelFor(accent),
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = measuredOnLabel(marker.measuredOn),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
