package app.babylog.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.babylog.data.repo.EventDraft
import app.babylog.domain.EventType
import app.babylog.domain.UnitPreferences
import app.babylog.domain.formatVolume
import app.babylog.domain.fromMillilitres
import app.babylog.domain.toMillilitres
import app.babylog.domain.volumeStep
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Bottle feed: time, amount, formula brand, notes.
 *
 * The amount is held in the **display** unit and converted to millilitres once on save. Stepping a
 * canonical ml value by 0.5 oz would round on every press and drift ("4 oz" -> 4.1 -> 4.6); stepping
 * the display value keeps the buttons landing on the numbers a parent actually reads off a bottle.
 * Storage is still canonical ml, so the preference never rewrites what is stored.
 */
@Composable
fun BottleSheet(
    accent: Color,
    units: UnitPreferences,
    zone: ZoneId,
    now: Long,
    initialAmountMl: Int?,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    var startedAt by remember { mutableStateOf(now) }
    var brand by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var displayAmount by remember {
        mutableStateOf(initialAmountMl?.let { fromMillilitres(it.toDouble(), units.volume) } ?: 0.0)
    }

    ActivitySheet(
        title = "Bottle feed",
        accent = accent,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.BOTTLE,
                    startedAt = startedAt,
                    amountMl = displayAmount.takeIf { it > 0.0 }
                        ?.let { toMillilitres(it, units.volume).roundToInt() },
                    formulaBrand = brand.trim().ifEmpty { null },
                    note = note.trim().ifEmpty { null },
                ),
            )
        },
    ) {
        TimeRow(instantMs = startedAt, zone = zone, onChange = { startedAt = it })
        StepperRow(
            label = "Amount",
            value = if (displayAmount > 0.0) {
                formatVolume(toMillilitres(displayAmount, units.volume), units.volume)
            } else {
                "\u2014"
            },
            onDecrease = {
                displayAmount = (displayAmount - volumeStep(units.volume)).coerceAtLeast(0.0)
            },
            onIncrease = { displayAmount += volumeStep(units.volume) },
        )
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
