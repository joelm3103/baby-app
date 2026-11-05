package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.babylog.data.repo.EventDraft
import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import app.babylog.domain.UnitPreferences
import app.babylog.domain.VolumeUnit
import app.babylog.domain.bottleAmount
import app.babylog.domain.dateOn
import app.babylog.domain.formatVolume
import app.babylog.domain.toMillilitres
import app.babylog.ui.common.glyphPainter
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.labelFor
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Below this a "bottle" is a typo; above it, a number that would wreck every total. */
private const val MIN_BOTTLE_ML = 1
private const val MAX_BOTTLE_ML = 1000

/**
 * Bottle feed: date, time, what was poured, what was left behind, formula brand, notes.
 *
 * **Two numbers, one subtraction** (Phase 26). The amount is what went into the bottle; the optional
 * leftover is what came back. The app subtracts ONCE — in [bottleAmount] — and stores what was drunk in
 * `amountMl` with the leftover beside it in `leftBehindMl`, so the poured volume is recoverable exactly and
 * no total has to re-interpret an old row. The line under the fields says the result before it is kept,
 * because a parent should be able to check the number rather than trust it.
 *
 * Phase 24's calculator pad is gone: the field takes a number, the arithmetic is the app's job rather than
 * the parent's, and the two facts the sum was made of are now recorded instead of discarded.
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

    // Editing seeds from the row being edited; logging seeds from the last POURED amount, because a parent
    // makes the same 6 oz again far more often than they make whatever part of it was drunk.
    //
    // The LEFTOVER is never prefilled from another feed: it is this feed's own fact, and a sheet that opened
    // claiming 1 oz was left before anything was poured would be inventing it. Editing is the one exception,
    // because there the number is already in the row.
    val seedPouredMl = initial?.pouredMl ?: initialAmountMl
    val initialAmountText = seedPouredMl?.let { editableAmount(it, units.volume) } ?: ""
    val initialLeftText = initial?.leftBehindMl?.let { editableAmount(it, units.volume) } ?: ""
    var amount by remember { mutableStateOf(initialAmountText) }
    var leftText by remember { mutableStateOf(initialLeftText) }

    // An UNTOUCHED prefill keeps its stored value. 90 ml renders as "3" fl oz, and re-parsing that would
    // store 89 ml — so merely opening a sheet and pressing Save would silently rewrite the amount.
    // Round-tripping through a coarser display unit must not change what is stored. The same guard now
    // covers the leftover, for the same reason.
    val pouredMl = resolveVolume(amount, initialAmountText, seedPouredMl, units.volume)
    val leftMl = resolveVolume(leftText, initialLeftText, initial?.leftBehindMl, units.volume)

    val stored = bottleAmount(pouredMl, leftMl)

    // Blank amount is allowed — a feed can be logged without an amount at all. Anything else must be a
    // plausible pour, so Save is disabled rather than writing a nonsense total.
    val pouredInRange = pouredMl == null || pouredMl.roundToInt() in MIN_BOTTLE_ML..MAX_BOTTLE_ML
    val leftWithoutAmount = leftMl != null && pouredMl == null
    val leftTooMuch = leftMl != null && pouredMl != null && leftMl > pouredMl
    val saveEnabled = stored != null && pouredInRange

    val errorText = when {
        leftWithoutAmount -> "Enter an amount first — left behind is what was left of it."
        leftTooMuch -> "Left behind cannot be more than the amount."
        !pouredInRange -> "Enter an amount between " +
            formatVolume(MIN_BOTTLE_ML.toDouble(), units.volume) + " and " +
            formatVolume(MAX_BOTTLE_ML.toDouble(), units.volume) + "."
        else -> null
    }

    // The line under the fields. A leftover of 0 was recorded and changes nothing, so it earns no bracket;
    // the bracket is the arithmetic the parent can check, and `(6 − 0)` is arithmetic that says nothing.
    //
    // These are VALUES, and the composable is called inside the content lambda below. Calling it here — even
    // through an inline `?.let` — emits into BottleSheet's own scope instead of the sheet's content column,
    // and the line is then drawn behind the sheet: measured on the phone on 2026-11-05, where the space was
    // there and the words were not.
    val consumedMl = stored?.consumedMl
    val consumedArithmetic = if (leftMl != null && leftMl > 0) {
        "${amount.trim()} \u2212 ${leftText.trim()}"
    } else {
        null
    }

    ActivitySheet(
        type = EventType.BOTTLE,
        title = if (initial != null) "Edit feed" else "Bottle feed",
        subtitle = "for $childLabel",
        saveEnabled = saveEnabled,
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.BOTTLE,
                    startedAt = startedAt,
                    amountMl = stored?.consumedMl,
                    leftBehindMl = stored?.leftBehindMl,
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

        PairedFieldRow {
            LabelledNumberField(
                label = "Amount (${volumeUnitLabel(units.volume)})",
                value = amount,
                onValueChange = { amount = decimalInput(it) },
                trailing = "*",
                isError = errorText != null && (leftWithoutAmount || !pouredInRange),
                modifier = Modifier.weight(1f),
            )
            LabelledNumberField(
                label = "Left behind (${volumeUnitLabel(units.volume)})",
                value = leftText,
                onValueChange = { leftText = decimalInput(it) },
                trailing = "Optional",
                isError = leftTooMuch || leftWithoutAmount,
                modifier = Modifier.weight(1f),
            )
        }

        if (presets.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { presetMl ->
                    ChoiceChip(
                        label = formatVolume(presetMl.toDouble(), units.volume),
                        // A preset picks the POUR, so it is compared against the amount field: selecting
                        // "6 oz" and typing a leftover must not un-highlight the chip that was tapped.
                        selected = pouredMl?.roundToInt() == presetMl,
                        accent = accent,
                        onClick = { amount = editableAmount(presetMl, units.volume) },
                    )
                }
            }
        }

        if (consumedMl != null) {
            ConsumedLine(
                consumedText = formatVolume(consumedMl.toDouble(), units.volume),
                arithmetic = consumedArithmetic,
                accent = accent,
            )
        }

        if (errorText != null) {
            Text(
                text = errorText,
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

/**
 * `Consumed: 5 oz (6 − 1)` — what will be stored, said before it is stored.
 *
 * The word and the value are `labelFor(accent)` rather than the raw accent: on the light theme the bottle's
 * `#FFC857` on a white sheet measures about 1.5:1 and is unreadable, while the lifted token clears 4.5:1 on
 * both themes. The arithmetic half is `textSecondary`, because `(6 − 1)` is the working, not the answer.
 */
@Composable
private fun ConsumedLine(
    consumedText: String,
    arithmetic: String?,
    accent: Color,
) {
    val colors = BabyLogTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = EventType.BOTTLE.glyphPainter(),
            contentDescription = null,
            tint = colors.labelFor(accent),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Consumed:",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = consumedText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.labelFor(accent),
        )
        if (arithmetic != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "($arithmetic)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = 12.dp).height(1.dp))
}

/**
 * The millilitres a field holds, honouring the untouched-prefill rule.
 *
 * [prefillText] is what the field was opened with and [prefillMl] what that text was rendered FROM. When
 * the two still match, the stored value wins: 90 ml shows as "3" fl oz, and reading that back would store
 * 89 ml. Blank is `null` — no volume, not zero.
 */
private fun resolveVolume(text: String, prefillText: String, prefillMl: Int?, unit: VolumeUnit): Double? = when {
    text.isBlank() -> null
    text == prefillText && prefillMl != null -> prefillMl.toDouble()
    else -> text.trim().toDoubleOrNull()?.let { toMillilitres(it, unit) }
}

/** The volume as editable text in the chosen unit: "120" for ml, "4.1" for fl oz. */
private fun editableAmount(millilitres: Int, unit: VolumeUnit): String =
    formatVolume(millilitres.toDouble(), unit).substringBefore(' ')
