package app.babylog.domain

/**
 * The four activities the owner actually logs, plus the two he wants present.
 *
 * Nursing and pumping were in the schema's first draft and are deliberately absent: he logs bottle and
 * solids only, and unused variants — with no accent colour, no card and no sheet — are how a schema
 * acquires dead weight. Adding a type later is an enum value, an accent, a card and a sheet.
 */
enum class EventType(
    /**
     * Whether this activity is an INTERVAL rather than a point in time. Only sleep is.
     *
     * This exists because `endedAt == null` alone cannot distinguish "still in progress" from "a
     * moment that has no end at all". Without it every logged bottle, diaper, weigh-in and
     * temperature read as a running session, and the dashboard put a live count-up timer on all six
     * cards.
     */
    val hasDuration: Boolean = false,
) {
    BOTTLE,
    SOLIDS,
    DIAPER,
    GROWTH,
    HEALTH,
    SLEEP(hasDuration = true),
}

/** DELIBERATELY three. Both source apps shipped a clinical taxonomy nobody asks for at 3am. */
enum class DiaperKind { WET, DIRTY, MIXED }

/**
 * The temperature at or above which a reading is shown as a fever.
 *
 * 38.0 °C is the usual threshold for a core temperature, and the word describes the NUMBER, not the
 * child: an axillary (armpit) reading runs lower than a core one, so a flagged reading is a prompt to
 * look, not a diagnosis. It lives here, once, because the card, the Both-mode columns and History must
 * never disagree about which readings are flagged.
 */
const val FEVER_C = 38.0

/**
 * The order activities are presented in. The dashboard cards and the History totals both read from
 * this, so the two screens can never disagree about where an activity lives.
 *
 * Fixed by how often each is used. Sleep is LAST and present: it is tracked rarely but was asked for
 * explicitly, so do not "optimise" it away.
 */
val EVENT_DISPLAY_ORDER: List<EventType> = listOf(
    EventType.BOTTLE,
    EventType.DIAPER,
    EventType.SOLIDS,
    EventType.GROWTH,
    EventType.HEALTH,
    EventType.SLEEP,
)

enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

/** the reference app's exact wording for the solids reaction field. */
enum class FoodReaction { LOVED_IT, ALLERGY_OR_SENSITIVITY }

/**
 * A single logged event. `endedAt == null` on a SLEEP event means the timer is running; on a point
 * event it simply means there is no end time, which is not the same thing.
 *
 * One table with per-type optional fields rather than six tables: the types share almost everything (a
 * time, a child, a note, a creator) and differ in a few columns. Six tables would mean six DAOs and six
 * sync paths for no benefit at this size.
 *
 * Timestamps are epoch millis UTC. Quantities are canonical metric — grams, millimetres, millilitres —
 * converted for display only. Temperature is stored in Celsius; display and input follow the preference.
 */
data class BabyEvent(
    val id: String,
    val childId: String,
    val type: EventType,
    val startedAt: Long,
    val endedAt: Long? = null,
    // BOTTLE
    val amountMl: Int? = null,
    val formulaBrand: String? = null,
    // SOLIDS
    val mealSlot: MealSlot? = null,
    val foodIds: List<String> = emptyList(),
    val reaction: FoodReaction? = null,
    // DIAPER
    val diaperKind: DiaperKind? = null,
    val rash: Boolean? = null,
    // GROWTH
    val weightGrams: Int? = null,
    val lengthMm: Int? = null,
    val headMm: Int? = null,
    // HEALTH
    val temperatureC: Double? = null,
    val medication: String? = null,
    /**
     * The dose given, as the parent recorded it: an amount and the measure it was given in.
     *
     * Both halves are stored because the app must never CONVERT a dose — 2.5 ml and 2.5 mg are the same
     * number and not the same thing, and a future reader of this log needs to know which was written.
     * Null on everything that is not a dose; the pair is always set or unset together.
     */
    val doseAmount: Double? = null,
    val doseUnit: DoseUnit? = null,
    val vaccineName: String? = null,
    // any
    val note: String? = null,
    val createdBy: String,
    val updatedAt: Long,
    /** Soft-delete tombstone: sync must propagate deletions. Never hard-delete. */
    val deleted: Boolean = false,
) {
    /**
     * A live timer. Requires BOTH a duration-capable type and no end yet — `endedAt == null` on its
     * own is also true of every point event, which is how a logged diaper got a live timer.
     */
    val isRunning: Boolean get() = type.hasDuration && endedAt == null
    val durationMs: Long? get() = endedAt?.let { it - startedAt }
}
