package app.babylog.ui.trends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.babylog.domain.displayName
import app.babylog.domain.EVENT_DISPLAY_ORDER
import app.babylog.domain.EventType
import app.babylog.domain.TrendRange
import app.babylog.ui.growth.GrowthChartPane
import app.babylog.ui.growth.GrowthMetric
import app.babylog.ui.growth.GrowthUiState
import app.babylog.ui.home.ChoiceChip
import app.babylog.ui.common.glyphPainter
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.accentFor

/**
 * Trends: one tab per activity, so every chart in the app has one home.
 *
 * A chart is something a parent goes to look at, not something to trip over while logging a feed, which
 * is why none of this lives on the Activity cards.
 *
 * Five of the six tabs chart a per-day quantity (bottle volume, solids, diapers, sleep, and Growth's
 * percentiles, which are plotted against age instead of days). The sixth, Health, is a dated table
 * rather than a chart — a temperature is a reading, not a daily amount, and averaging readings would
 * invent a number nobody should act on.
 *
 * The tab row is scrollable because six activity tabs do not fit a phone's width, and shortening the
 * labels to fit would be worse than scrolling them.
 */
@Composable
fun TrendsScreen(
    growth: GrowthUiState,
    onSelectMetric: (GrowthMetric) -> Unit,
    onEnsureLoaded: () -> Unit,
    trends: TrendsUiState,
    onSelectRange: (TrendRange) -> Unit,
    onSelectType: (EventType) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val colors = BabyLogTheme.colors
    val type = trends.type

    // The tab row sits ABOVE the scrolling content, so the shell's content padding never reached it and the
    // chips were drawn from y=0 — under the 94px status bar, which made the top of every chip untappable.
    // Found on the S23: tapping a chip's centre did nothing, and only the strip below the status bar worked.
    val layoutDirection = LocalLayoutDirection.current
    val rowPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
    )
    // The pane's padding drops the top, which the tab row has already consumed.
    val panePadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding(),
    )

    // The growth reference is ~490 KB of JSON, parsed only when this screen is actually opened rather than
    // on every launch.
    LaunchedEffect(Unit) { onEnsureLoaded() }

    // One scroll state is shared by every pane and owned by the shell, which is what makes the top scrim
    // follow the screen. Switching tabs therefore has to return to the top: Growth's list is long and a
    // four-item chart pane carrying its scroll offset would open halfway down a chart that has not been
    // seen yet.
    LaunchedEffect(type) { listState.scrollToItem(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rowPadding)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EVENT_DISPLAY_ORDER.forEach { option ->
                ChoiceChip(
                    label = option.displayName(),
                    selected = option == type,
                    accent = colors.accentFor(option),
                    // The same glyph the activity's card carries, so the tab and the card agree.
                    icon = option.glyphPainter(),
                    onClick = { onSelectType(option) },
                )
            }
        }

        when {
            type == EventType.GROWTH -> GrowthChartPane(
                state = growth,
                onSelectMetric = onSelectMetric,
                listState = listState,
                contentPadding = panePadding,
                // The remaining height under the tab row, so the pane cannot overlap the chips.
                modifier = Modifier.weight(1f),
            )

            type == EventType.HEALTH -> {
                val health = trends.health
                if (health == null) {
                    PaneLoading(modifier = Modifier.weight(1f), padding = panePadding)
                } else {
                    HealthTrendPane(
                        health = health,
                        range = trends.range,
                        childLabel = trends.childLabel,
                        zone = trends.zone,
                        onSelectRange = onSelectRange,
                        accent = colors.accentFor(type),
                        contentPadding = panePadding,
                        listState = listState,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            else -> {
                val series = trends.series
                if (series == null) {
                    PaneLoading(modifier = Modifier.weight(1f), padding = panePadding)
                } else {
                    TrendPane(
                        series = series,
                        units = trends.units,
                        childLabel = trends.childLabel,
                        zone = trends.zone,
                        onSelectRange = onSelectRange,
                        accent = colors.accentFor(type),
                        contentPadding = panePadding,
                        listState = listState,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The one frame between opening a tab and its Flow's first emission, so there is no empty flicker. */
@Composable
private fun PaneLoading(modifier: Modifier, padding: PaddingValues) {
    val colors = BabyLogTheme.colors
    Column(modifier = modifier.fillMaxWidth().padding(padding)) {
        Text(
            text = "Reading the log\u2026",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}
