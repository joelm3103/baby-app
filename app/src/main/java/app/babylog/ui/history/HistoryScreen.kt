package app.babylog.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.babylog.domain.ActivityTotal
import app.babylog.domain.ChildSelection
import app.babylog.domain.EventType
import app.babylog.domain.HistoryRange
import app.babylog.ui.home.ChoiceChip
import app.babylog.ui.home.ScreenHeader
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.displayName
import app.babylog.ui.theme.labelFor
import java.time.LocalDate
import java.time.ZoneId

/**
 * History: how the selected child (or both) has been doing over a range.
 *
 * Same header and switcher as Activity on purpose — the design contract calls the header "the app's
 * identity" and says the child switcher drives both screens, because the mental model is "who am I
 * looking at", not "which tab".
 *
 * Stateless: totals arrive already computed and already filtered, so this file is layout only.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    hidden: Set<EventType>,
    onSelectRange: (HistoryRange) -> Unit,
    onToggleFilter: (EventType) -> Unit,
    onSelectChild: (ChildSelection) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
    today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
) {
    val colors = BabyLogTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            ScreenHeader(
                title = "History",
                children = state.children,
                selection = state.selection,
                onSelectChild = onSelectChild,
                onOpenSettings = onOpenSettings,
                today = today,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryRange.entries.forEach { range ->
                    ChoiceChip(
                        label = range.label,
                        selected = state.range == range,
                        accent = colors.navActive,
                        onClick = { onSelectRange(range) },
                    )
                }
            }
        }

        item {
            FilterChips(hidden = hidden, onToggle = onToggleFilter)
        }

        if (state.totals.isEmpty()) {
            item {
                Text(
                    text = "No activities selected. Turn one back on above to see its totals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        items(state.totals, key = { it.type.name }) { total ->
            TotalCard(total = total, showPerChild = state.showPerChild)
        }
    }
}

/**
 * One chip per activity, selected = shown. Multi-select rather than a single choice, because wanting
 * "just feeds and nappies" is normal and wanting exactly one is not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(hidden: Set<EventType>, onToggle: (EventType) -> Unit) {
    val colors = BabyLogTheme.colors
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Domain order, so the chips read in the same sequence as the cards they control.
        EventType.entries.sortedBy { type -> app.babylog.domain.EVENT_DISPLAY_ORDER.indexOf(type) }
            .forEach { type ->
                ChoiceChip(
                    label = type.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = type !in hidden,
                    accent = colors.navActive,
                    onClick = { onToggle(type) },
                )
            }
    }
}

/**
 * One activity's totals. Tinted with its OWN accent via `cardFor`, exactly like the dashboard, and the
 * label is `labelFor(accent)` because the raw accent is not legible on its own tint. Supporting rows use
 * `textOnCard`, never `textSecondary`.
 */
@Composable
private fun TotalCard(total: ActivityTotal, showPerChild: Boolean) {
    val colors = BabyLogTheme.colors
    val accent = colors.accentFor(total.type)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.cardFor(accent))
            .padding(Spacing.cardPadding),
    ) {
        Text(
            text = total.type.displayName(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.labelFor(accent),
        )
        Spacer(Modifier.height(8.dp))

        if (total.empty) {
            Text(
                text = "Nothing logged in this range",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textOnCard,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total.headline,
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.textPrimary,
                )
                total.unit?.let { unit ->
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                }
            }
        }

        total.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(row.label, style = MaterialTheme.typography.bodySmall, color = colors.textOnCard)
                Text(row.value, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
            }
        }

        // Only in Both mode: a single-child selection makes this line a restatement of the headline.
        if (showPerChild && total.perChild.isNotEmpty()) {
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = 10.dp))
            total.perChild.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodySmall, color = colors.textOnCard)
                    Text(row.value, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                }
            }
        }
    }
}
