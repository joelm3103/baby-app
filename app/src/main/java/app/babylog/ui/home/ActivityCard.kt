package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.domain.UnitPreferences
import app.babylog.domain.clockTime
import app.babylog.domain.elapsed
import app.babylog.ui.common.RelativeTimeText
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.displayName
import app.babylog.ui.theme.labelFor
import java.time.ZoneId

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
    onStartSleep: () -> Unit,
    onStop: () -> Unit,
    onSeeAll: () -> Unit,
    onEditEntry: (BabyEvent) -> Unit,
    onDeleteEntry: (BabyEvent) -> Unit,
) {
    val colors = BabyLogTheme.colors
    val accent = colors.accentFor(card.type)
    val last = card.last

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.cardFor(accent))
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
            if (last != null && card.running == null) {
                RelativeTimeText(then = last.startedAt, now = now)
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
                                ChildBadge(
                                    initials = childInitials[column.childId] ?: "?",
                                    size = Spacing.rowBadge,
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
                                    if (line.fever) {
                                        // A shape, not just a hue: the tinted number alone was invisible on
                                        // a magenta card (the owner's report), so the word rides in a pill.
                                        Row(
                                            modifier = Modifier.padding(top = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.feverOnCard,
                                            )
                                            FeverBadge()
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
                val fever = card.type == EventType.HEALTH && isFever(headline.temperatureC)
                if (value != null) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        // A reading at or above 38.0 °C leaves `textPrimary` — the pill beside it is the
                        // marker, and the colour is the second cue rather than the only one.
                        Text(
                            value.first,
                            style = MaterialTheme.typography.displayLarge,
                            color = if (fever) colors.feverOnCard else colors.textPrimary,
                        )
                        if (value.second.isNotBlank()) {
                            Text(
                                " ${value.second}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fever) colors.feverOnCard else colors.textPrimary,
                            )
                        }
                        if (fever) {
                            FeverBadge(
                                Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 8.dp, bottom = 4.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // In Both mode the newest event is ONE child's, and the card used to say which
                        // only by omission. Naming it (and dating it) removes the "combined is wrong"
                        // reading without changing what the number means.
                        if (showChildBadge) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 4.dp),
                            ) {
                                ChildBadge(initials = childInitials[headline.childId] ?: "?", size = Spacing.rowBadge)
                                Text(
                                    text = clockTime(headline.startedAt, zone),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textOnCard,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = growth?.let { growthDetail(it, units) } ?: detailLine(headline, units),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textOnCard,
                )
                Spacer(Modifier.height(10.dp))
            }

            when (card.type) {
                // Diaper asks one question with three answers, so it answers in place of a sheet. The
                // sheet, behind "More", is only for what the pills cannot say: back-dating, a rash, a note.
                EventType.DIAPER -> Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiaperKind.entries.forEach { kind ->
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

                EventType.BOTTLE -> PillButton(
                    label = "+ Log feed for $childLabel",
                    fill = accent,
                    textColor = colors.onAccent,
                    onClick = { onOpenSheet(EventType.BOTTLE) },
                    fullWidth = true,
                )

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

        // 3. today's entries, the one behind the big number included
        val rows = cardRows(card.entries, now, zone)
        if (rows.shown.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.rowBlockTop))
            rows.shown.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                EntryRow(
                    event = event,
                    units = units,
                    initials = childInitials[event.childId],
                    showChild = showChildBadge,
                    // The row says `Fever` in a pill, so a fever cannot be read past in a list either.
                    fever = isFever(event.temperatureC),
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

@Composable
private fun PillButton(
    label: String,
    fill: Color,
    onClick: () -> Unit,
    textColor: Color = BabyLogTheme.colors.textPrimary,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(Radius.pill))
            .background(fill)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
