package app.babylog.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.babylog.domain.ChildSelection
import app.babylog.domain.LengthUnit
import app.babylog.domain.NightWindow
import app.babylog.domain.ThemeMode
import app.babylog.domain.UnitPreferences
import app.babylog.domain.UnitSystem
import app.babylog.domain.VolumeUnit
import app.babylog.domain.WeightUnit
import app.babylog.domain.decodeChildSelection
import app.babylog.domain.encodeChildSelection
import app.babylog.domain.enumOrDefault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Preferences. Deliberately tiny: units, selected child, theme. Anything else that wants to live here
 * should first justify why it is not a constant (see the do-not-build list).
 *
 * Defaults are chosen so a fresh install is already correct: **Metric**, **Both children**, **follow
 * the system theme**.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val units: Flow<UnitPreferences> = dataStore.data.map { prefs ->
        UnitPreferences(
            length = enumOrDefault(prefs[KEY_LENGTH], LengthUnit.CENTIMETRES),
            weight = enumOrDefault(prefs[KEY_WEIGHT], WeightUnit.KILOGRAMS),
            volume = enumOrDefault(prefs[KEY_VOLUME], VolumeUnit.MILLILITRES),
        )
    }

    val selectedChild: Flow<ChildSelection> =
        dataStore.data.map { decodeChildSelection(it[KEY_SELECTED_CHILD]) }

    val theme: Flow<ThemeMode> = dataStore.data.map { enumOrDefault(it[KEY_THEME], ThemeMode.SYSTEM) }

    /** True once the first-run children seed has run. Never reset — see [seedChildrenOnce]. */
    val childSeedDone: Flow<Boolean> = dataStore.data.map { it[KEY_CHILD_SEEDED] == true }

    /**
     * When night is, for the History day/night diaper split. Defaults to 18:00-06:00; `start == end`
     * means no night at all (see [NightWindow]).
     */
    val nightWindow: Flow<NightWindow> = dataStore.data.map {
        NightWindow(
            startHour = it[KEY_NIGHT_START] ?: DEFAULT_NIGHT_START_HOUR,
            endHour = it[KEY_NIGHT_END] ?: DEFAULT_NIGHT_END_HOUR,
        )
    }

    suspend fun setNightStartHour(hour: Int) =
        dataStore.edit { it[KEY_NIGHT_START] = hour.coerceIn(0, 23) }

    suspend fun setNightEndHour(hour: Int) =
        dataStore.edit { it[KEY_NIGHT_END] = hour.coerceIn(0, 23) }

    /**
     * The bottle amounts worth one tap, stored canonically in MILLILITRES and kept in ASCENDING order,
     * which is how a list of sizes reads. Shown in whatever volume unit is selected, so switching units
     * never rewrites a preset.
     */
    val bottlePresets: Flow<List<Int>> =
        dataStore.data.map { decodePresets(it[KEY_BOTTLE_PRESETS]) }

    /** Adds a preset unless it is already there or is not a positive amount. */
    suspend fun addBottlePreset(millilitres: Int) {
        if (millilitres <= 0) return
        dataStore.edit { prefs ->
            val current = decodePresets(prefs[KEY_BOTTLE_PRESETS])
            if (millilitres !in current) {
                prefs[KEY_BOTTLE_PRESETS] = encodePresets(current + millilitres)
            }
        }
    }

    suspend fun removeBottlePreset(millilitres: Int) {
        dataStore.edit { prefs ->
            val current = decodePresets(prefs[KEY_BOTTLE_PRESETS])
            prefs[KEY_BOTTLE_PRESETS] = encodePresets(current - millilitres)
        }
    }

    /**
     * The medications worth one tap in the health sheet, in the order they were added.
     *
     * EMPTY by default, deliberately. The bottle presets default to common sizes because a size is a
     * fact about bottles; a medicine and its dose are facts about a child, and an app that suggests
     * either would be putting a plausible-looking tap in front of a parent at 3am. The presets are for
     * what they already use.
     */
    val medicationPresets: Flow<List<String>> =
        dataStore.data.map { decodeMedicationPresets(it[KEY_MEDICATION_PRESETS]) }

    /** Adds a preset unless it is blank, already there, or differs only by case. Order is insertion order. */
    suspend fun addMedicationPreset(name: String) {
        val trimmed = sanitiseMedicationPreset(name)
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val current = decodeMedicationPresets(prefs[KEY_MEDICATION_PRESETS])
            if (current.none { it.equals(trimmed, ignoreCase = true) }) {
                prefs[KEY_MEDICATION_PRESETS] = encodeMedicationPresets(current + trimmed)
            }
        }
    }

    suspend fun removeMedicationPreset(name: String) {
        dataStore.edit { prefs ->
            val current = decodeMedicationPresets(prefs[KEY_MEDICATION_PRESETS])
            prefs[KEY_MEDICATION_PRESETS] = encodeMedicationPresets(current - name)
        }
    }

    /**
     * Runs [seed] at most once in the app's lifetime, and reports whether it ran.
     *
     * The old guard was "is the children table empty", which a parent makes true again simply by
     * removing their children — and the app would then resurrect the children on the next launch. A
     * one-way flag is the only thing that tells "never seeded" apart from "seeded, then emptied".
     */
    suspend fun seedChildrenOnce(seed: suspend () -> Unit): Boolean {
        if (dataStore.data.first()[KEY_CHILD_SEEDED] == true) return false
        seed()
        dataStore.edit { it[KEY_CHILD_SEEDED] = true }
        return true
    }

    /** An import replaces the children wholesale, so the first-run seed must never fire afterwards. */
    suspend fun markChildSeedDone() = dataStore.edit { it[KEY_CHILD_SEEDED] = true }

    /** Sets all three families at once. CUSTOM intentionally changes nothing — it is derived, not a preset. */
    suspend fun setUnitSystem(system: UnitSystem) {
        val preset = when (system) {
            UnitSystem.IMPERIAL -> UnitPreferences.Imperial
            UnitSystem.METRIC -> UnitPreferences.Metric
            UnitSystem.CUSTOM -> return
        }
        dataStore.edit { prefs ->
            prefs[KEY_LENGTH] = preset.length.name
            prefs[KEY_WEIGHT] = preset.weight.name
            prefs[KEY_VOLUME] = preset.volume.name
        }
    }

    /** Selecting one family individually is how the user lands in CUSTOM. */
    suspend fun setLengthUnit(unit: LengthUnit) = dataStore.edit { it[KEY_LENGTH] = unit.name }

    suspend fun setWeightUnit(unit: WeightUnit) = dataStore.edit { it[KEY_WEIGHT] = unit.name }

    suspend fun setVolumeUnit(unit: VolumeUnit) = dataStore.edit { it[KEY_VOLUME] = unit.name }

    suspend fun setSelectedChild(selection: ChildSelection) =
        dataStore.edit { it[KEY_SELECTED_CHILD] = encodeChildSelection(selection) }

    suspend fun setTheme(mode: ThemeMode) = dataStore.edit { it[KEY_THEME] = mode.name }

    private companion object {
        val KEY_LENGTH = stringPreferencesKey("units.length")
        val KEY_WEIGHT = stringPreferencesKey("units.weight")
        val KEY_VOLUME = stringPreferencesKey("units.volume")
        val KEY_SELECTED_CHILD = stringPreferencesKey("child.selected")
        val KEY_THEME = stringPreferencesKey("ui.theme")
        val KEY_CHILD_SEEDED = booleanPreferencesKey("children.seeded")
        val KEY_NIGHT_START = intPreferencesKey("history.nightStartHour")
        val KEY_NIGHT_END = intPreferencesKey("history.nightEndHour")
        val KEY_BOTTLE_PRESETS = stringPreferencesKey("bottle.presets")
        val KEY_MEDICATION_PRESETS = stringPreferencesKey("health.medicationPresets")

        const val DEFAULT_NIGHT_START_HOUR = 18
        const val DEFAULT_NIGHT_END_HOUR = 6
    }
}

/**
 * Presets are a small ordered list of sizes, so they are stored as a comma-separated string rather than
 * a `stringSetPreferencesKey`: DataStore has no ordered collection. They are SORTED ascending on read,
 * so the stored order never matters and a hand-edited preference still reads sensibly. An absent key
 * yields the defaults; an emptied list stays empty, so removing every preset does not silently bring the
 * defaults back.
 */
private fun decodePresets(raw: String?): List<Int> =
    raw?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.filter { it > 0 }
        ?.distinct()
        ?.sorted()
        ?: DEFAULT_BOTTLE_PRESETS

private fun encodePresets(values: List<Int>): String = values.joinToString(",")

/**
 * Medication presets are free text, so they are separated by a NEWLINE rather than the comma the bottle
 * sizes use: "Calpol, 2.5 ml" is a dose a parent may well type, and a comma-separated encoding would
 * silently split it into two presets. Newlines cannot be typed — the field is single-line — so the
 * separator is the one character that cannot appear in the data.
 */
private fun decodeMedicationPresets(raw: String?): List<String> = raw
    ?.split('\n')
    ?.map(::sanitiseMedicationPreset)
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    ?: emptyList()

private fun encodeMedicationPresets(values: List<String>): String = values.joinToString("\n")

/** Also the guard at the door: a newline in a preset would forge a second entry on the next read. */
private fun sanitiseMedicationPreset(value: String): String =
    value.replace('\n', ' ').trim()

/** Common bottle sizes, so the chips are useful before anything has been configured. */
private val DEFAULT_BOTTLE_PRESETS = listOf(60, 90, 120, 150)