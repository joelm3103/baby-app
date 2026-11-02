package app.babylog.ui.settings

import app.babylog.domain.TimeFormat
import app.babylog.domain.TemperatureUnit
import app.babylog.ui.LocalTimeFormat
import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.babylog.data.sync.SyncState
import app.babylog.BuildConfig
import app.babylog.domain.ABOUT_CREDITS
import app.babylog.domain.Child
import app.babylog.domain.SOURCE_REPOSITORY_URL
import app.babylog.domain.aboutVersionLine
import app.babylog.domain.LengthUnit
import app.babylog.domain.EVENT_DISPLAY_ORDER
import app.babylog.domain.EventType
import app.babylog.domain.REMINDER_TYPES
import app.babylog.domain.displayName
import app.babylog.domain.NightWindow
import app.babylog.domain.Sex
import app.babylog.domain.TERM_GESTATION_DAYS
import app.babylog.domain.ThemeMode
import app.babylog.domain.UnitPreferences
import app.babylog.domain.UnitSystem
import app.babylog.domain.VolumeUnit
import app.babylog.domain.WeightUnit
import app.babylog.domain.formatVolume
import app.babylog.domain.toMillilitres
import app.babylog.ui.home.ChipRow
import app.babylog.ui.home.DangerButton
import app.babylog.ui.home.DateRow
import app.babylog.ui.home.InputRow
import app.babylog.ui.home.InputValue
import app.babylog.ui.home.SecondaryButton
import app.babylog.ui.home.StepperRow
import app.babylog.data.reminders.ReminderText
import app.babylog.ui.home.SwitchRow
import app.babylog.ui.home.TextFieldRow
import app.babylog.ui.home.volumeUnitLabel
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import app.babylog.ui.theme.accentFor
import app.babylog.ui.theme.Spacing
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** 20 weeks. Below this is not a viable gestational age, so the stepper stops. */
private const val MIN_GESTATION_DAYS = 140

/** A preset above this is a typo, not a bottle. */
private const val MAX_PRESET_ML = 1000

/**
 * Which of Phase 15's two resets a dialog is asking about.
 *
 * One enum rather than two booleans: the two confirmations say different things, and a `server: Boolean`
 * threaded through the screen is exactly how the wrong sentence ends up on the wrong button.
 */
private enum class ResetRequest { LOCAL, SERVER }

/** "18:00" — the night window is hour-granular, so minutes would be false precision. */
@Composable
private fun hourLabel(hour: Int): String = java.time.LocalTime.of(hour, 0).format(
    java.time.format.DateTimeFormatter.ofPattern(
        if (LocalTimeFormat.current == TimeFormat.TWELVE_HOUR) "h:mm a" else "HH:mm", Locale.ENGLISH))

/** The theme's name as it is offered on the chip row, so the menu row and the chip cannot disagree. */
private fun themeLabel(mode: ThemeMode): String =
    mode.name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Settings: a category menu, and one submenu per category.
 *
 * Every setting the app has is still here, under the name it had; what changed is how it is reached.
 * The menu lists each group with a one-line summary of its current value, so the answer to "which
 * units am I on" is legible without opening anything, and each name opens that group alone.
 *
 * There is exactly one card per view now, which is why the old alternating `surfaceRaised` shading is
 * gone: it existed to separate consecutive cards down one long scroll, and there is no longer a
 * second card for a boundary to fall between.
 *
 * Stateless about navigation and about every write: the section shown, and the state a back press
 * lands on, are owned by the shell (`SettingsNav`), and this screen reports intent.
 */
@Composable
fun SettingsScreen(
    children: List<Child>,
    units: UnitPreferences,
    nightWindow: NightWindow,
    theme: ThemeMode,
    presets: List<Int>,
    /**
     * The medications worth one tap in the health sheet, in the order they were added.
     *
     * Empty until configured, deliberately: the app does not suggest a medicine or a dose. See
     * `SettingsRepository.medicationPresets`.
     */
    medicationPresets: List<String> = emptyList(),
    /** The activity cards the Activity tab leaves out. Empty means every activity shows. */
    hiddenActivities: Set<EventType> = emptySet(),
    /** Activities with at least one enabled reminder, so hiding one can warn before it cancels. */
    activitiesWithReminders: Set<EventType> = emptySet(),
    onSetActivityHidden: (EventType, Boolean) -> Unit = { _, _ -> },
    onTheme: (ThemeMode) -> Unit,
    onUnitSystem: (UnitSystem) -> Unit,
    onLengthUnit: (LengthUnit) -> Unit,
    onWeightUnit: (WeightUnit) -> Unit,
    onVolumeUnit: (VolumeUnit) -> Unit,
    onTemperatureUnit: (TemperatureUnit) -> Unit = {},
    onTimeFormat: (TimeFormat) -> Unit = {},
    onNightStart: (Int) -> Unit,
    onNightEnd: (Int) -> Unit,
    onAddPreset: (Int) -> Unit,
    onRemovePreset: (Int) -> Unit,
    onAddMedicationPreset: (String) -> Unit = {},
    onRemoveMedicationPreset: (String) -> Unit = {},
    onUpdateChild: (Child) -> Unit,
    onAddChild: () -> Unit,
    onArchiveChild: (Child) -> Unit,
    onDeleteChildPermanently: (Child) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetLocal: () -> Unit = {},
    onDeleteServerData: () -> Unit = {},
    message: String?,
    /**
     * The bundled dataset's identity line, e.g. `WHO 2006 · v2026.10 · updated 15 Oct 2026`.
     *
     * Null until it loads. A percentile a parent might act on is never shown elsewhere without this
     * line; Settings is where they can find out which tables the app is reading.
     */
    growthReference: String? = null,
    growthUpdateMessage: String? = null,
    onCheckGrowthUpdates: () -> Unit = {},
    /** Signed-in state of the shared log. NotConfigured on a build with no Firebase values. */
    syncState: SyncState = SyncState.NotConfigured,
    /** What the last sign-in or drain did, in one sentence. */
    syncMessage: String? = null,
    /** Queued writes waiting to reach the other phone. */
    pendingWrites: Int = 0,
    /** False until this build has an OAuth client id: the Google row is hidden rather than broken. */
    googleSignInAvailable: Boolean = false,
    onSignIn: (String, String) -> Unit = { _, _ -> },
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    /**
     * Which submenu is open. Null is the category menu, which is Settings' root.
     *
     * A single nullable section rather than a stack: Settings is two levels deep, and a stack would
     * invite a third.
     */
    section: SettingsSection? = null,
    onOpenSection: (SettingsSection) -> Unit = {},
    onBackToMenu: () -> Unit = {},
    contentPadding: PaddingValues,
    listState: LazyListState,
    /**
     * The system navigation-bar inset, applied OUTSIDE the scroll.
     *
     * Settings has no bottom bar of its own, so the Scaffold does not consume this inset for it. Left in
     * `contentPadding` it would scroll away with the content, letting rows pass under the transparent
     * system bar — which is exactly what looked like a see-through navigation bar.
     */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = BabyLogTheme.colors
    // Settings chips use the neutral UI colour, never an activity accent — the accents mean activities.
    val selection = colors.navActive

    // The one destructive action is gated by a dialog that NAMES the child.
    var pendingDelete by remember { mutableStateOf<Child?>(null) }
    // Phase 15: two buttons, not one row and a checkbox, so the destructive-and-unrecoverable choice is
    // its own visible decision rather than something a parent can leave ticked by accident.
    var pendingReset by remember { mutableStateOf<ResetRequest?>(null) }
    // An import replaces the shared log, so it asks first like the resets do.
    var confirmImport by remember { mutableStateOf(false) }
    var presetInput by remember { mutableStateOf("") }
    var medicationInput by remember { mutableStateOf("") }
    // Which activity a hide is waiting on, because hiding one that still has a reminder must warn first.
    var pendingHide by remember { mutableStateOf<EventType?>(null) }

    // Smart-cast once, so the `when` below is over a non-null section.
    val open = section

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(bottom = bottomInset),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            SettingsHeader(
                title = open?.title ?: "Settings",
                // Only a submenu has somewhere to go back to; the menu is Settings' root.
                onBack = if (open == null) null else onBackToMenu,
            )
        }

        item {
            SectionCard {
                if (open == null) {
                    SettingsSection.entries.forEach { entry ->
                        InputRow(
                            label = entry.title,
                            onClick = { onOpenSection(entry) },
                        ) {
                            InputValue(
                                sectionSummary(
                                    section = entry,
                                    theme = theme,
                                    units = units,
                                    nightWindow = nightWindow,
                                    presetCount = presets.size,
                                    medicationPresetCount = medicationPresets.size,
                                    childCount = children.size,
                                    hiddenActivityCount = hiddenActivities.size,
                                    syncState = syncState,
                                    pendingWrites = pendingWrites,
                                    growthReference = growthReference,
                                ),
                            )
                        }
                    }
                } else {
                    when (open) {
                        SettingsSection.APPEARANCE -> ChipRow(
                            label = "Theme",
                            options = ThemeMode.entries,
                            selected = theme,
                            accent = selection,
                            labelOf = ::themeLabel,
                            onSelect = onTheme,
                        )

                        SettingsSection.UNITS -> {
                            // Only the two presets are offered: picking a family individually is what
                            // makes it Custom.
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
                            ChipRow(label = "Temperature", options = TemperatureUnit.entries,
                                selected = units.temperature, accent = selection,
                                labelOf = { it.label }, onSelect = onTemperatureUnit)
                            ChipRow(label = "Time format", options = TimeFormat.entries,
                                selected = LocalTimeFormat.current, accent = selection,
                                labelOf = { if (it == TimeFormat.TWELVE_HOUR) "12-hour" else "24-hour" },
                                onSelect = onTimeFormat)
                        }

                        SettingsSection.DAY_NIGHT -> {
                            Text(
                                text = "Overnight changes count as night in the Summary diaper split.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            // Hours wrap at midnight rather than clamping, because that is how a clock reads.
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
                        }

                        SettingsSection.BOTTLE_PRESETS -> {
                            val unit = units.volume
                            Text(
                                text = "Amounts worth one tap in the bottle sheet, shown in ${volumeUnitLabel(unit)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            if (presets.isEmpty()) {
                                Text(
                                    text = "No presets yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            presets.forEach { millilitres ->
                                InputRow(
                                    label = formatVolume(millilitres.toDouble(), unit),
                                    onClick = { onRemovePreset(millilitres) },
                                ) {
                                    InputValue("Remove")
                                }
                            }
                            TextFieldRow(
                                label = "Add preset (${volumeUnitLabel(unit)})",
                                value = presetInput,
                                onValueChange = { typed -> presetInput = typed.filter { it.isDigit() || it == '.' } },
                                placeholder = "e.g. ${formatVolume(120.0, unit).substringBefore(' ')}",
                                keyboardType = KeyboardType.Decimal,
                            )
                            SecondaryButton(
                                label = "Add preset",
                                onClick = {
                                    val ml = presetInput.trim().toDoubleOrNull()
                                        ?.let { toMillilitres(it, unit).roundToInt() }
                                    if (ml != null && ml in 1..MAX_PRESET_ML) {
                                        onAddPreset(ml)
                                        presetInput = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }

                        SettingsSection.MEDICATION_PRESETS -> {
                            Text(
                                text = "Medications worth one tap in the health sheet. Left empty on purpose: the " +
                                    "app does not suggest a medicine or a dose, so these are the ones you actually use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            if (medicationPresets.isEmpty()) {
                                Text(
                                    text = "No presets yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            medicationPresets.forEach { medication ->
                                InputRow(
                                    label = medication,
                                    onClick = { onRemoveMedicationPreset(medication) },
                                ) {
                                    InputValue("Remove")
                                }
                            }
                            TextFieldRow(
                                label = "Add medication",
                                value = medicationInput,
                                onValueChange = { typed -> medicationInput = typed },
                                placeholder = "e.g. Calpol 2.5 ml",
                            )
                            SecondaryButton(
                                label = "Add preset",
                                onClick = {
                                    if (medicationInput.isNotBlank()) {
                                        onAddMedicationPreset(medicationInput.trim())
                                        medicationInput = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }

                        SettingsSection.CHILDREN -> {
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
                        }

                        SettingsSection.ACTIVITIES -> {
                            Text(
                                text = "Which cards the Activity tab shows. Hiding one keeps every record it holds — " +
                                    "this is a display choice, not a delete. A hidden activity is out of the way, not gone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            EVENT_DISPLAY_ORDER.forEach { type ->
                                SwitchRow(
                                    label = type.displayName(),
                                    checked = type !in hiddenActivities,
                                    accent = colors.accentFor(type),
                                    onCheckedChange = { shown ->
                                        when {
                                            shown -> onSetActivityHidden(type, false)
                                            // A reminder is set from its card, so hiding the card would leave the rule
                                            // with no surface. Ask, then cancel it with the hide.
                                            type in activitiesWithReminders -> pendingHide = type
                                            else -> onSetActivityHidden(type, true)
                                        }
                                    },
                                )
                            }
                        }

                        SettingsSection.DATA -> {
                            InputRow(label = "Export backup", onClick = onExport) { InputValue("JSON") }
                            InputRow(label = "Import backup", onClick = { confirmImport = true }) { InputValue("Replaces everything") }
                            Text(
                                text = "The backup includes both children, every event (including deleted ones) and the " +
                                    "food list. Keep it somewhere that is not this phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            InputRow(
                                label = "Reset local data",
                                onClick = { pendingReset = ResetRequest.LOCAL },
                            ) { InputValue("Erases this log") }
                            // Offered only while signed in: a server wipe that cannot reach the server is a button
                            // that lies about what it did.
                            if (syncState is SyncState.SignedIn) {
                                InputRow(
                                    label = "Delete all server data",
                                    onClick = { pendingReset = ResetRequest.SERVER },
                                ) { InputValue("Both phones") }
                            }
                            Text(
                                text = "Both of these erase every event and every child, and clear your settings, " +
                                    "presets and reminders. Neither deletes an exported backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            message?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        SettingsSection.SYNC -> SyncSection(
                            state = syncState,
                            message = syncMessage,
                            pendingWrites = pendingWrites,
                            googleSignInAvailable = googleSignInAvailable,
                            selection = selection,
                            onSignIn = onSignIn,
                            onSignInWithGoogle = onSignInWithGoogle,
                            onSignOut = onSignOut,
                            onSyncNow = onSyncNow,
                        )

                        SettingsSection.GROWTH_REFERENCE -> {
                            Text(
                                text = growthReference ?: "Reading the bundled reference…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Percentiles are computed from this dataset. A newer one can be fetched without " +
                                    "reinstalling the app; the bundled copy stays if the check fails.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            SecondaryButton(
                                label = "Check for updates",
                                onClick = onCheckGrowthUpdates,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            growthUpdateMessage?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        SettingsSection.ABOUT -> AboutContent()
                    }
                }
            }
        }
    }

    pendingHide?.let { type ->
        AlertDialog(
            onDismissRequest = { pendingHide = null },
            title = { Text("Hide ${type.displayName()}?") },
            text = {
                Text(
                    "${type.displayName()} has a reminder set. A reminder is set from its card, so hiding " +
                        "the card would leave it ringing with nothing to change it — hiding it cancels " +
                        "that reminder first. Every ${ReminderText.noun(type)} record stays in the log.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingHide = null
                    onSetActivityHidden(type, true)
                }) { Text("Hide and cancel the reminder", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingHide = null }) { Text("Keep it visible") }
            },
        )
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Replace everything with this file?") },
            text = {
                Text(
                    "The file becomes the whole log. This phone is emptied first, and because both phones " +
                        "share one log, the server is made to match the file as well — any child or record " +
                        "it does not contain is deleted on the other phone too. Export the current log " +
                        "first if you might want it back.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    onImport()
                }) { Text("Choose a file", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }

    pendingReset?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingReset = null },
            title = {
                Text(
                    when (request) {
                        ResetRequest.LOCAL -> "Reset this log?"
                        ResetRequest.SERVER -> "Delete everything, everywhere?"
                    }
                )
            },
            text = {
                Text(
                    when (request) {
                        ResetRequest.LOCAL ->
                            "This erases every event and every child on this phone, including First Twin " +
                                "and Second Twin. Your settings, presets and reminders go too. Both phones " +
                                "share one log, so this deletes it on the other phone as well. It cannot be " +
                                "undone — export a backup first if you might want it back."

                        ResetRequest.SERVER ->
                            "This removes the whole log from the server as well as from this phone. It " +
                                "cannot be recovered from the server, and both phones lose it. Export a " +
                                "backup first if you might want it back."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (request) {
                        ResetRequest.LOCAL -> onResetLocal()
                        ResetRequest.SERVER -> onDeleteServerData()
                    }
                    pendingReset = null
                }) {
                    Text(
                        when (request) {
                            ResetRequest.LOCAL -> "Reset this phone"
                            ResetRequest.SERVER -> "Delete everything"
                        },
                        color = colors.danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = null }) { Text("Cancel") }
            },
        )
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

/**
 * The one-line answer on a menu row.
 *
 * This exists so the menu is worth reading rather than a list of names: it says what each group is
 * currently set to. Every string here is derived from the same value the submenu's own control reads,
 * so the summary cannot drift from the control it summarises.
 */
@Composable
private fun sectionSummary(
    section: SettingsSection,
    theme: ThemeMode,
    units: UnitPreferences,
    nightWindow: NightWindow,
    presetCount: Int,
    medicationPresetCount: Int,
    childCount: Int,
    hiddenActivityCount: Int,
    syncState: SyncState,
    pendingWrites: Int,
    growthReference: String?,
): String = when (section) {
    SettingsSection.APPEARANCE -> themeLabel(theme)
    SettingsSection.UNITS -> units.system.name.lowercase().replaceFirstChar { it.uppercase() }
    SettingsSection.DAY_NIGHT -> "${hourLabel(nightWindow.startHour)} – ${hourLabel(nightWindow.endHour)}"
    SettingsSection.BOTTLE_PRESETS -> counted(presetCount, "preset")
    SettingsSection.MEDICATION_PRESETS -> counted(medicationPresetCount, "preset")
    SettingsSection.CHILDREN -> counted(childCount, singular = "child", plural = "children")
    SettingsSection.ACTIVITIES ->
        if (hiddenActivityCount == 0) "All shown" else "$hiddenActivityCount hidden"
    SettingsSection.DATA -> "Backup & reset"
    SettingsSection.SYNC -> when (syncState) {
        SyncState.NotConfigured -> "Not set up"
        SyncState.SignedOut -> "Signed out"
        is SyncState.Refused -> "Account refused"
        is SyncState.SignedIn ->
            if (pendingWrites == 0) "Signed in" else "Signed in · $pendingWrites waiting"
    }
    SettingsSection.GROWTH_REFERENCE -> datasetSummary(growthReference) ?: "Bundled"
    SettingsSection.ABOUT -> BuildConfig.VERSION_NAME
}

/**
 * `1 preset` / `3 presets`, `1 child` / `2 children`, and `None` when there are zero.
 *
 * [plural] is passed explicitly rather than having an `s` appended, because `2 childs` is exactly what
 * appending one produced — it shipped to the phone in the Phase 19 pass and was read off the menu.
 *
 * `None` rather than `0 presets` because an empty list is a state the app is deliberately designed to
 * start in (the medication list ships empty), and "0 presets" reads like something failed.
 */
internal fun counted(
    count: Int,
    singular: String,
    plural: String = "${singular}s",
    zero: String = "None",
): String = when (count) {
    0 -> zero
    1 -> "1 $singular"
    else -> "$count $plural"
}

/**
 * `WHO 2006 · v2026.10` from the full `WHO 2006 · v2026.10 · updated 15 Oct 2026`.
 *
 * The submenu shows the whole line; the menu row cannot. It was measured on the phone in the Phase 19
 * pass: the full line wrapped to two lines, and a two-line left-aligned value in a column of one-line
 * right-aligned ones made that row taller than every other row in the menu. The update date is the part
 * dropped rather than the version, because the version is what identifies which tables produced a
 * percentile.
 */
internal fun datasetSummary(identity: String?): String? =
    identity?.split(" · ")?.take(2)?.joinToString(" · ")

/**
 * The title of whichever Settings view is showing, with the way back when there is one.
 *
 * The back arrow is inside the scroll, matching where every other screen's title sits, and it carries a
 * content description because an icon-only control has no text for a screen reader to announce.
 */
@Composable
private fun SettingsHeader(title: String, onBack: (() -> Unit)? = null) {
    val colors = BabyLogTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Settings",
                    tint = colors.textPrimary,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SyncSection(
    state: SyncState,
    message: String?,
    pendingWrites: Int,
    googleSignInAvailable: Boolean,
    selection: Color,
    onSignIn: (String, String) -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val colors = BabyLogTheme.colors
    // `remember`, not `rememberSaveable`: a password has no business in the saved-instance-state bundle.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    when (state) {
        SyncState.NotConfigured -> {
            Text(
                text = "Sync is not set up on this build, so the log stays on this phone. Logging works " +
                    "normally.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                text = "Setting it up needs a Firebase project for app.babylog and a local " +
                    "firebase.properties. See PLAN.md, Phase 13.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        is SyncState.SignedIn -> {
            Text(
                text = "Signed in as ${state.email ?: "an account with no email"}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                text = if (pendingWrites == 0) {
                    "Everything here is on the server."
                } else {
                    "$pendingWrites change${if (pendingWrites == 1) "" else "s"} waiting to sync."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            SecondaryButton(
                label = "Sync now",
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            SecondaryButton(
                label = "Sign out",
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        SyncState.SignedOut, is SyncState.Refused -> {
            if (state is SyncState.Refused) {
                Text(
                    text = "${state.email ?: "That account"} is not one of the two accounts this log " +
                        "belongs to, so sync stays off and nothing is shared. Sign in with one of the two.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger,
                )
            } else {
                Text(
                    text = "Two accounts, one shared log. Sign in with either account on either phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
            TextFieldRow(
                label = "Email",
                value = email,
                onValueChange = { email = it },
                keyboardType = KeyboardType.Email,
            )
            TextFieldRow(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                isPassword = true,
            )
            SecondaryButton(
                label = "Sign in",
                onClick = { onSignIn(email, password) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (googleSignInAvailable) {
                SecondaryButton(
                    label = "Sign in with Google",
                    onClick = onSignInWithGoogle,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * One view, on its own surface.
 *
 * The surface change plus the gap between cards is what makes a boundary obvious where a heading alone
 * was not. There is now one card per view — the category menu's rows, or one setting group — so the
 * old `alternate` shading, which existed to separate consecutive cards down a single long scroll, has
 * nothing left to separate and is gone.
 */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = BabyLogTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surface)
            .padding(Spacing.cardPadding),
    ) {
        content()
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

/**
 * What this build is, and where it came from.
 *
 * The version comes from [BuildConfig], not from a typed string: this is the row a parent would quote in
 * a bug report, and a Settings screen that disagreed with the installed APK is worse than one that says
 * nothing. The repository is opened with an `ACTION_VIEW` intent rather than being printed as text, which
 * is the difference between a credit and a treasure hunt.
 */
@Composable
private fun AboutContent() {
    val colors = BabyLogTheme.colors
    val context = LocalContext.current
    Text(
        text = aboutVersionLine(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.DEBUG),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
    )
    Text(
        text = ABOUT_CREDITS,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 6.dp),
    )
    SecondaryButton(
        label = "Open the repository",
        onClick = {
            // A phone with no browser gets nothing rather than a crash: an optional credit must not be
            // able to take the Settings screen down.
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_REPOSITORY_URL)))
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Text(
        text = "Growth percentiles: WHO Child Growth Standards 2006 (igrowup).",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = "No analytics, no trackers, no ads. The log lives on this phone, and with sync on it " +
            "also lives in your own Firebase project.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 6.dp),
    )
}
