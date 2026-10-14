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

        const val DEFAULT_NIGHT_START_HOUR = 18
        const val DEFAULT_NIGHT_END_HOUR = 6
    }
}
