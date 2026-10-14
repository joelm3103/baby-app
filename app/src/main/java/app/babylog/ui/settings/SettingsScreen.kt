package app.babylog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.babylog.domain.Child
import app.babylog.domain.LengthUnit
import app.babylog.domain.NightWindow
import app.babylog.domain.Sex
import app.babylog.domain.TERM_GESTATION_DAYS
import app.babylog.domain.ThemeMode
import app.babylog.domain.UnitPreferences
import app.babylog.domain.UnitSystem
import app.babylog.domain.VolumeUnit
import app.babylog.domain.WeightUnit
import app.babylog.ui.home.ChipRow
import app.babylog.ui.home.DangerButton
import app.babylog.ui.home.DateRow
import app.babylog.ui.home.InputRow
import app.babylog.ui.home.InputValue
import app.babylog.ui.home.SecondaryButton
import app.babylog.ui.home.SectionTitle
import app.babylog.ui.home.StepperRow
import app.babylog.ui.home.SwitchRow
import app.babylog.ui.home.TextFieldRow
import app.babylog.ui.theme.BabyLogTheme
import java.time.LocalDate
import java.util.Locale

/** 20 weeks. Below this is not a viable gestational age, so the stepper stops. */
private const val MIN_GESTATION_DAYS = 140

/** "18:00" — the night window is hour-granular, so minutes would be false precision. */
private fun hourLabel(hour: Int): String = String.format(Locale.ENGLISH, "%02d:00", hour)

/**
 * Settings: appearance, units, the children and the backup. One scrolling screen rather than a list of
 * sub-screens, because every setting here is a single tap and the app is meant to stay small.
 *
 * Stateless on purpose — it takes values and reports intent, so the ViewModel owns every write.
 */
@Composable
fun SettingsScreen(
    children: List<Child>,
    units: UnitPreferences,
    nightWindow: NightWindow,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onUnitSystem: (UnitSystem) -> Unit,
    onLengthUnit: (LengthUnit) -> Unit,
    onWeightUnit: (WeightUnit) -> Unit,
    onVolumeUnit: (VolumeUnit) -> Unit,
    onNightStart: (Int) -> Unit,
    onNightEnd: (Int) -> Unit,
    onUpdateChild: (Child) -> Unit,
    onAddChild: () -> Unit,
    onArchiveChild: (Child) -> Unit,
    onDeleteChildPermanently: (Child) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    message: String?,
    contentPadding: PaddingValues,
) {
    val colors = BabyLogTheme.colors
    // Settings chips use the neutral UI colour, never an activity accent — the accents mean activities.
    val selection = colors.navActive

    // The one destructive action is gated by a dialog that NAMES the child.
    var pendingDelete by remember { mutableStateOf<Child?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)

        SectionTitle("Appearance")
        ChipRow(
            label = "Theme",
            options = ThemeMode.entries,
            selected = theme,
            accent = selection,
            labelOf = { mode -> mode.name.lowercase().replaceFirstChar { it.uppercase() } },
            onSelect = onTheme,
        )

        SectionTitle("Units")
        // Only the two presets are offered: picking a family individually is what makes it Custom.
        ChipRow(
            label = "Preset",
            options = listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL),
            selected = units.system.takeIf { it != UnitSystem.CUSTOM },
            accent = selection,
            labelOf = { system -> system.name.lowercase().replaceFirstChar { it.uppercase() } },
            onSelect = onUnitSystem,
        )
        ChipRow(
            label = "Length",
            options = LengthUnit.entries,
            selected = units.length,
            accent = selection,
            labelOf = { if (it == LengthUnit.INCHES) "Inches" else "Centimetres" },
            onSelect = onLengthUnit,
        )
        ChipRow(
            label = "Weight",
            options = WeightUnit.entries,
            selected = units.weight,
            accent = selection,
            labelOf = { if (it == WeightUnit.POUNDS_OUNCES) "lb & oz" else "kg" },
            onSelect = onWeightUnit,
        )
        ChipRow(
            label = "Volume",
            options = VolumeUnit.entries,
            selected = units.volume,
            accent = selection,
            labelOf = { if (it == VolumeUnit.FLUID_OUNCES) "fl oz" else "ml" },
            onSelect = onVolumeUnit,
        )
        Text(
            text = "Temperature is always Celsius \u2014 there is no Fahrenheit option, on purpose.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionTitle("Day & night")
        Text(
            text = "Overnight changes are counted as night in the History diaper split.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // Hours wrap at midnight rather than clamping, because that is how a clock reads: 23:00 -> 00:00.
        StepperRow(
            label = "Night starts",
            value = hourLabel(nightWindow.startHour),
            onDecrease = { onNightStart((nightWindow.startHour + 23) % 24) },
            onIncrease = { onNightStart((nightWindow.startHour + 1) % 24) },
        )
        StepperRow(
            label = "Night ends",
            value = hourLabel(nightWindow.endHour),
            onDecrease = { onNightEnd((nightWindow.endHour + 23) % 24) },
            onIncrease = { onNightEnd((nightWindow.endHour + 1) % 24) },
        )
        if (nightWindow.isEmpty) {
            Text(
                text = "Start and end are the same, so nothing counts as night.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionTitle("Children")
        if (children.isEmpty()) {
            Text(
                text = "No children yet. Add one to start logging.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        children.forEach { child ->
            ChildEditor(
                child = child,
                selection = selection,
                onUpdate = onUpdateChild,
                onArchive = { onArchiveChild(child) },
                onDelete = { pendingDelete = child },
            )
        }
        SecondaryButton(
            label = "Add child",
            onClick = onAddChild,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        SectionTitle("Data")
        InputRow(label = "Export backup", onClick = onExport) { InputValue("JSON") }
        InputRow(label = "Import backup", onClick = onImport) { InputValue("Replaces everything") }
        Text(
            text = "The backup includes both children, every event (including deleted ones) and the food " +
                "list. Keep it somewhere that is not this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        } ?: Spacer(Modifier.height(24.dp))
    }

    pendingDelete?.let { child ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${child.name} permanently?") },
            text = {
                Text(
                    "This erases ${child.name} and every event logged for them. It cannot be undone: the " +
                        "rows are removed, not archived. Archive instead if you might want them back.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteChildPermanently(child)
                    pendingDelete = null
                }) {
                    Text("Delete ${child.name}", color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChildEditor(
    child: Child,
    selection: Color,
    onUpdate: (Child) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = BabyLogTheme.colors
    // Local text state keyed by id, so a database emission cannot move the caret mid-word.
    var name by remember(child.id) { mutableStateOf(child.name) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        TextFieldRow(
            label = "Name",
            value = name,
            onValueChange = {
                name = it
                onUpdate(child.copy(name = it))
            },
        )
        DateRow(label = "Birth date", epochDay = child.birthDate.toEpochDay()) { epochDay ->
            onUpdate(child.copy(birthDate = LocalDate.ofEpochDay(epochDay)))
        }
        ChipRow(
            label = "Sex",
            options = Sex.entries,
            selected = child.sex,
            accent = selection,
            labelOf = { if (it == Sex.BOY) "Boy" else "Girl" },
            onSelect = { onUpdate(child.copy(sex = it)) },
        )
        StepperRow(
            label = "Gestational age",
            value = child.gestationalWeeksLabel,
            onDecrease = {
                onUpdate(child.copy(gestationalAgeDays = (child.gestationalAgeDays - 7).coerceAtLeast(MIN_GESTATION_DAYS)))
            },
            onIncrease = {
                onUpdate(child.copy(gestationalAgeDays = (child.gestationalAgeDays + 7).coerceAtMost(TERM_GESTATION_DAYS)))
            },
        )
        SwitchRow(
            label = "Use adjusted age",
            checked = child.useAdjustedAge,
            accent = selection,
            onCheckedChange = { onUpdate(child.copy(useAdjustedAge = it)) },
        )
        if (child.isPreterm) {
            Text(
                text = "${child.prematureDays} days early",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(label = "Archive", onClick = onArchive, modifier = Modifier.weight(1f))
            DangerButton(label = "Delete permanently", onClick = onDelete, modifier = Modifier.weight(1f))
        }
    }
}
