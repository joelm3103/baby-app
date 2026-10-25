package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.babylog.data.repo.EventDraft
import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import app.babylog.domain.LengthUnit
import app.babylog.domain.UnitPreferences
import app.babylog.domain.WeightUnit
import app.babylog.domain.dateOn
import app.babylog.ui.theme.BabyLogTheme
import java.time.Instant
import java.time.ZoneId

/**
 * Growth: date, time, and up to three measurements — weight, length and head circumference.
 *
 * Every box is optional, because a parent often weighs a baby without measuring them. But at least ONE
 * measurement is required: the Phase-5 stub wrote growth events with no values at all, and those rows
 * are still visible in the log as a feature that lied. A sheet that can save nothing is a sheet that
 * can lie again.
 *
 * Weight is a single field in kilograms, or the two-part `lb` / `oz` pair in imperial — the design
 * contract's rule that `lb & oz` is a compound value, not a decimal. Conversion to grams happens once
 * on save, and an untouched prefill keeps its stored grams rather than round-tripping through the
 * display unit (see [readGrowth]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowthSheet(
    accent: Color,
    childLabel: String,
    units: UnitPreferences,
    zone: ZoneId,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    initial: BabyEvent? = null,
    /** Offered inside the sheet while editing; null for a new record. */
    onDelete: (() -> Unit)? = null,
) {
    val colors = BabyLogTheme.colors
    val initialValues = GrowthValues(
        weightGrams = initial?.weightGrams,
        lengthMm = initial?.lengthMm,
        headMm = initial?.headMm,
    )
    // The stored row, rendered in the current unit — and the text that counts as "untouched".
    val seed = remember(units, initial) { growthFieldsFor(initialValues, units) }

    var startedAt by remember { mutableStateOf(initial?.startedAt ?: now) }
    var fields by remember(seed) { mutableStateOf(seed) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    val read = readGrowth(fields, units, initialValues)
    val lengthLabel = if (units.length == LengthUnit.CENTIMETRES) "cm" else "in"

    ActivitySheet(
        title = if (initial != null) "Edit measurement" else "Measurement",
        accent = accent,
        subtitle = "for $childLabel",
        saveEnabled = read.error == null && !read.values.isEmpty,
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.GROWTH,
                    startedAt = startedAt,
                    weightGrams = read.values.weightGrams,
                    lengthMm = read.values.lengthMm,
                    headMm = read.values.headMm,
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

        when (units.weight) {
            WeightUnit.KILOGRAMS -> TextFieldRow(
                label = "Weight (kg)",
                value = fields.weight,
                onValueChange = { typed -> fields = fields.copy(weight = decimalInput(typed)) },
                placeholder = "e.g. 6.35",
                keyboardType = KeyboardType.Decimal,
            )

            WeightUnit.POUNDS_OUNCES -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextFieldRow(
                    label = "Weight (lb)",
                    value = fields.pounds,
                    onValueChange = { typed -> fields = fields.copy(pounds = digits(typed)) },
                    placeholder = "e.g. 14",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                TextFieldRow(
                    label = "Weight (oz)",
                    value = fields.ounces,
                    onValueChange = { typed -> fields = fields.copy(ounces = digits(typed)) },
                    placeholder = "0-15",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        TextFieldRow(
            label = "Length ($lengthLabel)",
            value = fields.length,
            onValueChange = { typed -> fields = fields.copy(length = decimalInput(typed)) },
            placeholder = "Optional",
            keyboardType = KeyboardType.Decimal,
        )
        TextFieldRow(
            label = "Head circumference ($lengthLabel)",
            value = fields.head,
            onValueChange = { typed -> fields = fields.copy(head = decimalInput(typed)) },
            placeholder = "Optional",
            keyboardType = KeyboardType.Decimal,
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
                text = "Enter at least one measurement — weight, length or head.",
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

/** Digits and at most one decimal point for the ounce field. */
private fun digits(typed: String): String = typed.filter { it.isDigit() }
