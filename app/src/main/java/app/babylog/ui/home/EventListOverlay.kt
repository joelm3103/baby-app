package app.babylog.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import app.babylog.domain.UnitPreferences
import app.babylog.domain.groupByDay
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.displayName
import app.babylog.ui.theme.labelFor
import java.time.ZoneId

/**
 * "See all": every entry of one activity for the current child/both selection, newest first, grouped by
 * day.
 *
 * Full-screen rather than an accordion inside the card, for the reason the logging sheets are: this is
 * drawn as a sibling of the `Scaffold`, so it covers the bottom navigation bar instead of being clipped
 * by it, and `BackHandler` is co-located with the surface it dismisses.
 *
 * It is also the one place deletion lives. Tap edits, long-press deletes — the hint in the header says
 * so, because a gesture nobody can see is a gesture nobody uses.
 */
@Composable
fun EventListOverlay(
    type: EventType,
    entries: List<BabyEvent>,
    units: UnitPreferences,
    childLabel: String,
    childInitials: Map<String, String>,
    showChildBadge: Boolean,
    zone: ZoneId,
    now: Long,
    onDismiss: () -> Unit,
    onEditEntry: (BabyEvent) -> Unit,
    onDeleteEntry: (BabyEvent) -> Unit,
) {
    val colors = BabyLogTheme.colors
    val accent = colors.accentFor(type)

    // The edit sheet is composed after this and owns its own handler, so back closes the sheet first and
    // this list second - the order the screens are stacked in.
    BackHandler(enabled = true, onBack = onDismiss)

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardFor(accent)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    Text(
                        text = type.displayName(),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.labelFor(accent),
                    )
                    // Balances the close button so the title is actually centred.
                    Spacer(Modifier.width(48.dp))
                }
                Text(
                    text = "for $childLabel \u00b7 tap to edit, long-press to delete",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.labelFor(accent),
                )
            }

            val days = groupByDay(entries, zone, now)
            if (days.isEmpty()) {
                Text(
                    text = "Nothing logged for $childLabel yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(Spacing.gutter),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = 4.dp),
            ) {
                days.forEach { day ->
                    item(key = "day-${day.label}") {
                        Text(
                            text = day.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(count = day.events.size, key = { index -> day.events[index].id }) { index ->
                        val event = day.events[index]
                        if (index > 0) HorizontalDivider(color = colors.divider)
                        EntryRow(
                            event = event,
                            units = units,
                            initials = childInitials[event.childId],
                            showChild = showChildBadge,
                            // Same marker as the card: the list and the card must flag the same readings.
                            fever = isFever(event.temperatureC),
                            zone = zone,
                            onTap = { onEditEntry(event) },
                            onLongPress = { onDeleteEntry(event) },
                            onCard = false,
                        )
                    }
                }
            }
        }
    }
}
