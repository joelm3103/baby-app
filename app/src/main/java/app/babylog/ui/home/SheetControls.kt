package app.babylog.ui.home

import app.babylog.ui.LocalTimeFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.babylog.domain.VolumeUnit
import app.babylog.domain.clockTime
import app.babylog.domain.timeOfDayOn
import app.babylog.ui.common.rememberHaptics
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
 * A typed number in a box whose label sits INSIDE it, above the value (Phase 26).
 *
 * This is the shape the owner's reference sheet uses for the bottle's two numbers, and it is deliberately
 * not [TextFieldRow]'s floating label: two of these sit side by side, and a floating label that moves to
 * the border on focus would make the pair jump the moment a parent started typing. Drawn here rather than
 * reached for from Material so both boxes keep one geometry whether they are empty, filled or in error.
 *
 * [trailing] carries the small word beside the label — `Optional` on the leftover field, `*` on the amount
 * that cannot be left blank. It is `textSecondary`, because it qualifies the label rather than being data.
 */
@Composable
fun LabelledNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    isError: Boolean = false,
) {
    val colors = BabyLogTheme.colors
    val shape = RoundedCornerShape(4.dp)
    val focus = remember { FocusRequester() }
    Column(
        // Tapping anywhere in the box focuses the number: the label lives inside the border, so a tap on it
        // reads as a tap on the field, and leaving it inert would be a second tap for no reason.
        modifier = modifier
            .border(1.dp, if (isError) colors.danger else colors.divider, shape)
            .clickable { focus.requestFocus() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) colors.danger else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .focusRequester(focus),
        )
    }
}

/**
 * Two [LabelledNumberField]s on one row — the amount and what was left behind.
 *
 * Kept here rather than inlined in the sheet so the 8dp gap and the shared top margin sit in the same file
 * as the fields themselves; a second sheet that needs a pair gets the same geometry for free.
 */
@Composable
fun PairedFieldRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * Editable time, defaulting to now.
 *
 * Opens [TimeOfDayDialog], which carries its own switch: the dial, the wheels, and the two-field
 * keyboard. It used to be the keyboard alone, chosen because "the dial does not fit a phone dialog at
 * small widths" — Phase 21 reverses that, so the dial is now measured on the phone rather than assumed.
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

/**
 * The one time dialog in the app, and the only place a time is edited: every logging sheet reaches it through
 * [TimeRow], and the reminder sheet reaches it for a fixed-time rule.
 *
 * **Three inputs, one switch.** The dial (Material3's `TimePicker`) and the wheels (ours — Material3 has no
 * wheel) are the two inputs the owner asked to move between; the original two-field keyboard is kept because
 * it is the only one that can type an exact minute without scrolling. The button in the title bar carries the
 * mode the tap LEADS TO, so its content description says what will happen rather than what is happening.
 *
 * **Nothing is persisted and the dialog opens on the dial every time.** A remembered mode is a preference the
 * owner declined, and a dialog that opens differently depending on last week's habit is the kind of hidden
 * state this app avoids.
 *
 * **One pair of numbers, two objects.** [hour]/[minute] are the wheel's value and the hand-off between modes;
 * `TimePickerState` is the Material modes' value. Before leaving a Material mode its numbers are copied into
 * the pair, and the pair keys the state — so cycling never loses an edit, and `TimePickerState.hour` never
 * needs a setter it does not have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayDialog(
    instantMs: Long,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val colors = BabyLogTheme.colors
    val local = Instant.ofEpochMilli(instantMs).atZone(zone).toLocalTime()
    // Match the selected clock format in every mode: the AM/PM column and the dial's inner ring exist only
    // when the parent reads a 12-hour clock.
    val is24Hour = LocalTimeFormat.current == app.babylog.domain.TimeFormat.TWENTY_FOUR_HOUR

    var hour by remember { mutableIntStateOf(local.hour) }
    var minute by remember { mutableIntStateOf(local.minute) }
    var mode by remember { mutableStateOf(TimeInputMode.DIAL) }

    val state = key(hour, minute) {
        rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24Hour)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // Read the mode that was actually being edited: they are two objects, and only one of them
                // has been touched.
                if (mode == TimeInputMode.WHEEL) onConfirm(hour, minute) else onConfirm(state.hour, state.minute)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // The reference's own headings, and they differ for a reason: a dial selects, a field is
                    // entered into.
                    text = if (mode == TimeInputMode.TEXT) "Enter time" else "Select time",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                val next = nextTimeInputMode(mode)
                IconButton(
                    onClick = {
                        // Capture this mode's edit before leaving it, so the next mode opens on it.
                        if (mode != TimeInputMode.WHEEL) {
                            hour = state.hour
                            minute = state.minute
                        }
                        mode = next
                    },
                ) {
                    Icon(
                        painter = painterResource(next.iconRes()),
                        // Names the DESTINATION: "Switch to wheels" is an instruction, "wheels" is not.
                        contentDescription = "Switch to ${next.spoken}",
                        tint = colors.textSecondary,
                    )
                }
            }
        },
        text = {
            when (mode) {
                TimeInputMode.DIAL -> TimePicker(state = state, modifier = Modifier.fillMaxWidth())
                TimeInputMode.WHEEL -> WheelTimePicker(
                    hour = hour,
                    minute = minute,
                    is24Hour = is24Hour,
                    onHour = { hour = it },
                    onMinute = { minute = it },
                    // The half-day shift is done here, on the one number that owns it, rather than by a
                    // column converting an index it would first have to be told.
                    onHalfDay = { pm -> hour = if (pm) (hour % 12) + 12 else hour % 12 },
                )
                TimeInputMode.TEXT -> TimeInput(state = state)
            }
        },
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
    // A switch answers a yes/no, so it gets the toggle buzz rather than the ordinary tap.
    val haptics = rememberHaptics()
    InputRow(
        label = label,
        onClick = {
            haptics.toggle()
            onCheckedChange(!checked)
        },
        chevron = false,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.toggle()
                onCheckedChange(it)
            },
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
    // Every chip in the app is this composable -- activity tabs, ranges, filters, diaper kinds, meal slots --
    // so the tap that selects one is felt through one line of code everywhere.
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) accent else colors.surfaceRaised)
            .clickable {
                haptics.tap()
                onClick()
            }
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
    /**
     * A fixed width for the value slot, or null to size it to its text.
     *
     * The reminder interval needs it: `1h` and `1h 30m` are different widths, so without a slot the -/+ buttons
     * shuffle sideways every time the value changes - which is exactly what the phone showed. A Settings stepper
     * whose values are all one width does not need it.
     */
    valueWidth: androidx.compose.ui.unit.Dp? = null,
) {
    val colors = BabyLogTheme.colors
    InputRow(label = label) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepButton("\u2212", onDecrease)
            Box(
                contentAlignment = Alignment.Center,
                modifier = if (valueWidth != null) Modifier.width(valueWidth) else Modifier,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
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

/**
 * One chip in a MULTI-select set — the same visual language as [ChoiceChip] (accent fill, `onAccent`
 * content, raw accent icon when off) because it is the same kind of answer, just not exclusive.
 *
 * A separate component rather than a flag on [ChoiceChip]: selection state is a different type here
 * (`Boolean` from a set rather than `T?`), and a shared composable with two meanings is how a caller ends
 * up passing the wrong one. Phase 13.3 needed it for a dirty nappy's colour and consistency.
 */
@Composable
fun MultiChoiceChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A leading shape. Tinted like [ChoiceChip]'s: raw accent off, `onAccent` on the accent fill. */
    icon: Painter? = null,
    /** 14dp matches [ChoiceChip]; the filled stool silhouettes read better at 16dp. */
    iconSize: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val colors = BabyLogTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) accent else colors.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (selected) colors.onAccent else accent,
                modifier = Modifier.size(iconSize),
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
 * A labelled MULTI-select chip set that wraps rather than clips, for the same reason [ChipRow] does:
 * seven consistency words — one of them "Mucousy" — do not fit a phone's width in one row, and an
 * equal-weight row would squeeze them off the edge instead of flowing onto a second line.
 *
 * Selection is a set, and the caller receives the TOGGLED set rather than being told "select this": a
 * multi-select field has to be clearable field-by-field, which was the defect [ChipRow]'s `clearLabel`
 * was added to fix for the single-select case.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChipRow(
    label: String,
    options: List<T>,
    selected: Set<T>,
    accent: Color,
    labelOf: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Composable because each shape comes from `painterResource`, and a Painter needs a context. */
    painterOf: (@Composable (T) -> Painter)? = null,
    iconSize: androidx.compose.ui.unit.Dp = 14.dp,
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
            options.forEach { option ->
                MultiChoiceChip(
                    label = labelOf(option),
                    selected = option in selected,
                    accent = accent,
                    onClick = { onToggle(option) },
                    icon = painterOf?.invoke(option),
                    iconSize = iconSize,
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}
