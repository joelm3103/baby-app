package app.babylog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.MaintenanceRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.Child
import app.babylog.domain.LengthUnit
import app.babylog.domain.NightWindow
import app.babylog.domain.Sex
import app.babylog.domain.ThemeMode
import app.babylog.domain.UnitPreferences
import app.babylog.domain.UnitSystem
import app.babylog.domain.VolumeUnit
import app.babylog.domain.WeightUnit
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings state and every write it performs. The screen stays stateless; this owns persistence, so
 * there is exactly one place that decides what a tap means.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val children: ChildRepository,
    private val maintenance: MaintenanceRepository,
) : ViewModel() {

    val childrenList: StateFlow<List<Child>> = children.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val units: StateFlow<UnitPreferences> = settings.units
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitPreferences())

    val theme: StateFlow<ThemeMode> = settings.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** When night is, for the History day/night diaper split. Defaults to 18:00-06:00. */
    val nightWindow: StateFlow<NightWindow> = settings.nightWindow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightWindow())

    /** The bottle amounts worth one tap, canonical millilitres, ascending. */
    val bottlePresets: StateFlow<List<Int>> = settings.bottlePresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The medications worth one tap in the health sheet, in the order they were added. */
    val medicationPresets: StateFlow<List<String>> = settings.medicationPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One line of feedback for the Data section: an export written, or why an import was refused. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }

    fun setUnitSystem(system: UnitSystem) = viewModelScope.launch { settings.setUnitSystem(system) }

    fun setLengthUnit(unit: LengthUnit) = viewModelScope.launch { settings.setLengthUnit(unit) }

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { settings.setWeightUnit(unit) }

    fun setVolumeUnit(unit: VolumeUnit) = viewModelScope.launch { settings.setVolumeUnit(unit) }

    fun setNightStartHour(hour: Int) = viewModelScope.launch { settings.setNightStartHour(hour) }

    fun setNightEndHour(hour: Int) = viewModelScope.launch { settings.setNightEndHour(hour) }

    fun addBottlePreset(millilitres: Int) = viewModelScope.launch { settings.addBottlePreset(millilitres) }

    fun removeBottlePreset(millilitres: Int) = viewModelScope.launch { settings.removeBottlePreset(millilitres) }

    fun addMedicationPreset(name: String) = viewModelScope.launch { settings.addMedicationPreset(name) }

    fun removeMedicationPreset(name: String) =
        viewModelScope.launch { settings.removeMedicationPreset(name) }

    fun updateChild(child: Child) = viewModelScope.launch { children.upsert(child) }

    /**
     * Adds a child with a placeholder name and today's date, ready to edit. There is no separate form:
     * the row is on screen the moment it exists, so a modal for four fields would be ceremony.
     */
    fun addChild() = viewModelScope.launch {
        children.add(name = "New child", birthDate = LocalDate.now(), sex = Sex.BOY)
    }

    /** Archive: recoverable, and the child leaves the switcher and the totals. */
    fun archiveChild(child: Child) = viewModelScope.launch { maintenance.archiveChild(child.id) }

    /** Permanent: the rows are gone. Gated by a naming confirmation in the UI. */
    fun deleteChildPermanently(child: Child) = viewModelScope.launch {
        maintenance.deleteChildPermanently(child.id)
    }

    /** The JSON to hand to the file picker. */
    suspend fun exportJson(): String = maintenance.exportJson()

    /**
     * Replace the whole database from a backup file.
     *
     * `replaceAll` throws before touching anything if the file is unreadable or from a newer schema, so
     * the caller can surface the message without a half-applied restore. The children seed is marked
     * done afterwards, so a restored database is never overwritten by the first-run seed.
     */
    suspend fun importJson(json: String) {
        maintenance.replaceAll(json)
        settings.markChildSeedDone()
        _message.value = "Backup imported. Everything now matches the file."
    }

    fun showMessage(text: String) {
        _message.value = text
    }
}
