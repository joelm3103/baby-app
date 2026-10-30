package app.babylog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.MaintenanceRepository
import app.babylog.data.growth.GrowthDatasetRepository
import app.babylog.data.growth.message
import app.babylog.data.repo.SettingsRepository
import kotlinx.coroutines.flow.map
import app.babylog.domain.ChildSelection
import app.babylog.domain.EventType
import app.babylog.domain.REMINDER_TYPES
import app.babylog.domain.ReminderConfig
import app.babylog.domain.reminderKeysToCancelOnHide
import app.babylog.domain.ReminderKey
import app.babylog.data.sync.DrainResult
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

    /**
     * The activity cards the Activity tab hides. Default empty: every activity shows until a parent hides one.
     */
    val hiddenActivities: StateFlow<Set<EventType>> = settings.hiddenActivities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Which activities currently have at least one ENABLED reminder rule.
     *
     * The Activities card asks this before it hides anything: a rule is set from its card, so hiding the card
     * without cancelling the rule leaves an alarm ringing for a surface that no longer exists.
     */
    val activitiesWithReminders: StateFlow<Set<EventType>> = settings.reminders
        .map { rules -> rules.filterValues { it.enabled }.keys.map { it.type }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** One line of feedback for the Data section: an export written, or why an import was refused. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _growthUpdateMessage = MutableStateFlow<String?>(null)
    val growthUpdateMessage: StateFlow<String?> = _growthUpdateMessage.asStateFlow()

    fun checkGrowthUpdates() {
        val repo = growthDataset ?: return
        viewModelScope.launch {
            _growthUpdateMessage.value = "Checking\u2026"
            _growthUpdateMessage.value = repo.checkForUpdates(true).message()
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
                    val result = sync.withoutInboundApply {
                        wipePhone()
                        sync.drainUntilIdle()
                    }
                    _message.value = resetSummary(result, wipedServer = false)
                }
            }
        }
    }

    /**
     * The whole log goes, on the server too.
     *
     * `reconcileRemote` runs inside the same detached block, before the local wipe, so the ids it reads
     * from the server are the ones about to disappear locally too — and `enqueueTombstones` skips ids that
     * are already queued, so each row travels once rather than twice.
     */
    fun deleteAllServerData() {
        viewModelScope.launch {
            val outcome = runCatching {
                sync.withoutInboundApply {
                    val queuedRemotely = sync.reconcileRemote(emptySet(), emptySet())
                    wipePhone()
                    queuedRemotely to sync.drainUntilIdle()
                }
            }
            outcome.onSuccess { (queuedRemotely, result) ->
                _message.value = resetSummary(result, wipedServer = true) +
                    if (queuedRemotely > 0) " $queuedRemotely row(s) only the server knew about were removed." else ""
            }.onFailure { failure ->
                _message.value = "Nothing was deleted: the server could not be read " +
                    "(${failure.message ?: "no answer"})."
            }
        }
    }

    /**
     * Empty this phone and leave NO children behind.
     *
     * The first-run seed is deliberately not run: the owner asked for a reset that leaves an empty app
     * rather than the two seeded twins, so the flag is set to keep `seedChildrenOnce` from putting them
     * back on the next launch. `clearForReset` no longer clears that flag, which is why this is the only
     * place that decides it.
     */
    private suspend fun wipePhone() {
        maintenance.resetLocal()
        settings.clearForReset()
        settings.markChildSeedDone()
        // Rules anchored to a log that no longer exists are cancelled here, not merely forgotten.
        refreshReminders()
    }

    /**
     * A summary that is allowed to say "the server too" only when that is true.
     *
     * The owner was told "The log is gone from this phone and the server" while ~160 deletions were still
     * queued — two contradictory statements in one sentence. Whatever is left is now named.
     */
    private fun resetSummary(result: DrainResult?, wipedServer: Boolean): String {
        val left = result?.remaining ?: 0
        return when {
            left == 0 && wipedServer ->
                "This phone is empty and so is the server. Add a child in Settings to start again."
            left == 0 -> "This phone is empty. Every deletion reached the server."
            wipedServer ->
                "This phone is empty. $left deletion(s) are still waiting for the server, so the log is " +
                    "NOT gone from there yet — they will go when the connection allows."
            else ->
                "This phone is empty. $left deletion(s) are still waiting for the server."
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

    /**
     * Hide or show one activity on the Activity tab.
     *
     * Hiding CANCELS that activity's reminders first. The rule is set from the card, so a hidden card would
     * leave an alarm with no surface to change it — and a notification the parent cannot find is worse than
     * no notification. The screen asks before calling this when a rule exists, which is why the cancellation
     * lives here rather than behind a second confirm.
     */
    fun setActivityHidden(type: EventType, hidden: Boolean) = viewModelScope.launch {
        // Hidden is written FIRST, then the rules are swept. Either order leaves a window, but this one keeps
        // the state a parent can see ahead of the state they cannot: if the sweep loses a race, the card is
        // already gone and the leftover rule is what the next hide (or the reminders refresh) will catch,
        // rather than a card still on screen whose reminder has silently been cancelled.
        settings.setActivityHidden(type, hidden)
        if (!hidden) return@launch
        // Re-read AFTER the write, so a rule saved in between is still in the set that gets cancelled. Every
        // child's rule for this activity, not just the one in view: the card is the only place a rule can be
        // edited, so a twin left armed would be unreachable.
        val rules = settings.reminders.first()
        reminderKeysToCancelOnHide(rules, type)
            .forEach { settings.setReminder(it, ReminderConfig()) }
        refreshReminders()
    }

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
        // Read first: a bad or newer-schema file throws here, before anything is wiped, and its ids are the
        // keep set the server is reconciled against.
        val snapshot = maintenance.readSnapshot(json)
        val keepChildren = snapshot.children.map { it.id }.toSet()
        val keepEvents = snapshot.events.map { it.id }.toSet()

        val outcome = runCatching {
            sync.withoutInboundApply {
                maintenance.replaceAll(json)
                settings.markChildSeedDone()
                // The file's rows are the new truth and must travel.
                sync.reseedAfterRestore()
                val removed = sync.reconcileRemote(keepChildren, keepEvents)
                removed to sync.drainUntilIdle()
            }
        }

        outcome.onSuccess { (removed, result) ->
            val left = result?.remaining ?: 0
            _message.value = when {
                left > 0 ->
                    "Backup imported on this phone. $left change(s) are still waiting for the server."
                removed > 0 ->
                    "Backup imported. This phone and the server now match the file — $removed old row(s) removed."
                else ->
                    "Backup imported. This phone and the server now match the file."
            }
        }.onFailure { failure ->
            _message.value = failure.message ?: "That file could not be imported."
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }
}
