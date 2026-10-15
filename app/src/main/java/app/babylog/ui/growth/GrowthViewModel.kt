package app.babylog.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.growth.GrowthReference
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.domain.EventType
import app.babylog.domain.UnitPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** What the Growth screen shows. [chart] is null only while the reference asset is loading. */
data class GrowthUiState(
    val metric: GrowthMetric = GrowthMetric.WEIGHT,
    val chart: GrowthChartData? = null,
    val childLabel: String = "",
    val loading: Boolean = true,
)

/**
 * The Growth screen's state: which measurement is charted, and the chart itself.
 *
 * Thin on purpose. Everything that decides what a number MEANS lives in [growthChart], so the screen
 * cannot compute a percentile differently from the callout beside it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrowthViewModel(
    private val events: EventRepository,
    private val children: ChildRepository,
    private val settings: SettingsRepository,
    private val reference: suspend () -> GrowthReference,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val metric = MutableStateFlow(GrowthMetric.WEIGHT)

    private val referenceState = MutableStateFlow<GrowthReference?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GrowthUiState> = combine(
        children.observeAll(),
        settings.selectedChild,
        settings.units,
        metric,
        referenceState,
    ) { kids, selection, units, metricChoice, loaded ->
        Parts(kids, selection, units, metricChoice, loaded)
    }
        .flatMapLatest { parts ->
            val reference = parts.reference
            if (reference == null || parts.kids.isEmpty()) {
                return@flatMapLatest MutableStateFlow(GrowthUiState(metric = parts.metric))
            }
            val selection: ChildSelection = parts.selection
            val ids = selection.resolve(parts.kids.map { it.id })
            val inScope = parts.kids.filter { it.id in ids }
            events.observeForChildren(ids).map { all ->
                GrowthUiState(
                    metric = parts.metric,
                    chart = growthChart(
                        children = inScope,
                        events = all.filter { it.type == EventType.GROWTH },
                        reference = reference,
                        metric = parts.metric,
                        units = parts.units,
                        zone = zone,
                    ),
                    childLabel = selectionLabel(selection, inScope),
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrowthUiState())

    init {
        // The reference is NOT loaded here. It is ~490 KB of JSON and only Trends' Growth tab needs it, so
        // `ensureLoaded` is called from that tab: a parent logging a 3am feed should not pay for a parse
        // they may never look at.
    }

    private var loadStarted = false

    /** Loads the growth reference once, on demand. Safe to call from a composition effect. */
    fun ensureLoaded() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch { referenceState.value = reference() }
    }

    fun selectMetric(choice: GrowthMetric) {
        metric.value = choice
    }

    private data class Parts(
        val kids: List<Child>,
        val selection: ChildSelection,
        val units: UnitPreferences,
        val metric: GrowthMetric,
        val reference: GrowthReference?,
    )
}

/** "for both" / "for First Twin", matching the sheets' wording. */
private fun selectionLabel(selection: ChildSelection, inScope: List<Child>): String = when {
    inScope.size == 2 -> "for both"
    inScope.size == 1 -> "for ${inScope.first().name}"
    else -> ""
}
