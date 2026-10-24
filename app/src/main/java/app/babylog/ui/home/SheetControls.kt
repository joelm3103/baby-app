package app.babylog.ui.home

import app.babylog.ui.LocalTimeFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.babylog.domain.VolumeUnit
import app.babylog.domain.clockTime
import app.babylog.domain.timeOfDayOn
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The input primitives every sheet is built from. They are deliberately plain: a label on the left and
 * the value or control on the right, separated by a hairline. The sheet's job is to ask a handful of
 * one-line questions, so anything more elaborate would be decoration.
 */

/** Label left, value/control right, `›` when tapping pushes something. Hairline underneath. */
@Composable
fun InputRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    // A chevron means "this pushes a screen". A row that toggles or edits in place must pass false,
    // or the arrow promises navigation the tap does not deliver.
    chevron: Boolean = onClick != null,
    trailing: @Composable () -> Unit,
) {
    val colors = BabyLogTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                trailing()
                if (chevron) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

/** The muted right-hand value on an input row. */
@Composable
fun InputValue(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = BabyLogTheme.colors.textSecondary,
    )
}

/** Free-text entry. Used for the formula brand, a custom food name, a bottle amount and notes. */
@Composable
fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    /** `Decimal` for a numeric field, so the keypad shows digits rather than a full keyboard. */
    keyboardType: KeyboardType = KeyboardType.Text,
    /**
     * Where to send focus when this field appears, e.g. a name the parent just asked to type. Without
     * it the field is on screen but the keyboard is still down, which is a second tap for no reason.
     */
    focusRequester: FocusRequester? = null,
    /**
     * Masks the field. Only the sync password uses this — everything else in the app is a log entry, and
     * a masked bottle amount would be worse than useless.
     */
    isPassword: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = if (placeholder.isBlank()) null else {
                { Text(placeholder, color = BabyLogTheme.colors.textSecondary) }
            },
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
        HorizontalDivider(color = BabyLogTheme.colors.divider, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * Digits and at most one decimal point, so a keypad cannot produce "6.3.5" or "38.2.1".
 *
 * Shared by every sheet that takes a typed number — growth and health both had their own copy of these
 * three lines, which is how two fields end up disagreeing about what a valid number looks like.
 */
internal fun decimalInput(typed: String): String {
    val cleaned = typed.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot < 0) return cleaned
    return cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).filter { it.isDigit() }
}

/**
 * Editable time, defaulting to now. Opens a compact dial-free picker: `TimeInput` (two fields) rather
 * than the clock dial, because the dial does not fit a phone dialog at small widths and this needs to
 * be right the first time without a device iteration to discover a clip.
 */
@Composable
fun TimeRow(
    instantMs: Long,
    zone: ZoneId,
    onChange: (Long) -> Unit,
    label: String = "Time",
) {
    var picking by remember { mutableStateOf(false) }
    InputRow(label = label, onClick = { picking = true }) {
        InputValue(clockTime(instantMs, zone, LocalTimeFormat.current))
    }
    if (picking) {
        TimeOfDayDialog(
            instantMs = instantMs,
            zone = zone,
            onDismiss = { picking = false },
            onConfirm = { hour, minute ->
                onChange(timeOfDayOn(instantMs, hour, minute, zone))
                picking = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOfDayDialog(
    instantMs: Long,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val local = Instant.ofEpochMilli(instantMs).atZone(zone).toLocalTime()
    // Match the selected clock format in both the picker and the row.
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = LocalTimeFormat.current == app.babylog.domain.TimeFormat.TWENTY_FOUR_HOUR,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimeInput(state = state) },
    )
}

/** A boolean setting, e.g. the diaper rash flag. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = BabyLogTheme.colors
    InputRow(label = label, onClick = { onCheckedChange(!checked) }, chevron = false) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = accent,
            ),
        )
    }
}

/** One of a small set of discrete answers (Wet/Dirty/Mixed, Breakfast/Lunch/Dinner/Snack). */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * An optional leading glyph. Null by default, so every existing chip — ranges, meal slots, diaper
     * kinds, growth metrics — keeps exactly the metrics it had; only the activity chips pass one.
     *
     * Tinted with the RAW accent when the chip is off and `onAccent` when it is on: an accent glyph on
     * `surfaceRaised` measures 5.1-8.4:1 dark and 4.2-6.5:1 light, above the 3:1 a graphic needs, while
     * the accent-filled selected chip measures 6.7-11.2:1 dark and 4.7-7.3:1 light against `onAccent`.
     */
    icon: Painter? = null,
) {
    val colors = BabyLogTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) accent else colors.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (selected) colors.onAccent else accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onAccent else colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A labelled set of chips that WRAP rather than clip. "Breakfast" next to three other meal chips does
 * not fit four-across on a phone, and an equal-weight row would silently squeeze it — so the chips size
 * to their text and flow onto a second line when they must.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T?,
    accent: Color,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * When set, a chip that CLEARS the selection is shown first and reads as selected while nothing is
     * chosen. Without it a row of chips is a one-way door: the solids reaction could be set but never
     * unset, so "no reaction" was unrepresentable once you had tapped once.
     */
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    val colors = BabyLogTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (clearLabel != null && onClear != null) {
                ChoiceChip(
                    label = clearLabel,
                    selected = selected == null,
                    accent = accent,
                    onClick = onClear,
                )
            }
            options.forEach { option ->
                ChoiceChip(
                    label = labelOf(option),
                    selected = option == selected,
                    accent = accent,
                    onClick = { onSelect(option) },
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

/** A numeric value with unit-appropriate steps, e.g. the bottle amount (10 ml / 0.5 oz). */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colors = BabyLogTheme.colors
    InputRow(label = label) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepButton("\u2212", onDecrease)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            StepButton("+", onIncrease)
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    val colors = BabyLogTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.surfaceRaised)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
    }
}

// ---- settings controls ----------------------------------------------------

/** A section heading on the Settings screen. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = BabyLogTheme.colors.textPrimary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}

/** "ml" / "fl oz" — the short unit label shown beside an amount, in one place. */
fun volumeUnitLabel(unit: VolumeUnit): String = when (unit) {
    VolumeUnit.MILLILITRES -> "ml"
    VolumeUnit.FLUID_OUNCES -> "fl oz"
}

/** A date, edited through the platform picker. Stored as an epoch day — a birth date has no time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRow(label: String, epochDay: Long, onChange: (Long) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(epochDay)
    InputRow(label = label, onClick = { picking = true }) {
        InputValue(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)))
    }
    if (picking) {
        // The picker speaks UTC-midnight millis; convert BOTH ways through UTC so the day cannot slip.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** A neutral action: visible, without competing with an accent-filled primary. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BabyLogTheme.colors
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Destructive actions only — the one use of the `danger` token. Filled with `danger` and labelled in
 * `onAccent`, measured at 6.85:1 dark and 6.54:1 light.
 */
@Composable
fun DangerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BabyLogTheme.colors
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.onAccent,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.danger)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
