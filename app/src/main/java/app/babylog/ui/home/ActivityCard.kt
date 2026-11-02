package app.babylog.ui.home

import app.babylog.ui.LocalTimeFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.babylog.domain.displayName
import app.babylog.domain.BabyEvent
import app.babylog.domain.TemperatureBand
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.ui.common.glyphPainter
import app.babylog.domain.markedBand
import app.babylog.domain.UnitPreferences
import app.babylog.domain.formatVolume
import app.babylog.domain.clockTime
import app.babylog.domain.elapsed
import app.babylog.ui.common.RelativeTimeText
import app.babylog.ui.common.activityPattern
import app.babylog.ui.common.rememberHaptics
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.labelFor
import java.time.ZoneId
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The core component. Anatomy from the approved mockup: activity name in its accent hue with the last
 * event's relative time opposite, the last value, a full-width action in the accent, then recent rows.
 *
 * The card surface is TINTED (accent at 26% over `surface`): full-section tint is what identifies a
 * section at a glance, and the fraction is measured rather than taste. The consequences are deliberate
 * — the label uses `colors.labelFor(accent)` because the raw accent is not legible on its own tint, and
 * meta text uses `textOnCard` rather than `textSecondary`, which fails AA on the stronger fills.
 *
 * The row block shows TODAY's entries (see [cardRows]) — newest first, the entry behind the big number
 * included — and "See all" whenever something is not on the card. Rows are tappable for editing: a
 * dashboard you cannot correct is a dashboard you stop trusting.
 */
/**
 * The answers a quick pill on the Diaper card may write.
 *
 * Wet and Dirty only, and deliberately NOT `DiaperKind.entries`. "Both" is a two-part answer, and a single
 * tap cannot be a two-part answer: choosing it for the parent is the shortcut-to-a-wrong-record that the
 * sheet exists to prevent. [app.babylog.domain.DiaperKind.MIXED] is therefore reachable only from the sheet.
 */
internal val DIAPER_QUICK_KINDS: List<DiaperKind> = listOf(DiaperKind.WET, DiaperKind.DIRTY)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityCard(
    card: CardUi,
    units: UnitPreferences,
    childLabel: String,
    childInitials: Map<String, String>,
    /** Names, for the Growth card's per-child columns, which are headed by name rather than initial. */
    childNames: Map<String, String> = emptyMap(),
    /** The children in scope, in switcher order. Two or more means Both mode. */
    childIds: List<String> = emptyList(),
    showChildBadge: Boolean,
    now: Long,
    zone: ZoneId,
    onOpenSheet: (EventType) -> Unit,
    /** The Health card's second form. A separate callback because the type alone cannot say which. */
    onOpenVaccine: () -> Unit,
    onQuickDiaper: (DiaperKind) -> Unit,
    /**
     * One tap on a preset logs a feed of that many ml, with no sheet.
     *
     * The same contract as [onQuickDiaper]: the tap IS the record, so it writes immediately. The amount is
     * canonical millilitres and the label is composed here in the parent's display unit, so the same preset
     * reads `4 oz` or `120 ml` without stored data moving.
     */
    onQuickBottle: (Int) -> Unit = {},
    /** The amounts worth one tap, in ml, ascending. Empty draws no pill row at all. */
    bottlePresets: List<Int> = emptyList(),
    onStartSleep: () -> Unit,
    onStop: () -> Unit,
    onSeeAll: () -> Unit,
    onEditEntry: (BabyEvent) -> Unit,
    onDeleteEntry: (BabyEvent) -> Unit,
    /**
     * This card's reminders, one chip per child in scope, e.g. `Every 3h (1:00 PM)`. Empty when there is none.
     *
     * Chips under the actions rather than in the header: a reminder is a standing instruction, and the header is where
     * the last event's relative time lives. With both children on screen there are two chips, each with that child's
     * badge, so "whose 2:30 PM" needs no reading.
     */
    reminderChips: List<app.babylog.domain.ReminderChip> = emptyList(),
    /**
     * Null when this activity cannot carry a reminder (Growth and Sleep), so no menu is drawn at all rather than a
     * menu with a disabled item explaining itself.
     *
     * The second argument is WHICH child the action is about: a chip knows its own child, and the menu passes null
     * because a card showing both children has no single one in mind.
     */
    onReminderAction: ((ReminderAction, childId: String?) -> Unit)? = null,
    /** `id -> name` for the solids catalogue, so a meal reads as a name and not as a slug. */
    foodNames: Map<String, String> = emptyMap(),
) {
    val colors = BabyLogTheme.colors
    val accent = colors.accentFor(card.type)
    val last = card.last

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.cardFor(accent))
            // The activity's own glyph, tiled tone-on-tone behind everything below. Identity by shape
            // as well as hue: the six tints are 12.5 dE apart, which identifies a section at a glance
            // but not at arm's length in the dark. Drawn after the fill and before the padding, so it
            // sits UNDER every label, value and row.
            .activityPattern(card.type, accent)
            .padding(Spacing.cardPadding),
    ) {
        // 1. header: accent label, relative time of the last event
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.type.displayName(),
                style = MaterialTheme.typography.titleMedium,
                color = colors.labelFor(accent),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (last != null && card.running == null) {
                    RelativeTimeText(then = last.startedAt, now = now)
                }
                if (onReminderAction != null) {
                    ReminderMenu(
                        hasReminder = reminderChips.isNotEmpty(),
                        onAction = { action -> onReminderAction(action, null) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 2. either the running timer, or the last value + the action
        val running = card.running
        if (running != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = elapsed(now - running.startedAt),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.textPrimary,
                )
                PillButton(label = "Stop", fill = accent, textColor = colors.onAccent, onClick = onStop)
            }
        } else {
            // Growth and Health split into one column per child in Both mode. A single combined value has
            // no owner — "6 kg" was one child's weight read as the twins', and a temperature has exactly
            // the same problem. With one child selected the big-number card below is unchanged.
            val columns = if (childIds.size > 1) {
                cardColumns(card.type, card.entries, childIds, units)
            } else {
                null
            }
            if (columns != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    columns.forEach { column ->
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // No badge here: this column is already headed by the child's NAME, so an
                                // initial beside it was the same fact twice at the cost of a row's width.
                                // The card's own activity, small, so a column read on its own still says
                                // what it is measuring. Drawn from the card's type rather than passed in,
                                // so no caller can hand a Growth column a bottle.
                                Icon(
                                    painter = card.type.glyphPainter(),
                                    contentDescription = null,
                                    tint = colors.labelFor(accent),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = childNames[column.childId] ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.labelFor(accent),
                                )
                            }
                            // One item per line. Wrapped into a single line, three measurements plus their
                            // codes were unreadable at half a card's width.
                            if (column.hasData) {
                                column.lines.forEach { line ->
                                    val band = line.band
                                    if (band != null) {
                                        // A shape, not just a hue: the tinted number alone was invisible on
                                        // a magenta card (the owner's report), so the word rides in a pill.
                                        // Only FEVER recolours the number; Low and Mild keep textPrimary and
                                        // rely on the outline, so there is exactly one strong cue.
                                        Row(
                                            modifier = Modifier.padding(top = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (band == TemperatureBand.FEVER) {
                                                    colors.feverOnCard
                                                } else {
                                                    colors.textPrimary
                                                },
                                            )
                                            TemperatureBadge(band)
                                        }
                                    } else {
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.textPrimary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = if (card.type == EventType.GROWTH) {
                                        "No measurements yet"
                                    } else {
                                        "No records yet"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Growth's summary is the newest value of EACH measurement, not the newest entry's fields:
            // weight logged today and a head circumference logged last week are both the current answer,
            // and reading only the newest entry made the older one vanish from the card.
            val growth = if (card.type == EventType.GROWTH) growthLatest(card.entries) else null
            // Health's headline is the newest TEMPERATURE, not the newest event: a medication logged after
            // a fever would otherwise become the card and take the fever off it entirely. `last` is only
            // the fallback, for a card whose newest record is a medication with no reading.
            val health = if (card.type == EventType.HEALTH) healthLatest(card.entries) else null
            val headline = growth?.headline ?: health?.temperature ?: health?.medication ?: last
            if (headline != null && columns == null) {
                val value = growth?.let { growthBigValue(it, units) } ?: bigValue(headline, units)
                val band = if (card.type == EventType.HEALTH) markedBand(headline.temperatureC) else null
                if (value != null) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        // Only FEVER leaves `textPrimary` — the filled pill beside it is the marker, and the
                        // colour is the second cue rather than the only one. A Mild or Low reading keeps
                        // the number's own colour and relies on the outlined pill.
                        val valueColor = if (band == TemperatureBand.FEVER) colors.feverOnCard else colors.textPrimary
                        Text(
                            value.first,
                            style = MaterialTheme.typography.displayLarge,
                            color = valueColor,
                        )
                        if (value.second.isNotBlank()) {
                            Text(
                                " ${value.second}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = valueColor,
                            )
                        }
                        if (band != null) {
                            TemperatureBadge(
                                band,
                                Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 8.dp, bottom = 4.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // In Both mode the newest event is ONE child's, and the card used to say which
                        // only by omission. Naming it (and dating it) removes the "combined is wrong"
                        // reading without changing what the number means. The NAME, not the initial: a
                        // lone `K` beside a big number is a code to decode, and the name is already here.
                        // One line, ellipsised, so a long name cannot push the time off the card.
                        if (showChildBadge) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 4.dp),
                            ) {
                                Text(
                                    text = childNames[headline.childId].orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textOnCard,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = clockTime(headline.startedAt, zone, LocalTimeFormat.current),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textOnCard,
                                )
                            }
                        }
                    }
                }
                // The detail line and the colour dots share one row: the dots are part of what the newest
                // change WAS, and the same mark is replayed on the rows below (DiaperColorDots). A Row so
                // the text still ellipsises as one line at 1.3x font scale instead of wrapping the card.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = growth?.let { growthDetail(it, units) } ?: detailLine(headline, units, foodNames),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textOnCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    DiaperColorDots(
                        colors = diaperColors(headline),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            when (card.type) {
                // Diaper answers its two common cases in place of a sheet: one tap for Wet, one for Dirty.
                // Saying BOTH now costs opening the sheet — the owner's call, 2026-10-24 — so this list is
                // deliberately not `DiaperKind.entries`: a quick pill must never write MIXED, which is the
                // encoding of a two-part answer the parent did not give in one tap.
                EventType.DIAPER -> Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DIAPER_QUICK_KINDS.forEach { kind ->
                            PillButton(
                                label = kind.name.lowercase().replaceFirstChar { it.uppercase() },
                                fill = colors.surface,
                                textColor = accent,
                                onClick = { onQuickDiaper(kind) },
                            )
                        }
                    }
                    Text(
                        text = "More \u203a",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textOnCard,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { onOpenSheet(EventType.DIAPER) },
                    )
                }

                // Sleep is a timer, not a form: the action starts it on the card (see PLAN.md Task 6.2).
                EventType.SLEEP -> PillButton(
                    label = "Start sleep",
                    fill = accent,
                    textColor = colors.onAccent,
                    onClick = onStartSleep,
                    fullWidth = true,
                )

                EventType.BOTTLE -> Column {
                    // The presets are the common case, so they come first and log on the tap; the full-width
                    // button below stays for a custom amount, a brand, a note or a feed that happened earlier.
                    // FlowRow, not Row: the settings screen lets a parent add as many sizes as they like, and
                    // a fixed row would clip the fifth one off the card with no way to reach it.
                    if (bottlePresets.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            bottlePresets.forEach { millilitres ->
                                PillButton(
                                    label = formatVolume(millilitres.toDouble(), units.volume),
                                    fill = colors.surface,
                                    textColor = accent,
                                    onClick = { onQuickBottle(millilitres) },
                                )
                            }
                        }
                    }
                    PillButton(
                        label = "+ Log feed for $childLabel",
                        fill = accent,
                        textColor = colors.onAccent,
                        onClick = { onOpenSheet(EventType.BOTTLE) },
                        fullWidth = true,
                    )
                }

                EventType.SOLIDS -> PillButton(
                    label = "+ Log solids for $childLabel",
                    fill = accent,
                    textColor = colors.onAccent,
                    onClick = { onOpenSheet(EventType.SOLIDS) },
                    fullWidth = true,
                )

                // Growth logs a measurement; its chart is the screen behind "See all".
                EventType.GROWTH -> PillButton(
                    label = "+ Add measurement",
                    fill = accent,
                    textColor = colors.onAccent,
                    onClick = { onOpenSheet(EventType.GROWTH) },
                    fullWidth = true,
                )

                // Health has TWO forms — a temperature/medication record and a vaccine — and they are
                // separate records, so they get separate actions. The primary action is the one a parent
                // reaches for at 3am; the vaccine is a neutral secondary beside it, not a second loud
                // button competing for the same tap.
                EventType.HEALTH -> Column {
                    PillButton(
                        label = "+ Add health record for $childLabel",
                        fill = accent,
                        textColor = colors.onAccent,
                        onClick = { onOpenSheet(EventType.HEALTH) },
                        fullWidth = true,
                    )
                    Text(
                        text = "+ Vaccine",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(colors.surfaceRaised)
                            .clickable(onClick = onOpenVaccine)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }

        // The reminder, if this child has one: a standing instruction, so it sits under the actions rather than
        // in the header. The bell is the same mark the notification uses.
        if (reminderChips.isNotEmpty()) {
            // FlowRow: two chips with a badge and a time are wider than half a phone at 1.3x font scale, and a row
            // that clips the second one is a reminder the parent cannot read.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                // Tight, because both children's chips should sit on ONE row: a badge, a bell, `Every 3h (4:45 PM)`
                // twice, inside the 328dp a 360dp phone leaves after the card's padding.
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                reminderChips.forEach { chip ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(colors.surface)
                            // Tapping a chip EDITS that reminder. It is the thing showing the rule, so it is the
                            // thing to tap; going through the overflow menu for the same job was a step nobody needs.
                            // The CHIP's child, not the card's: with both children on screen the second chip belongs
                            // to the second twin, and opening the first twin's rule from it was a bug on the phone.
                            .clickable(enabled = onReminderAction != null) {
                                onReminderAction?.invoke(ReminderAction.SET, chip.childId)
                            }
                            // Measured: at 9dp of side padding, labelMedium and a 12dp bell each chip was 458px, and
                            // two of them plus the gap (933) did not fit the 900px inside the card's padding - so they
                            // wrapped onto two rows. These numbers bring a chip to ~420px, which does fit.
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // The badge only in Both mode, the same rule the entry rows follow. 14dp, not the row's
                        // 16dp: it is a marker inside a chip, not a row's identity.
                        val initials = chip.childId?.let { childInitials[it] }
                        if (initials != null) {
                            ChildBadge(initials = initials, size = 14.dp)
                        }
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = chip.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 3. today's entries, the one behind the big number included
        val rows = cardRows(card.entries, now, zone)
        if (rows.shown.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.rowBlockTop))
            rows.shown.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                EntryRow(
                    event = event,
                    units = units,
                    foodNames = foodNames,
                    childName = childNames[event.childId],
                    showChild = showChildBadge,
                    // The row says its band in a pill, so a flagged reading cannot be read past in a list.
                    band = markedBand(event.temperatureC),
                    zone = zone,
                    onTap = { onEditEntry(event) },
                    onLongPress = { onDeleteEntry(event) },
                )
            }
        }

        // 4. footer: `See all` once the activity has any entry at all — the same rule for every card, and
        // the full list of that activity's entries behind it. The link is the route to everything the card
        // cannot fit: today's overflow, older entries, and (for growth) the whole measurement history.
        if (showsFooter(card.entries.size)) {
            HorizontalDivider(color = colors.divider)
            Text(
                text = "See all \u203a",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textOnCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSeeAll)
                    .padding(top = 10.dp, bottom = 2.dp),
            )
        }
    }
}

/** What a card's reminder menu can ask for. */
enum class ReminderAction { SET, REMOVE }

/**
 * The card's `⋮` menu.
 *
 * Only on the four reminder-capable cards: Growth and Sleep pass no handler, so they have no menu at all - a menu
 * whose only useful item is missing is worse than no menu. `Set reminder` becomes `Edit reminder` when this child
 * already has one, and `Remove reminder` appears only then, because removing nothing is not an action.
 */
@Composable
private fun ReminderMenu(hasReminder: Boolean, onAction: (ReminderAction) -> Unit) {
    val colors = BabyLogTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Reminder options",
                tint = colors.textOnCard,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (hasReminder) "Edit reminder" else "Set reminder") },
                onClick = {
                    open = false
                    onAction(ReminderAction.SET)
                },
            )
            if (hasReminder) {
                DropdownMenuItem(
                    text = { Text("Remove reminder") },
                    onClick = {
                        open = false
                        onAction(ReminderAction.REMOVE)
                    },
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    fill: Color,
    onClick: () -> Unit,
    textColor: Color = BabyLogTheme.colors.textPrimary,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
) {
    // The card's action buttons and its quick pills are all this composable, so one line makes the whole
    // card feel like a control.
    val haptics = rememberHaptics()
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(Radius.pill))
            .background(fill)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptics.tap()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
