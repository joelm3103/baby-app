package app.babylog.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.babylog.data.repo.EventDraft
import app.babylog.domain.BabyEvent
import app.babylog.domain.DoseUnit
import app.babylog.domain.EventType
import app.babylog.domain.dateOn
import app.babylog.ui.theme.BabyLogTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Health record: date, time, temperature, medication, dose, notes.
 *
 * A vaccine is deliberately NOT on this form — it has its own sheet ([VaccineSheet]) and its own button.
 * A vaccine is a scheduled event recorded a handful of times; a temperature or a dose is what a parent
 * reaches for at 3am, and mixing the two put an irrelevant field in front of them.
 *
 * **The medication picker offers NAMES, and nothing here suggests a dose.** The list is bundled
 * (`medications/medications.json`, generic names with a common brand in brackets) and the parent's own
 * recurring names come from Settings. The dose is asked for as an amount plus the measure it was given
 * in, always typed by hand: an infant dose depends on weight and age and changes as the children grow, so
 * a number this app proposed would be plausible, confident and possibly wrong. The last dose given is
 * shown in grey as a reminder of the parent's OWN record, never as a value to accept.
 *
 * At least ONE of temperature and medication is required: the Phase-5 stub wrote health events with no
 * values at all, and those rows are still in the log as a feature that lied.
 */
@Composable
fun HealthSheet(
    accent: Color,
    childLabel: String,
    /** The bundled medicine names. Empty is survivable: free text still logs the record. */
    medications: List<String>,
    /** This family's own recurring names, from Settings. */
    medicationPresets: List<String>,
    /**
     * The last dose recorded for a medicine, as `"First Twin" to "2.5 ml"` — a hint for the field below it.
     *
     * A lambda rather than the event list, so the sheet does not need to know what an event list is.
     */
    lastDoses: (String) -> List<Pair<String, String>> = { emptyList() },
    zone: ZoneId,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    initial: BabyEvent? = null,
) {
    val colors = BabyLogTheme.colors
    val initialValues = HealthValues(
        temperatureC = initial?.temperatureC,
        medication = initial?.medication,
        doseAmount = initial?.doseAmount,
        doseUnit = initial?.doseUnit,
    )
    // The stored row, rendered for the fields — and the text that counts as "untouched".
    val seed = remember(initial) { healthFieldsFor(initialValues) }

    var startedAt by remember { mutableStateOf(initial?.startedAt ?: now) }
    var fields by remember(seed) { mutableStateOf(seed) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var medicationMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }
    // Typing a name that is on no list is a first-class path, not a fallback: a local brand is a real
    // answer, and a picker with no way out of it is how a log gets a wrong medicine in it.
    var typingOwn by remember { mutableStateOf(false) }

    val read = readHealth(fields, initialValues)
    val hints = lastDoses(fields.medication)

    ActivitySheet(
        title = if (initial != null) "Edit health record" else "Health record",
        accent = accent,
        subtitle = "for $childLabel",
        saveEnabled = read.error == null && !read.values.isEmpty,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.HEALTH,
                    startedAt = startedAt,
                    temperatureC = read.values.temperatureC,
                    medication = read.values.medication,
                    doseAmount = read.values.doseAmount,
                    doseUnit = read.values.doseUnit,
                    // No vaccineName: this sheet owns the temperature, the medication and the dose, and
                    // VaccineSheet owns the vaccine. Which of the two opens is decided in AppScaffold.
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
            label = "Temperature (\u00b0C)",
            value = fields.temperature,
            onValueChange = { typed -> fields = fields.copy(temperature = decimalInput(typed)) },
            placeholder = "e.g. 38.2",
            keyboardType = KeyboardType.Decimal,
        )

        InputRow(label = "Medication", onClick = { medicationMenu = true }) {
            InputValue(fields.medication.ifBlank { "Choose or type" })
        }
        DropdownMenu(
            expanded = medicationMenu,
            onDismissRequest = { medicationMenu = false },
        ) {
            if (medications.isNotEmpty()) {
                GroupLabel("Common")
                medications.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            fields = fields.copy(medication = name)
                            typingOwn = false
                            medicationMenu = false
                        },
                    )
                }
            }
            if (medicationPresets.isNotEmpty()) {
                HorizontalDivider()
                GroupLabel("Yours")
                medicationPresets.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            fields = fields.copy(medication = name)
                            typingOwn = false
                            medicationMenu = false
                        },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        "Type another name\u2026",
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                },
                onClick = {
                    typingOwn = true
                    medicationMenu = false
                },
            )
        }
        if (typingOwn || fields.medication.isBlank()) {
            TextFieldRow(
                label = "Medication name",
                value = fields.medication,
                onValueChange = { typed -> fields = fields.copy(medication = typed) },
                placeholder = "e.g. Calpol",
            )
        }

        TextFieldRow(
            label = "Dose",
            value = fields.dose,
            onValueChange = { typed -> fields = fields.copy(dose = decimalInput(typed)) },
            placeholder = "e.g. 2.5",
            keyboardType = KeyboardType.Decimal,
        )
        InputRow(label = "Dose unit", onClick = { unitMenu = true }) {
            InputValue(fields.doseUnit?.label ?: "Choose")
        }
        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
            DoseUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.label) },
                    onClick = {
                        fields = fields.copy(doseUnit = unit)
                        unitMenu = false
                    },
                )
            }
        }

        if (hints.isNotEmpty()) {
            Text(
                text = "Last recorded \u2014 " +
                    hints.joinToString(" \u00b7 ") { (child, dose) -> "$child $dose" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = "Names only \u2014 this app does not suggest a dose.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (read.error != null) {
            Text(
                text = read.error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else if (read.values.isEmpty) {
            // Save is disabled in this state; say why rather than leaving a dead button.
            Text(
                text = "Enter a temperature or a medication.",
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

/** A non-clickable heading inside a dropdown menu, so the two groups are not a wall of names. */
@Composable
private fun GroupLabel(text: String) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = BabyLogTheme.colors.textSecondary,
        )
    }
}
