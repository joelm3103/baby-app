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
import androidx.compose.foundation.lazy.LazyListState
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
import app.babylog.ui.home.FeverBadge
import app.babylog.ui.common.activityPattern
import app.babylog.ui.common.glyphPainter
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
    onShowAll: () -> Unit,
    onShowNone: () -> Unit,
    onSelectChild: (ChildSelection) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
) {
    val colors = BabyLogTheme.colors

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            ScreenHeader(
                title = "Summary",
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
            FilterChips(
                hidden = hidden,
                onToggle = onToggleFilter,
                onShowAll = onShowAll,
                onShowNone = onShowNone,
            )
        }

        if (state.totals.isEmpty()) {
            item { NoActivitiesCard() }
        }

        items(state.totals, key = { it.type.name }) { total ->
            TotalCard(total = total, showPerChild = state.showPerChild)
        }
    }
}

/**
 * The filter row: one bulk chip, then one chip per activity, selected = shown. Multi-select rather than
 * a single choice, because wanting "just feeds and nappies" is normal and wanting exactly one is not.
 *
 * The bulk chip is FIRST and is filled with `navActive` — the same fill the selected range chip above it
 * uses — because it is not an activity: those carry their own hue when on and `surfaceRaised` when off,
 * and a seventh pill in that row would read as a seventh activity. Its label and its action both come
 * from [bulkFilterAction], so the partial-selection rule is pinned by a test rather than decided here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(
    hidden: Set<EventType>,
    onToggle: (EventType) -> Unit,
    onShowAll: () -> Unit,
    onShowNone: () -> Unit,
) {
    val colors = BabyLogTheme.colors
    val bulk = bulkFilterAction(hidden)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceChip(
            label = bulk.label,
            selected = true,
            accent = colors.navActive,
            onClick = { if (bulk.showAll) onShowAll() else onShowNone() },
        )

        // Domain order, so the chips read in the same sequence as the cards they control.
        EventType.entries.sortedBy { type -> app.babylog.domain.EVENT_DISPLAY_ORDER.indexOf(type) }
            .forEach { type ->
                ChoiceChip(
                    label = type.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = type !in hidden,
                    // The activity's OWN accent, matching the card this chip controls, so the filter row
                    // reads as the same colour code as the screen below it.
                    accent = colors.accentFor(type),
                    // And its glyph, so the chip is the same picture as the card it shows and hides.
                    icon = type.glyphPainter(),
                    onClick = { onToggle(type) },
                )
            }
    }
}

/**
 * Shown when every activity is filtered out. A CARD rather than a bare line, because this is the region
 * the cards live in and a stray paragraph there reads as a section that failed to load.
 *
 * No "show everything" button: the control that caused this state is directly above it, and a second
 * control for one action is a second thing to learn. It is also why the copy does not name the chip's
 * current label — that label reads `All` in exactly this state, and hard-coding it here would be the
 * one place in the app that could disagree with it.
 */
@Composable
private fun NoActivitiesCard() {
    val colors = BabyLogTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surfaceRaised)
            .padding(Spacing.cardPadding),
    ) {
        Text(
            text = "No activities selected",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        // A plain surface, not a tinted one, so `textSecondary` is the right token here: it is the tinted
        // cards that must use `textOnCard`.
        Text(
            text = "Pick at least one above.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
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
            // The same glyph pattern as the dashboard card it totals, so the Summary card and the
            // home card are recognisably the same activity.
            .activityPattern(total.type, accent)
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
                    // A fever reading leaves textPrimary here for the same reason it does on the dashboard,
                    // and with the same token: these cards are tinted with the accent at 26%, so `danger`
                    // is not legible on them (it measures 3.76:1 dark / 4.28:1 light).
                    color = if (total.fever) colors.feverOnCard else colors.textPrimary,
                )
                total.unit?.let { unit ->
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (total.fever) colors.feverOnCard else colors.textPrimary,
                    )
                }
                if (total.fever) {
                    // A filled pill, not a tinted word: the colour alone was invisible on the tinted
                    // card, which is the owner's report from the first device pass.
                    FeverBadge(Modifier.padding(start = 8.dp, bottom = 4.dp))
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
