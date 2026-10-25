package app.babylog.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.babylog.domain.Child
import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderMode
import app.babylog.domain.displayName
import app.babylog.domain.intervalLabel
import app.babylog.ui.home.ActivitySheet
import app.babylog.ui.home.InputRow
import app.babylog.ui.home.InputValue
import app.babylog.ui.home.StepperRow
import app.babylog.ui.home.SwitchRow
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import java.time.DayOfWeek

/**
 * One child's reminder for one activity: `Set reminder — Diaper`.
 *
 * **Per child, never "Both".** A reminder is a thing that happens to a person: First Twin's 1:00 PM nappy is not
 * Second Twin's, and a shared rule hid which twin was actually overdue. When there are two children the sheet asks
 * which one, and `For` is the first row.
 *
 * The rows are the owner's mockup minus what he dropped: `Time` (an interval, standardised so the -/+ buttons do
 * not move between `1h` and `1h 30m`), `Notify`, and the night window and day chips that were asked for earlier.
 * `Start`, `End` and `Snooze (10 min)` are gone.
 *
 * Everything is local state until the Set button, like every other sheet in the app: a half-edited rule must
 * never be live.
 */
@Composable
fun ReminderSheet(
    type: EventType,
    accent: Color,
    children: List<Child>,
    initialChildId: String?,
    /**
     * Every stored rule for this activity, keyed by child id.
     *
     * A map rather than one config: the sheet can change which child it is editing, so `initial`, `editing` and the
     * button's label all have to follow the CHILD BEING EDITED. Computing them once for the child the switcher
     * happened to show meant picking the other twin still said `Update` and pre-filled the first twin's rule.
     */
    configs: Map<String, ReminderConfig>,
    /** False when notifications are denied: said here, where the reminder is actually set. */
    notificationsEnabled: Boolean,
    nightStartHour: Int,
    nightEndHour: Int,
    onDismiss: () -> Unit,
    onSave: (childId: String, config: ReminderConfig) -> Unit,
    onAllowNotifications: () -> Unit = {},
) {
    val colors = BabyLogTheme.colors
    var childId by remember {
        mutableStateOf(initialChildId ?: children.firstOrNull()?.id.orEmpty())
    }
    // The sheet edits an INTERVAL, so a stored rule is shown (and saved) as one. A config left in the old
    // fixed-times mode by an earlier build could never fire and had no UI left to fix it; opening it here converts
    // it, keeping its interval.
    fun editable(config: ReminderConfig?) = (config ?: ReminderConfig()).copy(
        mode = ReminderMode.AFTER_LAST,
        timesOfDay = emptyList(),
    )

    var config by remember { mutableStateOf(editable(configs[childId])) }
    // Switching child loads THAT child's rule, rather than carrying the previous one across and writing it back.
    var loadedFor by remember { mutableStateOf(childId) }
    if (childId != loadedFor) {
        loadedFor = childId
        config = editable(configs[childId])
    }
    val editing = configs[childId]?.enabled == true

    ActivitySheet(
        title = "Set reminder \u2014 ${type.displayName()}",
        accent = accent,
        subtitle = children.firstOrNull { it.id == childId }?.name,
        onDismiss = onDismiss,
        onSave = { onSave(childId, config) },
        saveLabel = if (editing) "Update" else "Set",
        saveEnabled = childId.isNotEmpty(),
    ) {
        if (!notificationsEnabled) {
            // The rule can be perfect and still deliver nothing, so this is said at the point of setting it
            // rather than in a Settings section that no longer exists.
            InputRow(label = "Notifications are off", onClick = onAllowNotifications, chevron = false) {
                InputValue("Allow")
            }
        }

        if (children.size > 1) {
            ChildChoice(
                children = children,
                selectedId = childId,
                accent = accent,
                onSelect = { childId = it },
            )
        }

        // `valueWidth` is the standardisation: 1h and 1h 30m are different widths, and without a fixed slot the
        // buttons step sideways as the value changes.
        StepperRow(
            label = "Time",
            value = intervalLabel(config.intervalMinutes),
            onDecrease = {
                config = config.copy(intervalMinutes = stepInterval(config.intervalMinutes, -1))
            },
            onIncrease = {
                config = config.copy(intervalMinutes = stepInterval(config.intervalMinutes, 1))
            },
            valueWidth = 88.dp,
        )
        Text(
            text = "After the last ${type.displayName().lowercase()} logged for " +
                (children.firstOrNull { it.id == childId }?.name ?: "this child") + ".",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        SwitchRow(
            label = "Notify",
            checked = config.enabled,
            accent = accent,
            onCheckedChange = { config = config.copy(enabled = it) },
        )

        SwitchRow(
            label = "Skip night",
            checked = config.skipNight,
            accent = accent,
            onCheckedChange = { config = config.copy(skipNight = it) },
        )
        Text(
            text = if (nightStartHour == nightEndHour) {
                "Day & night is set to no night at all, so this changes nothing."
            } else {
                "Quiet from ${hourLabel(nightStartHour)} to ${hourLabel(nightEndHour)}, then it fires when the " +
                    "night ends."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        DayChips(
            days = config.days,
            accent = accent,
            onToggle = { day ->
                val next = if (day in config.days) config.days - day else config.days + day
                // The last day cannot be switched off: a reminder with no days is unrepresentable, and a
                // disabled-looking chip would be a lie about why nothing fires.
                if (next.isNotEmpty()) config = config.copy(days = next)
            },
        )
    }
}

/** Which child the reminder is for. A row of chips, because it is one question with one answer. */
@Composable
private fun ChildChoice(
    children: List<Child>,
    selectedId: String,
    accent: Color,
    onSelect: (String) -> Unit,
) {
    val colors = BabyLogTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "For",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            children.forEach { child ->
                val selected = child.id == selectedId
                Text(
                    text = child.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.onAccent else colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) accent else colors.surfaceRaised)
                        .clickable { onSelect(child.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

/** The day chips: circular, Su-Sa, all on by default. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(days: Set<DayOfWeek>, accent: Color, onToggle: (DayOfWeek) -> Unit) {
    val colors = BabyLogTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Days",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in days
                Text(
                    text = day.name.take(2).lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.onAccent else colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) accent else colors.surfaceRaised)
                        .clickable { onToggle(day) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

/**
 * One step along [ReminderConfig.INTERVAL_CHOICES].
 *
 * A fixed list rather than a free-text field or a free-running stepper: 2h50 is not an interval anybody wants, and
 * a list keeps the choice to one tap. Steps clamp at the ends instead of wrapping, so holding `+` cannot jump from
 * six hours to half an hour.
 */
internal fun stepInterval(current: Int, direction: Int): Int {
    val choices = ReminderConfig.INTERVAL_CHOICES
    val exact = choices.indexOf(current)
    val index = if (exact >= 0) {
        exact
    } else {
        // A value that is not one of the choices (a config from an older build) steps from the nearest one above
        // it, or from the top of the list if it is longer than any of them.
        choices.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: choices.lastIndex
    }
    return choices[(index + direction).coerceIn(0, choices.lastIndex)]
}

private fun hourLabel(hour: Int): String = java.time.LocalTime.of(hour, 0).format(
    java.time.format.DateTimeFormatter.ofPattern("h:00 a", java.util.Locale.US),
)
