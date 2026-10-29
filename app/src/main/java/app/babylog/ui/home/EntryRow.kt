package app.babylog.ui.home

import app.babylog.ui.LocalTimeFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.babylog.domain.BabyEvent
import app.babylog.domain.TemperatureBand
import app.babylog.domain.UnitPreferences
import app.babylog.domain.clockTime
import app.babylog.ui.common.glyphPainter
import app.babylog.ui.common.rememberHaptics
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.accentFor
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
 *
 * [band] adds the band's pill for a flagged reading (Low, Mild or Fever). It is a marker on the row
 * rather than a word in the label, because the label is the first thing to be ellipsised when a row is
 * narrow — and losing the marker to an ellipsis is exactly how a fever gets read past.
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
    band: TemperatureBand? = null,
    /** `id -> name` for the solids catalogue: a row shows what was eaten, not its slug. */
    foodNames: Map<String, String> = emptyMap(),
) {
    // The diaper's colour, replayed as the same small circle the sheet's selector draws. Read from the
    // event rather than passed in, so a caller cannot forget it and leave a row that disagrees with the
    // headline above it - which is the only way a row and its card ever drift apart.
    val dotColors = diaperColors(event)
    val colors = BabyLogTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    haptics.tap()
                    onTap()
                },
                onLongClick = {
                    // A long press starts a delete, so it gets the sustained buzz that says so.
                    haptics.longPress()
                    onLongPress()
                },
            )
            .padding(vertical = Spacing.rowVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The activity's own glyph, in its raw accent. Derived from the event rather than passed in,
            // for the same reason the diaper dots are: a caller that forgot it would draw a row that
            // disagrees with the card it sits on.
            Icon(
                painter = event.type.glyphPainter(),
                contentDescription = null,
                tint = colors.accentFor(event.type),
                modifier = Modifier.size(14.dp),
            )
            // Which child, then how much. In Both mode two rows could otherwise read identically: the
            // brand is the same on every bottle and says nothing.
            if (showChild) {
                ChildBadge(initials = initials ?: "?", size = Spacing.rowBadge)
            }
            Text(
                text = recentRowLabel(event, units, foodNames),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The label gives way first, so the pill and the time both survive a narrow row.
                modifier = Modifier.weight(1f, fill = false),
            )
            DiaperColorDots(colors = dotColors, modifier = Modifier.padding(start = 6.dp))
            if (band != null) TemperatureBadge(band)
        }
        Text(
            text = clockTime(event.startedAt, zone, LocalTimeFormat.current),
            style = MaterialTheme.typography.bodySmall,
            color = if (onCard) colors.textOnCard else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
