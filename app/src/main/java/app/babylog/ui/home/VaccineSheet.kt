package app.babylog.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.babylog.data.repo.EventDraft
import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import app.babylog.domain.dateOn
import app.babylog.ui.theme.BabyLogTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Vaccine: date, time, the vaccine's name, notes.
 *
 * Its own sheet rather than a field on [HealthSheet], because a vaccine is a record in its own right —
 * it is entered from a red book or a clinic slip, on a different day from any fever, and a parent looking
 * for it should not have to open a temperature form to find it.
 *
 * The name is REQUIRED and is the whole record: there is no other field that could carry the meaning, so
 * Save stays disabled until something is typed. Free text, deliberately — this app does not carry a
 * schedule of vaccine names to choose from, and a custom one is better than a wrong one from a list.
 */
@Composable
fun VaccineSheet(
    accent: Color,
    childLabel: String,
    zone: ZoneId,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    initial: BabyEvent? = null,
) {
    val colors = BabyLogTheme.colors
    var startedAt by remember { mutableStateOf(initial?.startedAt ?: now) }
    var fields by remember { mutableStateOf(VaccineFields(initial?.vaccineName.orEmpty())) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    val read = readVaccine(fields)

    ActivitySheet(
        title = if (initial != null) "Edit vaccine" else "Vaccine",
        accent = accent,
        subtitle = "for $childLabel",
        saveEnabled = read.vaccineName != null,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.HEALTH,
                    startedAt = startedAt,
                    // No temperature or medication: this sheet owns the vaccine. Which of the two health
                    // sheets opens is decided in AppScaffold, from the row being edited or the button tapped.
                    vaccineName = read.vaccineName,
                    note = note.trim().ifEmpty { null },
                ),
            )
        },
    ) {
        DateRow(
            label = "Date",
            epochDay = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate().toEpochDay(),
        ) { epochDay -> startedAt = dateOn(startedAt, epochDay, zone) }
        TimeRow(instantMs = startedAt, zone = zone, onChange = { startedAt = it })

        TextFieldRow(
            label = "Vaccine",
            value = fields.vaccineName,
            onValueChange = { typed -> fields = VaccineFields(typed) },
            placeholder = "e.g. 6-in-1",
        )
        if (read.vaccineName == null) {
            Text(
                text = "Enter the vaccine's name.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        TextFieldRow(
            label = "Notes",
            value = note,
            onValueChange = { note = it },
            placeholder = "Optional",
            singleLine = false,
        )
    }
}
