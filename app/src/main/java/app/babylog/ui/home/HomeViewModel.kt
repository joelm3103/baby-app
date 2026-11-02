package app.babylog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.babylog.data.local.FoodEntity
import app.babylog.data.repo.ChildRepository
import app.babylog.data.repo.EventDraft
import app.babylog.data.repo.EventRepository
import app.babylog.data.repo.FoodRepository
import app.babylog.data.repo.SettingsRepository
import app.babylog.domain.BabyEvent
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.domain.DiaperKind
import app.babylog.domain.TimeFormat
import app.babylog.domain.nextReminderAt
import app.babylog.domain.reminderChipText
import app.babylog.domain.REMINDER_TYPES
import app.babylog.domain.EventType
import app.babylog.domain.displayFoodName
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import app.babylog.domain.upcomingReminders
import app.babylog.domain.UpcomingReminder
import app.babylog.domain.ReminderKey
import app.babylog.domain.ReminderArm
import app.babylog.domain.ReminderChip
import app.babylog.domain.ReminderConfig
import app.babylog.domain.NightWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardUi(
    val type: EventType,
    val last: BabyEvent?,
    /** Every entry of this activity in scope, newest first. The card draws today's slice of it. */
    val entries: List<BabyEvent>,
    val running: BabyEvent?,
)

data class HomeUiState(
    val children: List<Child> = emptyList(),
    val selection: ChildSelection = ChildSelection.Both,
    val childIds: List<String> = emptyList(),
    val cards: List<CardUi> = emptyList(),
    val units: app.babylog.domain.UnitPreferences = app.babylog.domain.UnitPreferences(),
    /**
     * `id -> name` for the solids catalogue.
     *
     * A solids row stores ids only; every surface that SHOWS one needs the name. Carried in the state
     * rather than fed to the cards one by one so the card, the entry rows and the full list cannot
     * disagree about what a meal was called.
     */
    val foodNames: Map<String, String> = emptyMap(),
    /**
     * Activity cards this parent has hidden in Settings. Empty is the default: nothing is hidden.
     *
     * Kept beside [cards] rather than applied to it so the screen can say WHY it is empty — all hidden,
     * or no children yet — and so nothing else reading `cards` inherits a display choice.
     */
    val hidden: Set<EventType> = emptySet(),
    /**
     * The amounts worth one tap on the Bottle feed card, in ml, in the order they were added.
     *
     * Canonical millilitres, like the settings that own them: the card labels them in whatever unit family
     * the parent reads, and a preset re-read after a unit change must not have moved. Empty draws no pills
     * at all rather than an empty row.
     */
    val bottlePresets: List<Int> = emptyList(),
) {
    /** The cards the Activity tab actually draws. */
    val shownCards: List<CardUi> get() = cards.filterNot { it.type in hidden }

    val anyRunning: Boolean get() = cards.any { it.running != null }

    /** Every entry of one activity in scope, newest first — what the "See all" list draws. */
    fun entriesOf(type: EventType): List<BabyEvent> = cards.firstOrNull { it.type == type }?.entries.orEmpty()

    /** The name behind a row's badge, for the edit sheet's subtitle in Both mode. */
    fun childName(childId: String): String = children.firstOrNull { it.id == childId }?.name.orEmpty()

    /** "both" or the child's name — used to make the action button say who it will log for. */
    val childLabel: String
        get() = when (val s = selection) {
            is ChildSelection.Both -> if (children.size == 2) "both" else children.firstOrNull()?.name.orEmpty()
            is ChildSelection.One -> children.firstOrNull { it.id == s.childId }?.name.orEmpty()
        }

    /**
     * Pre-fills the bottle sheet. A parent logs the same 120 ml far more often than a new number, so
     * opening the sheet on the last amount is the difference between one tap and twelve.
     */
    val lastBottleAmountMl: Int?
        get() = cards.firstOrNull { it.type == EventType.BOTTLE }?.last?.amountMl
}

/**
 * Which logging sheet to raise. Almost always just an [EventType], but Health has TWO forms — a
 * temperature/medication record and a vaccine — and they are separate records with separate fields, so
 * "open Health" alone would be ambiguous.
 *
 * [vaccine] is only meaningful for `EventType.HEALTH` and is ignored elsewhere.
 */
data class SheetTarget(
    val type: EventType,
    val vaccine: Boolean = false,
    /**
     * Set when the sheet was opened from a notification, and it is the whole point of that tap.
     *
     * The app's child switcher may be pointing at the OTHER twin - a reminder names one child, so the sheet
     * must log for that child regardless of what the header says. Null means "whatever the switcher has", which
     * is what every other way of opening a sheet wants.
     */
    val childId: String? = null,
)

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
    private val foods: FoodRepository,
) : ViewModel() {

    /**
     * The solids catalogue, recently used first. Observed, so logging a food moves it to the top of the
     * picker next time it opens without any manual refresh.
     */
    val foodList: StateFlow<List<FoodEntity>> = foods.observeCatalogue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which logging sheet is open, if any. A separate flow from [state] so an event emission cannot reset it. */
    private val _openSheet = MutableStateFlow<SheetTarget?>(null)
    val openSheet: StateFlow<SheetTarget?> = _openSheet.asStateFlow()

    /**
     * Which activity's full entry list is open, if any — the "See all" overlay.
     *
     * Its own flow rather than a field on the sheet state, because the two stack: the list stays open
     * underneath the edit sheet it launches.
     */
    private val _openEntries = MutableStateFlow<EventType?>(null)
    val openEntries: StateFlow<EventType?> = _openEntries.asStateFlow()

    /** The row currently being edited, if any. Set by tapping a row, cleared on save or dismiss. */
    private val _editing = MutableStateFlow<BabyEvent?>(null)
    val editing: StateFlow<BabyEvent?> = _editing.asStateFlow()

    /**
     * The state's five inputs, folded into one value first.
     *
     * Two `combine` calls rather than one, because Kotlin's typed `combine` overloads stop at five flows and
     * the sixth would silently fall through to the `Array<Any>` vararg form — which is what a `List<Child>`
     * becoming `Array<Any>` looks like at the call site. The inner combine keeps every type named.
     */
    private val selectionInputs: Flow<Quad> = combine(
        children.observeAll(),
        settings.selectedChild,
        settings.units,
        // The solids catalogue: `id -> name`, for every surface that shows a meal.
        foodList,
        // Which cards a parent has hidden. Carried in the state rather than filtered here, because the
        // screen has to tell "nothing to show because everything is hidden" apart from "no children yet",
        // and a pre-filtered list cannot answer that question.
        settings.hiddenActivities,
    ) { kids, selection, units, foods, hidden -> Quad(kids, selection, units, foods, hidden, null) }

    val state: StateFlow<HomeUiState> = combine(
        selectionInputs,
        // The one-tap amounts on the Bottle feed card. In the state rather than read by the card, so a
        // preset added in Settings appears on the card without the card knowing Settings exists.
        settings.bottlePresets,
    ) { quad, presets -> quad.copy(presets = presets) }
        .flatMapLatest { combined ->
            val kids = combined.kids
            val units = combined.units
            val hidden = combined.hidden
            // Defense in depth, and not paranoia: a null here takes the entire app down on launch,
            // and it already happened once (see ChildSelection.default - Android stored null). Note
            // the nullable local: writing `combined.selection ?: X` instead is constant-folded away
            // because the declared type is non-null, which is how the first attempt at this fallback
            // silently did nothing at all.
            val nullableSelection: ChildSelection? = combined.selection
            val selection: ChildSelection = nullableSelection ?: ChildSelection.Both
            val ids = selection.resolve(kids.map { it.id })
            val names = combined.foods.associate { it.id to displayFoodName(it.name, it.localName) }
            events.observeForChildren(ids).map { all ->
                build(kids, selection, ids, all, units, names, hidden, combined.presets.orEmpty())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Everything the reminder UI needs: the "Upcoming reminders" card's rows, and the per-card chip.
     *
     * One flow rather than two, because both are the same question asked twice - "when is the next reminder?" -
     * and two flows would compute the same anchors twice and could disagree for a frame.
     *
     * Computed here rather than in a composable because it needs the whole log (an anchor per activity and child,
     * not just the selected child's rows) and the stored rules, and because "which reminders are armed" is a fact
     * about the data that a test can pin.
     */
    val remindersUi: StateFlow<RemindersUiState> = combine(
        children.observeAll(),
        settings.reminders,
        settings.nightWindow,
        settings.timeFormat,
        settings.selectedChild,
    ) { kids, configs, night, format, selection ->
        ReminderRules(kids, configs, night, format, selection)
    }.flatMapLatest { rules ->
        allChildrenEvents().map { all -> rules to all }
    }.map { (rules, all) ->
        // Newest event per (activity, child), in one pass: asking the database eight times for a value that is
        // already in memory would be eight queries for the same list.
        val newest = all.groupBy { it.type to it.childId }.mapValues { (_, rows) -> rows.maxOf { it.startedAt } }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val anchorFor: (EventType, String) -> Long? = { type, childId -> newest[type to childId] }

        val upcoming = upcomingReminders(
            configs = rules.configs,
            children = rules.children,
            anchorFor = anchorFor,
            now = now,
            zone = zone,
            night = rules.night,
        )

        // The chips follow the SWITCHER, because the cards do: a card showing both children says one thing about
        // both, and a card showing one child says that child's.
        val inScope = rules.selection.resolve(rules.children.map { it.id })
        val bothOnScreen = inScope.size > 1
        val chips = REMINDER_TYPES.mapNotNull { type ->
            // One chip per child in scope, each with its own next time. With both children on screen that is two
            // chips, each carrying that child's badge, so which twin is due when needs no reading.
            val perChild = inScope.mapNotNull { childId ->
                val config = rules.configs[ReminderKey(type, childId)] ?: return@mapNotNull null
                if (!config.enabled) return@mapNotNull null
                val at = nextReminderAt(config, anchorFor(type, childId), now, zone, rules.night)
                    ?: return@mapNotNull null
                ReminderChip(
                    childId = if (bothOnScreen) childId else null,
                    text = reminderChipText(config, at, zone, rules.format, now),
                )
            }
            perChild.takeIf { it.isNotEmpty() }?.let { type to it }
        }.toMap()

        RemindersUiState(upcoming = upcoming, chips = chips)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    /** The stored rules, for the sheet to prefill from. */
    val reminderConfigs: StateFlow<Map<ReminderKey, ReminderConfig>> = settings.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Whether notifications are allowed.
     *
     * Sampled once per subscription rather than polled: the only ways it changes are the permission dialog this app
     * raises and the system's own settings screen, and the sheet re-reads it every time it opens.
     */
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    /** Re-read after the permission dialog or a trip to the system settings, both of which happen outside the app. */
    fun refreshNotificationState() {
        _notificationsEnabled.value = checkNotificationsEnabled()
    }

    /**
     * Supplied by the caller because the answer lives on the container (NotificationManagerCompat needs a Context),
     * and a ViewModel that reached for a Context would stop being testable.
     */
    var checkNotificationsEnabled: () -> Boolean = { true }

    /** Save one child's rule, or clear it. Called from the card's menu. */
    fun saveReminder(type: EventType, childId: String, config: ReminderConfig) = viewModelScope.launch {
        // Stamp when the rule was set: it is the anchor for a child who has never had this activity logged, so a
        // brand-new reminder rings once instead of never.
        val stamped = if (config.enabled) config.copy(anchorAt = System.currentTimeMillis()) else config
        settings.setReminder(ReminderKey(type, childId), stamped)
        // An alarm has to be armed or cancelled the moment the rule changes, not at the next launch.
        onRemindersChanged()
    }

    fun removeReminder(type: EventType, childId: String) = viewModelScope.launch {
        settings.setReminder(ReminderKey(type, childId), ReminderConfig())
        onRemindersChanged()
    }

    /**
     * Re-arm after a rule change.
     *
     * A callback rather than a container reference: the ViewModel already knows how to write a rule, and the
     * scheduler lives on the container, so the caller supplies the one line that touches it.
     */
    var onRemindersChanged: suspend () -> Unit = {}

    /** Every child's rows, regardless of the switcher: an anchor belongs to a child, not to the selection. */
    private fun allChildrenEvents() = children.observeAll()
        .map { kids -> kids.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> if (ids.isEmpty()) flowOf(emptyList()) else events.observeForChildren(ids) }

    init {
        // Keep the stored selection valid as children are added or removed. Only a selection that has
        // become IMPOSSIBLE is corrected — a deliberate single-child choice at two children is left
        // alone. The guard against writing an identical value keeps this from re-triggering itself.
        viewModelScope.launch {
            combine(children.observeAll(), settings.selectedChild) { kids, selection -> kids to selection }
                .collect { (kids, selection) ->
                    if (!selection.isValidFor(kids)) {
                        val fixed = ChildSelection.fallbackFor(kids)
                        if (fixed != selection) settings.setSelectedChild(fixed)
                    }
                }
        }
    }

    private fun build(
        kids: List<Child>,
        selection: ChildSelection,
        ids: List<String>,
        all: List<BabyEvent>,
        units: app.babylog.domain.UnitPreferences,
        foodNames: Map<String, String>,
        hidden: Set<EventType>,
        bottlePresets: List<Int>,
    ) = HomeUiState(
        children = kids,
        selection = selection,
        childIds = ids,
        units = units,
        cards = buildCards(all, kids.map { it.id }),
        foodNames = foodNames,
        hidden = hidden,
        bottlePresets = bottlePresets,
    )

    fun select(selection: ChildSelection) = viewModelScope.launch { settings.setSelectedChild(selection) }

    fun logDiaper(kind: DiaperKind) = viewModelScope.launch {
        events.log(
            state.value.childIds,
            EventDraft(type = EventType.DIAPER, startedAt = System.currentTimeMillis(), diaperKind = kind),
            createdBy = CREATED_BY,
        )
    }

    /**
     * One tap on a preset logs that feed, with no sheet.
     *
     * The same shape as [logDiaper], and for the same reason: the amount is the whole answer, so a parent
     * who already knows it should not have to open a form to repeat it. A preset that is not a bottle is
     * refused by [quickBottleDraft] rather than written.
     */
    fun logBottle(amountMl: Int) = viewModelScope.launch {
        val draft = quickBottleDraft(amountMl, System.currentTimeMillis()) ?: return@launch
        events.log(state.value.childIds, draft, createdBy = CREATED_BY)
    }

    fun startSleep() = viewModelScope.launch {
        events.startSleepTimer(state.value.childIds, createdBy = CREATED_BY)
    }

    fun stopSleep() = viewModelScope.launch {
        state.value.childIds.forEach { events.stopSleepTimer(it) }
    }

    /**
     * Commits a sheet's draft. The children list is resolved here, once, and handed to a SINGLE
     * `log` call — so Both mode writes one row per child with an identical timestamp instead of the
     * sheet knowing (or caring) how many children are in scope.
     */
    fun save(draft: EventDraft, childIds: List<String> = state.value.childIds) {
        val ids = childIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            events.log(ids, draft, createdBy = CREATED_BY)
            _openSheet.value = null
        }
    }

    /**
     * Opens the logging sheet for [type].
     *
     * Deliberately unguarded. This used to be `if (type in SHEET_TYPES)`, where the set listed the three
     * types that had sheets in Phase 6 - so when Growth's sheet landed, its button looked live, had the
     * right label, and did nothing at all. The sheet host in `AppScaffold` now names every type explicitly
     * and has no `else`, so adding an EventType without deciding its sheet is a compile error instead of a
     * dead control. Found on the S23, not by the build.
     */
    fun openSheet(type: EventType) {
        _openSheet.value = SheetTarget(type)
    }

    /**
     * Opens the sheet for one child, which is what a reminder's tap does.
     *
     * Separate from [openSheet] rather than an optional argument, so a caller that has a child id cannot forget
     * it and quietly log for whoever the header happens to be showing.
     */
    fun openSheetFor(type: EventType, childId: String) {
        _openSheet.value = SheetTarget(type, childId = childId)
    }

    /** The Health card's second action: the vaccine form, not the temperature one. */
    fun openVaccineRecord() {
        _openSheet.value = SheetTarget(EventType.HEALTH, vaccine = true)
    }

    fun dismissSheet() {
        _openSheet.value = null
    }

    fun openEntries(type: EventType) {
        // A sheet and the list are two ways to look at the same activity; opening one closes the other
        // so the back stack matches what is on screen.
        _openSheet.value = null
        _openEntries.value = type
    }

    fun dismissEntries() {
        _openEntries.value = null
    }

    /** Opens the row for editing: the sheet comes up pre-filled and saves back onto this same event. */
    fun editEntry(event: BabyEvent) {
        _editing.value = event
    }

    fun dismissEdit() {
        _editing.value = null
    }

    /**
     * Commits an edit. Identity comes from the row being edited, never from the sheet, so a Both-mode
     * pair stays two independent rows and an edit cannot move a feed to the other child.
     */
    fun saveEdit(original: BabyEvent, draft: EventDraft) = viewModelScope.launch {
        events.update(draft.applyTo(original, updatedAt = System.currentTimeMillis()))
        _editing.value = null
    }

    /** Soft delete, returning the tombstone so the caller can offer Undo. */
    suspend fun deleteEntry(event: BabyEvent): BabyEvent = events.softDelete(event)

    /** Called from the picker's coroutine scope; the new food appears (and sorts) on its own via the flow. */
    suspend fun addCustomFood(name: String): FoodEntity = foods.addCustom(name)

    /** Renames a food: a local override for a catalogue entry, a real rename for this phone's addition. */
    suspend fun renameFood(id: String, name: String) = foods.rename(id, name)

    /** Takes a food out of this phone's picker, or puts it back. Never leaves the device. */
    suspend fun setFoodHidden(id: String, hidden: Boolean) = foods.setHidden(id, hidden)

    /** Removes a food this phone added. A catalogue entry is refused by the repository — hide it instead. */
    suspend fun deleteFood(id: String) = foods.delete(id)

    fun undo(event: BabyEvent) = viewModelScope.launch { events.undoDelete(event) }

    companion object {
        /** Until sign-in exists (Phase 13) every write is attributed to the local device. */
        const val CREATED_BY = "local"
    }
}

/** What the reminder UI shows: the upcoming rows, and the per-card chips keyed by activity. */
data class RemindersUiState(
    val upcoming: List<UpcomingReminder> = emptyList(),
    val chips: Map<EventType, List<ReminderChip>> = emptyMap(),
)

/** The five stored inputs the reminder computation needs, bundled so the `combine` stays readable. */
private data class ReminderRules(
    val children: List<Child>,
    val configs: Map<ReminderKey, ReminderConfig>,
    val night: NightWindow,
    val format: TimeFormat,
    val selection: ChildSelection,
)

/**
 * The dashboard's four inputs.
 *
 * A named type rather than a `data class Quadruple`, so `combined.kids` reads as what it is. `Triple`
 * was the previous shape and its `.first` / `.third` made the food catalogue easy to insert in the
 * wrong slot.
 */
private data class Quad(
    val kids: List<Child>,
    val selection: ChildSelection,
    val units: app.babylog.domain.UnitPreferences,
    val foods: List<app.babylog.data.local.FoodEntity>,
    val hidden: Set<EventType>,
    /** Null only inside [HomeViewModel.selectionInputs], which fills it in as the second input. */
    val presets: List<Int>?,
)
