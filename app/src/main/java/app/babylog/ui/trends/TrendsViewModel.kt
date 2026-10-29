package app.babylog.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.CHARTED_TYPES
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.domain.EventType
import app.babylog.domain.HealthTrend
import app.babylog.domain.TrendRange
import app.babylog.domain.TrendSeries
import app.babylog.domain.UnitPreferences
import app.babylog.domain.healthTrend
import app.babylog.domain.trendSeries
import app.babylog.domain.trendWindow
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

/**
 * Trends' state: which activity and which window are being looked at, plus the numbers for them.
 *
 * The child selection is NOT owned here — it is the same stored selection the Activity and Summary tabs
 * use, exactly as History does it, because "who am I looking at" must not mean two different things on
 * two tabs.
 *
 * `zone` is carried in the state rather than read by the screen, so the day a bar represents and the
 * date under that bar come from one timezone. Two reads of the device default could disagree across a
 * midnight boundary, and the mismatch would be invisible.
 */
data class TrendsUiState(
    val type: EventType = EventType.BOTTLE,
    val range: TrendRange = TrendRange.FORTNIGHT,
    val units: UnitPreferences = UnitPreferences.Metric,
    val series: TrendSeries? = null,
    val health: HealthTrend? = null,
    val childLabel: String = "",
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * Reads the window's events ONCE and derives whichever pane is on screen from them.
 *
 * The range is re-derived inside the subscription rather than stored, so the buckets and the axis cannot
 * disagree: both come from the same `now`. `clock` and `zone` are injected so a test can pin the day
 * boundary on the JVM — the same reason History does it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(
    private val events: EventRepository,
    private val children: ChildRepository,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _range = MutableStateFlow(TrendRange.FORTNIGHT)
    val range: StateFlow<TrendRange> = _range.asStateFlow()

    private val _type = MutableStateFlow(EventType.BOTTLE)
    val type: StateFlow<EventType> = _type.asStateFlow()

    private data class Inputs(
        val kids: List<Child>,
        val selection: ChildSelection,
        val units: UnitPreferences,
        val range: TrendRange,
        val type: EventType,
    )

    val state: StateFlow<TrendsUiState> = combine(
        children.observeAll(),
        settings.selectedChild,
        settings.units,
        _range,
        _type,
    ) { kids, selection, units, range, type -> Inputs(kids, selection, units, range, type) }
        .flatMapLatest { inputs ->
            val ids = inputs.selection.resolve(inputs.kids.map { it.id })
            val inScope = inputs.kids.filter { it.id in ids }
            // One `now` for the window, the buckets and the labels.
            val now = clock.millis()
            // `All` buckets by month from the first entry, so the window needs to know when the log begins.
            // Read here rather than in the screen: the window and the buckets must come from one answer.
            val firstEventAt = events.earliest(ids)
            val window = trendWindow(inputs.range, now, zone, firstEventAt)
            events.observeBetween(ids, window.from, window.to).map { rows ->
                TrendsUiState(
                    type = inputs.type,
                    range = inputs.range,
                    units = inputs.units,
                    series = if (inputs.type in CHARTED_TYPES) {
                        trendSeries(
                            type = inputs.type,
                            children = inScope,
                            events = rows,
                            range = inputs.range,
                            now = now,
                            zone = zone,
                            units = inputs.units,
                            firstEventAt = firstEventAt,
                        )
                    } else {
                        null
                    },
                    health = if (inputs.type == EventType.HEALTH) {
                        healthTrend(inScope, rows, zone)
                    } else {
                        null
                    },
                    childLabel = selectionLabel(inScope),
                    zone = zone,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState(zone = zone))

    fun selectRange(choice: TrendRange) {
        _range.value = choice
    }

    fun selectType(choice: EventType) {
        _type.value = choice
    }
}

/** "for both" / "for First Twin", matching the sheets' wording. */
private fun selectionLabel(inScope: List<Child>): String = when {
    inScope.size == 2 -> "for both"
    inScope.size == 1 -> "for ${inScope.first().name}"
    else -> ""
}
