package app.babylog.ui.trends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.babylog.domain.EVENT_DISPLAY_ORDER
import app.babylog.domain.EventType
import app.babylog.ui.growth.GrowthChartPane
import app.babylog.ui.growth.GrowthMetric
import app.babylog.ui.growth.GrowthUiState
import app.babylog.ui.home.ChoiceChip
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.accentFor

/**
 * Trends: one tab per activity, so every chart in the app has one home.
 *
 * Growth's percentile chart is the first tab that works, and it is here rather than on the Activity card
 * because a chart is something you go to look at, not something to trip over while logging a feed. The
 * other activities keep an honest placeholder naming Phase 12 rather than an empty axis: a chart of
 * nothing teaches nothing, which is why Trends was planned last in the first place.
 *
 * The tab row is scrollable because six activity tabs do not fit a phone's width, and shortening the
 * labels to fit would be worse than scrolling them.
 */
@Composable
fun TrendsScreen(
    state: GrowthUiState,
    onSelectMetric: (GrowthMetric) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val colors = BabyLogTheme.colors
    // Opens on Growth: the only activity with a chart today, so it is where a parent wants to land.
    var selected by rememberSaveable { mutableStateOf(EventType.GROWTH.name) }
    val type = EventType.entries.firstOrNull { it.name == selected } ?: EventType.GROWTH

    // The growth reference is ~490 KB of JSON. Loading it only when this tab is actually composed keeps
    // the launch path free of a parse most sessions never need.
    LaunchedEffect(Unit) { onSelectMetric(GrowthMetric.WEIGHT) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EVENT_DISPLAY_ORDER.forEach { option ->
                ChoiceChip(
                    label = option.label,
                    selected = option == type,
                    accent = colors.accentFor(option),
                    onClick = { selected = option.name },
                )
            }
        }

        if (type == EventType.GROWTH) {
            GrowthChartPane(
                state = state,
                onSelectMetric = onSelectMetric,
                listState = listState,
                contentPadding = contentPadding,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                Text(
                    text = "${type.label} trend",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Charts for ${type.label.lowercase()} arrive in Phase 12, once there is enough " +
                        "data logged for the shape of it to be worth drawing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
