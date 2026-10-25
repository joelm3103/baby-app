package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import app.babylog.domain.UnitPreferences
import app.babylog.domain.VolumeUnit
import app.babylog.domain.dateOn
import app.babylog.domain.formatVolume
import app.babylog.domain.toMillilitres
import app.babylog.ui.theme.BabyLogTheme
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Below this a "bottle" is a typo; above it, a number that would wreck every total. */
private const val MIN_BOTTLE_ML = 1
private const val MAX_BOTTLE_ML = 1000

/**
 * Bottle feed: date, time, amount, formula brand, notes.
 *
 * The amount is FREE TEXT in the display unit, with chips for the stored presets. A stepper was the
 * wrong control here: it cannot express 135 ml, and it put a common number twelve taps away. The value
 * is converted to canonical millilitres once on save, so the unit preference never rewrites storage.
 *
 * [initial] is set when a row is being EDITED rather than a new feed logged: the sheet then opens on
 * that row's values and saves back onto it (see `EventDraft.applyTo`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BottleSheet(
    accent: Color,
    childLabel: String,
    units: UnitPreferences,
    presets: List<Int>,
    zone: ZoneId,
    now: Long,
    initialAmountMl: Int?,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    initial: BabyEvent? = null,
    /** Offered inside the sheet while editing; null for a new record. */
    onDelete: (() -> Unit)? = null,
) {
    var startedAt by remember { mutableStateOf(initial?.startedAt ?: now) }
    var brand by remember { mutableStateOf(initial?.formulaBrand.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    // Editing seeds from the row being edited; logging seeds from the last feed, because a parent logs
    // the same 120 ml far more often than a new number.
    val seedAmountMl = initial?.amountMl ?: initialAmountMl
    val initialText = seedAmountMl?.let { editableAmount(it, units.volume) } ?: ""
    var amount by remember { mutableStateOf(initialText) }

    // Blank is allowed: a bottle can be logged without an amount. Anything else must be a plausible
    // number, so Save is disabled rather than writing a nonsense total.
    //
    // An UNTOUCHED prefill keeps its stored value. 90 ml renders as "3" fl oz, and re-parsing that
    // would store 89 ml — so merely opening a sheet and pressing Save would silently rewrite the
    // amount. Round-tripping through a coarser display unit must not change what is stored.
    val millilitres = when {
        amount.isBlank() -> null
        amount == initialText && seedAmountMl != null -> seedAmountMl
        else -> amount.trim().toDoubleOrNull()?.let { toMillilitres(it, units.volume).roundToInt() }
    }
    val amountOk = amount.isBlank() || (millilitres != null && millilitres in MIN_BOTTLE_ML..MAX_BOTTLE_ML)

    ActivitySheet(
        title = if (initial != null) "Edit feed" else "Bottle feed",
        accent = accent,
        subtitle = "for $childLabel",
        saveEnabled = amountOk,
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.BOTTLE,
                    startedAt = startedAt,
                    amountMl = millilitres.takeIf { amountOk },
                    formulaBrand = brand.trim().ifEmpty { null },
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
            label = "Amount (${volumeUnitLabel(units.volume)})",
            value = amount,
            onValueChange = { typed -> amount = typed.filter { it.isDigit() || it == '.' } },
            placeholder = "e.g. ${editableAmount(120, units.volume)}",
            keyboardType = KeyboardType.Decimal,
        )
        if (presets.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { presetMl ->
                    ChoiceChip(
                        label = formatVolume(presetMl.toDouble(), units.volume),
                        selected = millilitres == presetMl,
                        accent = accent,
                        onClick = { amount = editableAmount(presetMl, units.volume) },
                    )
                }
            }
        }
        if (!amountOk) {
            Text(
                text = "Enter an amount between " +
                    formatVolume(MIN_BOTTLE_ML.toDouble(), units.volume) + " and " +
                    formatVolume(MAX_BOTTLE_ML.toDouble(), units.volume) + ".",
                style = MaterialTheme.typography.bodySmall,
                color = BabyLogTheme.colors.danger,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        TextFieldRow(
            label = "Formula brand",
            value = brand,
            onValueChange = { brand = it },
            placeholder = "Optional",
        )
        TextFieldRow(
            label = "Notes",
            value = note,
            onValueChange = { note = it },
            placeholder = "Optional",
            singleLine = false,
        )
    }
}

/** The amount as editable text in the chosen unit: "120" for ml, "4.1" for fl oz. */
private fun editableAmount(millilitres: Int, unit: VolumeUnit): String =
    formatVolume(millilitres.toDouble(), unit).substringBefore(' ')
