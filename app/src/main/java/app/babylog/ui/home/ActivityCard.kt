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
    showChildBadge: Boolean,
    now: Long,
    zone: ZoneId,
    onOpenSheet: (EventType) -> Unit,
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
            if (last != null) {
                val value = bigValue(last, units)
                if (value != null) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(value.first, style = MaterialTheme.typography.displayLarge, color = colors.textPrimary)
                        if (value.second.isNotBlank()) {
                            Text(" ${value.second}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
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
                                ChildBadge(initials = childInitials[last.childId] ?: "?", size = Spacing.rowBadge)
                                Text(
                                    text = clockTime(last.startedAt, zone),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textOnCard,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = detailLine(last, units),
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

                // Deliberately not wired to anything until their sheets exist. The Phase-5 stub logged a
                // growth/health event with no values at all, which would put meaningless rows in the log
                // the moment real data arrives — a disabled button is the honest placeholder.
                EventType.GROWTH, EventType.HEALTH -> Column {
                    PillButton(
                        label = if (card.type == EventType.GROWTH) "+ Add measurement" else "+ Add health",
                        fill = colors.surfaceRaised,
                        textColor = colors.textSecondary,
                        enabled = false,
                        onClick = {},
                        fullWidth = true,
                    )
                    Text(
                        text = if (card.type == EventType.GROWTH) {
                            "Growth charts arrive in Phase 10"
                        } else {
                            "Health records arrive in Phase 11"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textOnCard,
                        modifier = Modifier.padding(top = 6.dp),
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
                    zone = zone,
                    onTap = { onEditEntry(event) },
                    onLongPress = { onDeleteEntry(event) },
                )
            }
        }

        // 4. footer: only when at least one entry is not already on the card — today's overflow, or
        // anything older than today. With everything visible the link would lead to the same list twice.
        if (rows.hasMore) {
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
