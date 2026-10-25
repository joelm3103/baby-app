package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.babylog.domain.UpcomingReminder
import app.babylog.domain.clockTime
import app.babylog.domain.displayName
import app.babylog.domain.elapsedCoarse
import app.babylog.ui.LocalTimeFormat
import app.babylog.ui.common.glyphRes
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * The "Upcoming reminders" card: what the alarms are actually going to do, on the screen the parent opens
 * first.
 *
 * It reads the SAME [app.babylog.domain.nextReminderAt] the scheduler uses, so it cannot promise a reminder the
 * alarm will not make, and it shows nothing when nothing is armed — an empty card would be a box saying
 * "nothing", which is what its absence already says.
 *
 * Rows are drawn from the activity's own glyph and accent, like every other card, so a parent recognises the
 * activity before reading it. A row whose time has passed drops out on the next tick rather than sitting there
 * reading "in 0m": once a reminder has fired, the notification is the thing that is true.
 */
@Composable
fun UpcomingRemindersCard(
    reminders: List<UpcomingReminder>,
    /**
     * Which activity's reminder to open, and for WHICH CHILD.
     *
     * The child matters: a row that says `Bottle feed · Second Twin` must open Second Twin's rule, not the first
     * child's. The first version dropped it and every row opened the first twin's reminder.
     */
    onOpen: (type: app.babylog.domain.EventType, childId: String?) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = BabyLogTheme.colors

    // One ticker for the whole card, every half minute. Not every second: the countdown is in minutes, so a
    // per-second recomposition would be a wake-up per second to redraw the same text.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }

    val live = reminders.filter { it.at > now }
    if (live.isEmpty()) return
    // Expanded by default: the card exists to be read at a glance. Collapsing is remembered for the session only -
    // a parent who folded it away once should not have to fold it again after a relaunch, but a preference for it
    // would be a setting nobody asked for.
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surfaceRaised)
            .padding(Spacing.cardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(bottom = if (expanded) 4.dp else 0.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                // The count is the number of rows, so the card can never say "(3)" over two of them.
                text = "Upcoming reminders (${live.size})",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                // Up when open, down when folded: the chevron points at what the tap will do.
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.textSecondary,
            )
        }
        if (expanded) {
            live.forEachIndexed { index, reminder ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                UpcomingRow(
                    reminder = reminder,
                    now = now,
                    zone = zone,
                    onClick = { onOpen(reminder.type, reminder.childId) },
                )
            }
        }
    }
}

@Composable
private fun UpcomingRow(
    reminder: UpcomingReminder,
    now: Long,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val colors = BabyLogTheme.colors
    val accent = colors.accentFor(reminder.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(reminder.type.glyphRes()),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        // The activity, then whose it is when the twins are on different rules. The name gives way first, so a
        // long child name cannot push the time off the row.
        Text(
            text = listOfNotNull(
                reminder.type.displayName(),
                reminder.childName,
            ).joinToString(" \u00b7 "),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            // No "in": with a child's name on the row as well, "in " was the difference between fitting and being
            // ellipsised, and `4:45 PM (2h 59m)` reads the same.
            text = "${clockTime(reminder.at, zone, LocalTimeFormat.current)} " +
                "(${elapsedCoarse(reminder.at - now)})",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
        )
    }
}

/** Half a minute: the countdown is in minutes, so anything faster redraws the same text. */
private const val TICK_MS = 30_000L
