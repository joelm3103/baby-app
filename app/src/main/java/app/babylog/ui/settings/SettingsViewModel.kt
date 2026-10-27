package app.babylog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.MaintenanceRepository
import app.babylog.data.growth.GrowthDatasetRepository
import app.babylog.data.growth.GrowthDatasetUpdateResult
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.ChildSelection
import app.babylog.domain.EventType
import app.babylog.domain.REMINDER_TYPES
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import app.babylog.data.sync.ResetGate
import app.babylog.data.sync.SyncRepository
import app.babylog.data.sync.SyncState
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val sync: SyncRepository,
    private val growthDataset: GrowthDatasetRepository? = null,
    /**
     * Recompute and re-arm every reminder. Injected rather than reached for, because the engine lives in
     * the container and a ViewModel that grabbed it would make the reset untestable.
     */
    private val refreshReminders: () -> Unit = {},
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

    private val _growthUpdateMessage = MutableStateFlow<String?>(null)
    val growthUpdateMessage: StateFlow<String?> = _growthUpdateMessage.asStateFlow()

    fun checkGrowthUpdates() {
        val repo = growthDataset ?: return
        viewModelScope.launch {
            _growthUpdateMessage.value = "Checking\u2026"
            _growthUpdateMessage.value = when (val result = repo.checkForUpdates(true)) {
                is GrowthDatasetUpdateResult.Updated -> "Updated to ${result.version}"
                GrowthDatasetUpdateResult.NoUpdate -> "Already up to date"
                GrowthDatasetUpdateResult.Failed -> "Could not check"
            }
            // The identity line needs no refresh here: `GrowthDatasetRepository.identityLine` is a
            // StateFlow that the update publishes to, so the row above this message moves with it.
        }
    }

    // ---- reset to defaults (Phase 15) ---------------------------------------

    /**
     * Back to first-run state on this phone, telling the server what went.
     *
     * The gate runs FIRST, so a reset that could not travel is refused rather than performed and silently
     * undone by the next snapshot. Signed out is allowed: the tombstones wait for the next sign-in.
     */
    fun resetLocal() {
        viewModelScope.launch {
            when (val gate = sync.resetGate()) {
                is ResetGate.Refused -> {
                    _message.value = "Nothing was deleted: ${gate.message} " +
                        "Use Delete all server data if the log must go anyway."
                }

                ResetGate.Allowed -> {
                    resetEverything()
                    _message.value = drainSummary("This phone is back to first run.")
                }
            }
        }
    }

    /**
     * The whole log goes, on the server too.
     *
     * `queueTombstonesForEveryRemoteRow` runs first and is allowed to fail: if the server cannot be read,
     * nothing below it happens, because wiping this phone on a guess about what the server holds is how a
     * log comes back from the dead.
     */
    fun deleteAllServerData() {
        viewModelScope.launch {
            val queued = runCatching { sync.queueTombstonesForEveryRemoteRow() }.getOrElse { failure ->
                _message.value = "Nothing was deleted: the server could not be read " +
                    "(${failure.message ?: "no answer"})."
                return@launch
            }
            resetEverything()
            _message.value = drainSummary("The log is gone from this phone and the server.") +
                if (queued > 0) " $queued row(s) only the server knew about were queued." else ""
        }
    }

    /** One implementation for both buttons, so the two can never drift apart. */
    private suspend fun resetEverything() {
        // Detached from the inbound listener for the whole wipe: a snapshot taken before the deletions
        // landed used to re-insert every event into the emptied tables (found on the phone, Phase 15).
        sync.withoutInboundApply {
            maintenance.resetLocal()
            settings.clearForReset()
            // The children a reset leaves behind are the two seeded ones; the seed queues their UPSERTs,
            // which is what replaces the tombstones that deliberately were not queued for those ids.
            children.seedIfEmpty()
            settings.markChildSeedDone()
            // Rules anchored to a log that no longer exists are cancelled here, not merely forgotten.
            refreshReminders()
            sync.syncNow()
        }
    }

    private suspend fun drainSummary(done: String): String {
        val pending = sync.pendingWrites.first()
        return if (pending == 0) {
            "$done Nothing is left waiting to sync."
        } else {
            "$done $pending deletion(s) are still queued for the other phone."
        }
    }

    // ---- sync ---------------------------------------------------------------

    val syncState: StateFlow<SyncState> = sync.state

    /** What the last sign-in or drain did, in one sentence. */
    val syncMessage: StateFlow<String?> = sync.message

    /** Writes waiting for the other phone to be reachable. */
    val pendingWrites: StateFlow<Int> = sync.pendingWrites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** False until the build has an OAuth client id; the Google button is hidden rather than broken. */
    val googleSignInAvailable: Boolean = sync.googleSignInAvailable

    fun signIn(email: String, password: String) = viewModelScope.launch { sync.signIn(email, password) }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch { sync.signInWithGoogle(idToken) }

    fun signOut() = sync.signOut()

    fun syncNow() = viewModelScope.launch { sync.syncNow() }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }

    fun setUnitSystem(system: UnitSystem) = viewModelScope.launch { settings.setUnitSystem(system) }

    fun setLengthUnit(unit: LengthUnit) = viewModelScope.launch { settings.setLengthUnit(unit) }

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { settings.setWeightUnit(unit) }

    fun setTemperatureUnit(unit: app.babylog.domain.TemperatureUnit) = viewModelScope.launch { settings.setTemperatureUnit(unit) }
    fun setTimeFormat(format: app.babylog.domain.TimeFormat) = viewModelScope.launch { settings.setTimeFormat(format) }

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
        // The file's rows are the new truth and the queue was wiped with the tables, so tell the server
        // about the restored log instead of letting the next snapshot merge the old one back in.
        sync.reseedAfterRestore()
        _message.value = "Backup imported. Everything now matches the file."
    }

    fun showMessage(text: String) {
        _message.value = text
    }
}
