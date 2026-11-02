package app.babylog.data.repo

import app.babylog.domain.TemperatureUnit
import app.babylog.domain.TimeFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.babylog.domain.ChildSelection
import app.babylog.domain.LengthUnit
import app.babylog.domain.NightWindow
import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import app.babylog.domain.ThemeMode
import app.babylog.domain.UnitPreferences
import app.babylog.domain.UnitSystem
import app.babylog.domain.VolumeUnit
import app.babylog.domain.WeightUnit
import app.babylog.domain.decodeChildSelection
import app.babylog.domain.decodeReminder
import app.babylog.domain.encodeReminder
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
            temperature = enumOrDefault(prefs[KEY_TEMPERATURE], TemperatureUnit.CELSIUS),
        )
    }

    val timeFormat: Flow<TimeFormat> = dataStore.data.map {
        enumOrDefault(it[KEY_TIME_FORMAT], TimeFormat.TWELVE_HOUR)
    }

    suspend fun setTimeFormat(format: TimeFormat) = dataStore.edit { it[KEY_TIME_FORMAT] = format.name }
    suspend fun setTemperatureUnit(unit: TemperatureUnit) = dataStore.edit { it[KEY_TEMPERATURE] = unit.name }

    val selectedChild: Flow<ChildSelection> =
        dataStore.data.map { decodeChildSelection(it[KEY_SELECTED_CHILD]) }

    val theme: Flow<ThemeMode> = dataStore.data.map { enumOrDefault(it[KEY_THEME], ThemeMode.SYSTEM) }

    /** True once the first-run children seed has run. Never reset — see [seedChildrenOnce]. */
    val childSeedDone: Flow<Boolean> = dataStore.data.map { it[KEY_CHILD_SEEDED] == true }

    /**
     * True once the local log has been pushed to Firestore for the first time.
     *
     * A one-way flag, deliberately not "is the outbox empty": an empty queue is what a *finished* drain
     * looks like, so a flag is the only thing that tells "never seeded" apart from "seeded and drained".
     */
    val syncSeedDone: Flow<Boolean> = dataStore.data.map { it[KEY_SYNC_SEEDED] == true }

    suspend fun markSyncSeedDone() = dataStore.edit { it[KEY_SYNC_SEEDED] = true }

    /**
     * After a restore the whole database is new but the queue is empty, so the server has to be told
     * about it from scratch — this puts the one-way seed flag back so the next seed does exactly that.
     */
    suspend fun resetSyncSeed() = dataStore.edit { it[KEY_SYNC_SEEDED] = false }

    /**
     * True once the foods this phone had already typed were queued for `foods_pending`.
     *
     * A one-way flag for the same reason as [syncSeedDone]: the queue empties as soon as the drain works,
     * so "never seeded" and "seeded and drained" are only tellable apart by a flag. It exists because a
     * custom food created before the published catalogue shipped (ADR-0002) would otherwise never be
     * announced to the other phone — nothing else ever queues a food that already exists.
     */
    val foodSeedDone: Flow<Boolean> = dataStore.data.map { it[KEY_FOOD_SEEDED] == true }

    suspend fun markFoodSeedDone() = dataStore.edit { it[KEY_FOOD_SEEDED] = true }

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

    /**
     * The activity cards the Activity tab is told to leave out. Empty means every activity shows.
     *
     * A DISPLAY choice only: it hides a card, and nothing else about that activity changes. Because a
     * reminder is set from its card, hiding one that has a rule would strand the rule, so the caller
     * cancels the rules first (see `SettingsViewModel.setActivityHidden`) and the screen asks before it
     * does. A name this build does not know is dropped rather than throwing, so a preference written by a
     * newer build cannot break an older one.
     */
    val hiddenActivities: Flow<Set<EventType>> = dataStore.data.map {
        decodeHiddenActivities(it[KEY_HIDDEN_ACTIVITIES])
    }

    suspend fun setActivityHidden(type: EventType, hidden: Boolean) = dataStore.edit { prefs ->
        val current = decodeHiddenActivities(prefs[KEY_HIDDEN_ACTIVITIES]).toMutableSet()
        if (hidden) current.add(type) else current.remove(type)
        prefs[KEY_HIDDEN_ACTIVITIES] = encodeHiddenActivities(current)
    }

    /**
     * Every reminder rule on this phone, keyed by (activity, child).
     *
     * Read by scanning the preference map rather than by a fixed list of keys, because the keys are dynamic:
     * a reminder only exists once a parent has touched it, and adding a child must not need a code change.
     * Local only and never synced - a reminder is one phone's business, and the other parent's phone has its
     * own ideas about when to be woken.
     */
    val reminders: Flow<Map<ReminderKey, ReminderConfig>> = dataStore.data.map { prefs ->
        prefs.asMap().entries
            .mapNotNull { (key, value) ->
                val parsed = decodeReminderKey(key.name) ?: return@mapNotNull null
                parsed to decodeReminder(value as? String)
            }
            .toMap()
    }

    suspend fun setReminder(key: ReminderKey, config: ReminderConfig) = dataStore.edit {
        it[stringPreferencesKey(reminderKeyName(key))] = encodeReminder(config)
    }

    /**
     * Back to first-run preferences — Phase 15's reset.
     *
     * Everything this app stores about *this phone's* choices goes, plus every reminder rule, because a
     * reset deletes the log those rules were anchored to: a rule left behind would ring about a feed the
     * app no longer has any record of. `Reminders.refresh()` cancels the alarms afterwards, which is the
     * same pass that keeps a switched-off rule silent.
     *
     * Deliberately NOT cleared:
     * - `sync.seeded` — a reset must not re-upload a log it just deleted; the queued tombstones are the news.
     * - `children.seeded` — a reset leaves NO children, and the first-run seed must not put the twins back on
     *   the next launch. The callers set it explicitly with `markChildSeedDone`, so it is only ever set,
     *   never cleared and re-set — one ordering hazard fewer.
     * - `growth.dataset.*` — which reference produced a percentile is not the family's log, and a reset
     *   must not silently downgrade a dataset that was verified and accepted.
     * - `foods.seeded` — a reset wipes the catalogue table back to the bundled asset, so there is nothing to
     *   announce and re-seeding would queue nothing. Keeping the flag set is what stops a reset from
     *   re-announcing foods it just deleted.
     */
    suspend fun clearForReset() = dataStore.edit { prefs ->
        listOf<Preferences.Key<*>>(
            KEY_LENGTH, KEY_WEIGHT, KEY_VOLUME, KEY_TEMPERATURE, KEY_TIME_FORMAT,
            KEY_SELECTED_CHILD, KEY_THEME,
            KEY_NIGHT_START, KEY_NIGHT_END, KEY_BOTTLE_PRESETS, KEY_MEDICATION_PRESETS,
            // A reset returns the app to first-run state, and first run shows every activity. Without this
            // a reset would leave a parent with a permanently half-empty Activity tab and no error to see.
            KEY_HIDDEN_ACTIVITIES,
        ).forEach { prefs.remove(it) }
        // Reminder keys are dynamic (one per activity and child), so they are found by shape: the same
        // scan `reminders` uses to read them.
        prefs.asMap().keys
            .filter { decodeReminderKey(it.name) != null }
            .forEach { prefs.remove(stringPreferencesKey(it.name)) }
    }

    companion object {
        // The preferences keys are this class's own storage detail, so they stay private.
        private val KEY_LENGTH = stringPreferencesKey("units.length")
        private val KEY_WEIGHT = stringPreferencesKey("units.weight")
        private val KEY_TEMPERATURE = stringPreferencesKey("units.temperature")
        private val KEY_TIME_FORMAT = stringPreferencesKey("time.format")
        private val KEY_VOLUME = stringPreferencesKey("units.volume")
        private val KEY_SELECTED_CHILD = stringPreferencesKey("child.selected")
        private val KEY_THEME = stringPreferencesKey("ui.theme")
        private val KEY_CHILD_SEEDED = booleanPreferencesKey("children.seeded")
        private val KEY_SYNC_SEEDED = booleanPreferencesKey("sync.seeded")
        private val KEY_FOOD_SEEDED = booleanPreferencesKey("foods.seeded")
        private val KEY_NIGHT_START = intPreferencesKey("history.nightStartHour")
        private val KEY_NIGHT_END = intPreferencesKey("history.nightEndHour")
        private val KEY_BOTTLE_PRESETS = stringPreferencesKey("bottle.presets")
        private val KEY_MEDICATION_PRESETS = stringPreferencesKey("health.medicationPresets")
        private val KEY_HIDDEN_ACTIVITIES = stringPreferencesKey("activities.hidden")

        private const val DEFAULT_NIGHT_START_HOUR = 18
        private const val DEFAULT_NIGHT_END_HOUR = 6

        /**
         * The largest amount a bottle preset may hold, in ml.
         *
         * A preset above this is a typo rather than a bottle, and it is the same number in two places by
         * need: Settings refuses to store one, and the card refuses to quick-log one. Kept here, public, so
         * those two gates cannot drift into disagreeing about what a bottle is.
         */
        const val MAX_BOTTLE_PRESET_ML = 1000
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
 * Hidden activities are a small set of names, stored sorted so the bytes are stable and a diff of two
 * preference files is readable. An absent key is the empty set, which is the app's default: every card
 * shows until a parent says otherwise. An unknown name is dropped, so a preference written by a newer
 * build that knows a seventh activity cannot make this one throw on launch.
 */
private fun decodeHiddenActivities(raw: String?): Set<EventType> = raw
    ?.split(',')
    ?.map { it.trim() }
    ?.mapNotNull { name -> EventType.entries.firstOrNull { it.name == name } }
    ?.toSet()
    ?: emptySet()

private fun encodeHiddenActivities(values: Set<EventType>): String =
    values.map { it.name }.sorted().joinToString(",")

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

/**
 * A reminder's preference key is `reminder.<ACTIVITY>.<childId>`, and the type prefix is what makes the scan in
 * [SettingsRepository.reminders] cheap: everything else in the file is ignored without parsing.
 *
 * The activity name is used rather than its ordinal on purpose - an enum reorder must not silently move a
 * parent's feed reminder onto their sleep.
 */
private const val REMINDER_PREFIX = "reminder"

private fun reminderKeyName(key: ReminderKey): String =
    "${REMINDER_PREFIX}.${key.type.name}.${key.childId}"

/** Null for any key that is not a reminder, and for a type this build does not know. */
private fun decodeReminderKey(name: String): ReminderKey? {
    val parts = name.split('.')
    if (parts.size != 3 || parts[0] != REMINDER_PREFIX) return null
    val type = EventType.entries.firstOrNull { it.name == parts[1] } ?: return null
    if (parts[2].isEmpty()) return null
    return ReminderKey(type, parts[2])
}

/** Common bottle sizes, so the chips are useful before anything has been configured. */
private val DEFAULT_BOTTLE_PRESETS = listOf(60, 90, 120, 150)
