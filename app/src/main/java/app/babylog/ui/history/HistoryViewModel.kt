package app.babylog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.ActivityTotal
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.domain.EventType
import app.babylog.domain.HistoryRange
import app.babylog.domain.NightWindow
import app.babylog.domain.UnitPreferences
import app.babylog.domain.dayRange
import app.babylog.domain.totalsFor
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val children: List<Child> = emptyList(),
    val selection: ChildSelection = ChildSelection.Both,
    val range: HistoryRange = HistoryRange.DAY,
    val totals: List<ActivityTotal> = emptyList(),
    val childCount: Int = 0,
) {
    /** A per-child line only says something when more than one child is in scope. */
    val showPerChild: Boolean get() = childCount > 1
}

/**
 * History's state. Mirrors `HomeViewModel`'s selection handling deliberately: the child switcher drives
 * both screens, so "who am I looking at" cannot mean two different things on two tabs.
 *
 * The range read is OBSERVED rather than taken once, so logging on the Activity tab is reflected here
 * without a relaunch. `clock` and `zone` are injected so a test can pin the day boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val events: EventRepository,
    private val children: ChildRepository,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _range = MutableStateFlow(HistoryRange.DAY)
    val range: StateFlow<HistoryRange> = _range.asStateFlow()

    /** Types the user has filtered OUT. Empty means everything is shown. */
    private val _hidden = MutableStateFlow<Set<EventType>>(emptySet())
    val hidden: StateFlow<Set<EventType>> = _hidden.asStateFlow()

    private data class Inputs(
        val kids: List<Child>,
        val selection: ChildSelection,
        val units: UnitPreferences,
        val night: NightWindow,
        val range: HistoryRange,
    )

    val state: StateFlow<HistoryUiState> = combine(
        children.observeAll(),
        settings.selectedChild,
        settings.units,
        settings.nightWindow,
        _range,
    ) { kids, selection, units, night, range -> Inputs(kids, selection, units, night, range) }
        .combine(_hidden) { inputs, hidden -> inputs to hidden }
        .flatMapLatest { (inputs, hidden) ->
            val ids = inputs.selection.resolve(inputs.kids.map { it.id })
            // The window is computed once per subscription. Calendar days are stable for the day, so a
            // relaunch or a tab switch is enough; only an app left open across midnight would lag.
            val window = dayRange(clock.millis(), zone, inputs.range.days)
            events.observeBetween(ids, window.from, window.to).map { rows ->
                HistoryUiState(
                    children = inputs.kids,
                    selection = inputs.selection,
                    range = inputs.range,
                    totals = totalsFor(
                        events = rows,
                        children = inputs.kids.filter { it.id in ids },
                        units = inputs.units,
                        night = inputs.night,
                        zone = zone,
                    ).filter { it.type !in hidden },
                    childCount = ids.size,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setRange(range: HistoryRange) {
        _range.value = range
    }

    /** Toggles one activity on or off. Filtering is a view choice, so it is not persisted. */
    fun toggleFilter(type: EventType) {
        _hidden.value = if (type in _hidden.value) _hidden.value - type else _hidden.value + type
    }
}
