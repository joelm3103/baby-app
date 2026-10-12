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
import app.babylog.ui.theme.labelFor
import app.babylog.ui.theme.displayName
import java.time.ZoneId

/**
 * The core component. Anatomy from the approved mockup: activity name in its accent hue with the last
 * event's relative time opposite, the last value, a full-width action in the accent, then recent rows.
 *
 * The card surface is NOT tinted: a tint over a dark surface collapses the accent to a muddy chroma
 * and drops the label below AA (measured - see `Tokens.kt`). Identity comes from a full-chroma accent
 * bar at the leading edge plus the accent-filled action button. At 3am the bar is what you aim at.
 */
@Composable
fun ActivityCard(
    card: CardUi,
    units: UnitPreferences,
    now: Long,
    zone: ZoneId,
    onAction: () -> Unit,
    onQuickDiaper: (DiaperKind) -> Unit,
    onStop: () -> Unit,
    onSeeAll: () -> Unit,
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value.first, style = MaterialTheme.typography.displayLarge, color = colors.textPrimary)
                        if (value.second.isNotBlank()) {
                            Text(" ${value.second}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
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
                // Diaper asks one question with three answers, so it answers in place of a sheet.
                EventType.DIAPER -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaperKind.entries.forEach { kind ->
                        PillButton(
                            label = kind.name.lowercase().replaceFirstChar { it.uppercase() },
                            fill = colors.surface,
                            textColor = accent,
                            onClick = { onQuickDiaper(kind) },
                        )
                    }
                }
                else -> ActionButton(
                    type = card.type,
                    fill = accent,
                    textColor = colors.onAccent,
                    onClick = onAction,
                )
            }
        }

        // 3. recent rows, excluding whatever the header already showed
        val more = card.recent.drop(1)
        if (more.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            more.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = detailLine(event, units),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = clockTime(event.startedAt, zone),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textOnCard,
                    )
                }
            }
        }

        // 4. footer
        if (card.recent.size >= 3) {
            HorizontalDivider(color = colors.divider)
            Text(
                text = "See all \u203a",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textOnCard,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSeeAll).padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    type: EventType,
    fill: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    val verb = when (type) {
        EventType.BOTTLE -> "Log bottle"
        EventType.SOLIDS -> "Log solids"
        EventType.SLEEP -> "Start sleep"
        EventType.GROWTH -> "Add measurement"
        EventType.HEALTH -> "Add health"
        EventType.DIAPER -> "Log diaper"
    }
    PillButton(label = "+ $verb", fill = fill, textColor = textColor, onClick = onClick, fullWidth = true)
}

@Composable
private fun PillButton(
    label: String,
    fill: Color,
    onClick: () -> Unit,
    textColor: Color = BabyLogTheme.colors.textPrimary,
    fullWidth: Boolean = false,
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
