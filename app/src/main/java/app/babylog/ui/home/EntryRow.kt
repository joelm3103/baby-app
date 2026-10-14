package app.babylog.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.babylog.domain.BabyEvent
import app.babylog.domain.UnitPreferences
import app.babylog.domain.clockTime
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import java.time.ZoneId

/**
 * One logged entry: which child (Both mode only), what it was, and when.
 *
 * Shared by the cards and the "See all" list so the two renderings of the same row cannot drift. The
 * label is weighted and the time is pinned right with its own start padding, so a long label — a
 * solids meal with three foods, a temperature reading — ellipsises instead of pushing the time off the
 * card, which is what `SpaceBetween` alone allowed.
 *
 * Tap edits, long-press deletes: the long-press is the destructive one, so it is deliberately not the
 * single tap, and the list that offers it says so in its header.
 *
 * [onCard] picks the meta-text token for the surface it is drawn on: `textOnCard` on a tinted card,
 * `textSecondary` on the plain background of the full-entry list. They are not interchangeable — the
 * card tint is exactly why the two exist.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryRow(
    event: BabyEvent,
    units: UnitPreferences,
    initials: String?,
    showChild: Boolean,
    zone: ZoneId,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    onCard: Boolean = true,
) {
    val colors = BabyLogTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = Spacing.rowVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Which child, then how much. In Both mode two rows could otherwise read identically: the
            // brand is the same on every bottle and says nothing.
            if (showChild) {
                ChildBadge(initials = initials ?: "?", size = Spacing.rowBadge)
            }
            Text(
                text = recentRowLabel(event, units),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = clockTime(event.startedAt, zone),
            style = MaterialTheme.typography.bodySmall,
            color = if (onCard) colors.textOnCard else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
