package app.babylog.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.babylog.domain.FEVER_C
import app.babylog.domain.FeverReading
import app.babylog.domain.HealthTrend
import app.babylog.domain.TrendRange
import app.babylog.domain.formatTemperature
import app.babylog.ui.home.FeverBadge
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.labelFor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Health over the window: a dated list of fever readings, and how many days of fever there were.
 *
 * Deliberately NOT a chart. A temperature is a reading at a point in time rather than a per-day
 * quantity, so a daily average would invent a number nobody should act on — and a line drawn between two
 * readings three days apart is a line through nothing. What a parent looks back for is "how long did
 * that last", which is a list and a day count.
 *
 * Only readings at or above [FEVER_C] are listed; the plain readings are counted, not listed, or the
 * one line that matters would be buried in a fortnight of 36.5s.
 */
@Composable
fun HealthTrendPane(
    health: HealthTrend,
    range: TrendRange,
    childLabel: String,
    zone: ZoneId,
    onSelectRange: (TrendRange) -> Unit,
    accent: Color,
    contentPadding: PaddingValues,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    // Only name the child per row when more than one is in the list, exactly as Growth's measurement
    // rows do: a column of identical `K` badges says nothing.
    val showChild = health.feverReadings.map { it.childName }.distinct().size > 1

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item { RangeChips(range = range, onSelectRange = onSelectRange) }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(colors.cardFor(accent))
                    .padding(Spacing.cardPadding),
            ) {
                Text(
                    text = "Fever days",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.labelFor(accent),
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = health.feverDays.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.labelFor(accent),
                    )
                    Text(
                        text = "   in the last ${range.days} days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textOnCard,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                DataRow("Temperatures", health.readings.toString())
                DataRow("Fever readings", health.feverReadings.size.toString())
                if (childLabel.isNotEmpty()) DataRow("Children", childLabel.removePrefix("for "))
            }
        }

        if (health.readings == 0) {
            item {
                Text(
                    text = "No temperatures logged in the last ${range.days} days. " +
                        "Use + Add health record on the Health card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else if (!health.hasFever) {
            item {
                Text(
                    // A count with no fever is a good answer, and it says exactly what it means rather
                    // than showing an empty list that reads like a failure.
                    text = "${health.readings} temperature" + (if (health.readings == 1) "" else "s") +
                        " logged in the last ${range.days} days, all below ${formatThreshold()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            item {
                Text(
                    text = "Fever readings",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }
            items(health.feverReadings) { reading ->
                ReadingRow(reading = reading, zone = zone, showChild = showChild, accent = accent)
            }
            item {
                Text(
                    text = "A reading at or above ${formatThreshold()} is marked as a fever. " +
                        "The marker describes the number, not the child — an armpit reading runs lower than " +
                        "a core one, so a flagged reading is a prompt to look rather than a diagnosis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReadingRow(reading: FeverReading, zone: ZoneId, showChild: Boolean, accent: Color) {
    val colors = BabyLogTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showChild) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = reading.childName.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
            }
        }
        Text(
            text = formatTemperature(reading.temperatureC),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
        // The same shape marker the dashboard, the entry list and Summary use. On a plain background the
        // feverOnCard fill clears 10.39:1 (dark) / 9.75:1 (light), so it is legible here too.
        FeverBadge(modifier = Modifier.padding(start = 8.dp))
        Box(modifier = Modifier.weight(1f))
        Text(
            text = readingLabel(reading.at, zone),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    val colors = BabyLogTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textOnCard,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textOnCard,
        )
    }
}

private val readingFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)

private fun readingLabel(at: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(at).atZone(zone).format(readingFormat)

private fun formatThreshold(): String = formatTemperature(FEVER_C)
