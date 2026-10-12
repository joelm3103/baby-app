package app.babylog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventDraft
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.BabyEvent
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardUi(
    val type: EventType,
    val last: BabyEvent?,
    val recent: List<BabyEvent>,
    val running: BabyEvent?,
)

data class HomeUiState(
    val children: List<Child> = emptyList(),
    val selection: ChildSelection = ChildSelection.Both,
    val childIds: List<String> = emptyList(),
    val cards: List<CardUi> = emptyList(),
    val units: app.babylog.domain.UnitPreferences = app.babylog.domain.UnitPreferences(),
) {
    val anyRunning: Boolean get() = cards.any { it.running != null }

    /** "both" or the child's name — used to make the action button say who it will log for. */
    val childLabel: String
        get() = when (val s = selection) {
            is ChildSelection.Both -> if (children.size > 1) "both" else children.firstOrNull()?.name.orEmpty()
            is ChildSelection.One -> children.firstOrNull { it.id == s.childId }?.name.orEmpty()
        }
}

/**
 * One screen's state. Built from a SINGLE events query over the selected children rather than six
 * queries (one per activity type): at two users the whole day is a few dozen rows, and deriving the
 * cards in Kotlin keeps the ordering and the "latest per type" rules in one readable place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val events: EventRepository,
    private val children: ChildRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        children.observeAll(),
        settings.selectedChild,
        settings.units,
    ) { kids, selection, units -> Triple(kids, selection, units) }
        .flatMapLatest { combined ->
            val kids = combined.first
            val units = combined.third
            // Defense in depth, and not paranoia: a null here takes the entire app down on launch,
            // and it already happened once (see ChildSelection.default - Android stored null). Note
            // the nullable local: writing `combined.second ?: X` instead is constant-folded away
            // because the declared type is non-null, which is how the first attempt at this fallback
            // silently did nothing at all.
            val nullableSelection: ChildSelection? = combined.second
            val selection: ChildSelection = nullableSelection ?: ChildSelection.Both
            val ids = selection.resolve(kids.map { it.id })
            events.observeForChildren(ids).map { all -> build(kids, selection, ids, all, units) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun build(
        kids: List<Child>,
        selection: ChildSelection,
        ids: List<String>,
        all: List<BabyEvent>,
        units: app.babylog.domain.UnitPreferences,
    ) = HomeUiState(
        children = kids,
        selection = selection,
        childIds = ids,
        units = units,
        cards = buildCards(all),
    )

    fun select(selection: ChildSelection) = viewModelScope.launch { settings.setSelectedChild(selection) }

    fun logDiaper(kind: DiaperKind) = viewModelScope.launch {
        events.log(
            state.value.childIds,
            EventDraft(type = EventType.DIAPER, startedAt = System.currentTimeMillis(), diaperKind = kind),
            createdBy = CREATED_BY,
        )
    }

    fun startSleep() = viewModelScope.launch {
        events.startSleepTimer(state.value.childIds, createdBy = CREATED_BY)
    }

    fun stopSleep() = viewModelScope.launch {
        state.value.childIds.forEach { events.stopSleepTimer(it) }
    }

    /**
     * Phase 5 stub for the remaining activities: logs the event with only a timestamp, so the card
     * immediately shows "just now" and the layout can be judged. Phase 6 replaces this with the real
     * per-activity sheets described in `references/components.md`.
     */
    fun quickLog(type: EventType) = viewModelScope.launch {
        events.log(
            state.value.childIds,
            EventDraft(type = type, startedAt = System.currentTimeMillis()),
            createdBy = CREATED_BY,
        )
    }

    fun undo(event: BabyEvent) = viewModelScope.launch { events.undoDelete(event) }

    companion object {
        /** Until sign-in exists (Phase 11) every write is attributed to the local device. */
        const val CREATED_BY = "local"
    }
}
