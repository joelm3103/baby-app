package app.babylog.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * History totals, computed in plain Kotlin.
 *
 * Deliberately NOT SQL aggregates. A day boundary and the day/night diaper split both depend on the
 * local timezone, which SQLite cannot resolve — aggregating in SQL would silently use UTC and file an
 * 8pm diaper under the wrong day. Every function here takes `zone` (and usually `now`) explicitly, so
 * nothing can quietly fall back to the device default and the whole file is testable on the JVM.
 */

/** The ranges the History screen offers. Days, not hours — see [dayRange]. */
enum class HistoryRange(val days: Int, val label: String) {
    DAY(1, "1d"),
    WEEK(7, "7d"),
    FORTNIGHT(14, "14d"),
}

/** A half-open window, `[from, to)`. */
data class TimeRange(val from: Long, val to: Long)

/**
 * When "night" is, as hour-of-day boundaries in the local zone.
 *
 * Night is `[startHour, endHour)` and WRAPS midnight, which is the normal case (18:00-06:00). If the two
 * are equal the window is EMPTY and everything counts as day — a defined rule rather than a
 * division-shaped surprise, and it is what the Settings screen produces if both steppers land on the
 * same hour.
 */
data class NightWindow(val startHour: Int = 18, val endHour: Int = 6) {
    val isEmpty: Boolean get() = startHour == endHour
}

/**
 * The last [days] CALENDAR days ending with today, in [zone], as a half-open `[from, to)`.
 *
 * Calendar days rather than a rolling 24/168/336 hours: the question this screen answers is "how are we
 * doing today", and a bottle at 00:30 belongs to the new day rather than to the previous 24 hours.
 */
fun dayRange(now: Long, zone: ZoneId, days: Int): TimeRange {
    require(days >= 1) { "a range covers at least one day" }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return TimeRange(
        from = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
        to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

/** Whether [startedAt] falls inside the night window, in [zone]. */
fun isNight(startedAt: Long, zone: ZoneId, window: NightWindow): Boolean {
    if (window.isEmpty) return false
    val hour = Instant.ofEpochMilli(startedAt).atZone(zone).hour
    return if (window.startHour < window.endHour) {
        hour >= window.startHour && hour < window.endHour
    } else {
        // Wraps midnight: 18:00-06:00 means hour >= 18 OR hour < 6.
        hour >= window.startHour || hour < window.endHour
    }
}

/** One supporting line under a total's headline. */
data class TotalRow(val label: String, val value: String)

/** One activity's totals for a range. */
data class ActivityTotal(
    val type: EventType,
    /** The big value, without its unit — the unit renders smaller beside it, as on the cards. */
    val headline: String,
    val unit: String? = null,
    val rows: List<TotalRow> = emptyList(),
    /** The same total per child, for Both mode. Always computed; the screen decides whether to show it. */
    val perChild: List<TotalRow> = emptyList(),
    /** True when there is nothing to show at all (Growth/Health with no measurement in range). */
    val empty: Boolean = false,
    /**
     * A Health total's band, from its latest reading. Null when the range holds no reading, or the
     * reading is NORMAL — the total CARDS are tinted with the activity's own accent exactly like the
     * dashboard, so the marker uses the same tokens there.
     */
    val temperatureBand: TemperatureBand? = null,
)

private val measuredOn = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * One total per activity, in the shared display order.
 *
 * [events] must already be scoped to the range and to the children in view; [children] is only used to
 * label the per-child line, so pass the children actually in scope.
 */
fun totalsFor(
    events: List<BabyEvent>,
    children: List<Child>,
    units: UnitPreferences,
    night: NightWindow,
    zone: ZoneId,
): List<ActivityTotal> = EVENT_DISPLAY_ORDER.map { type ->
    val forType = events.filter { it.type == type }
    when (type) {
        EventType.BOTTLE -> bottleTotal(forType, children, units)
        EventType.DIAPER -> diaperTotal(forType, children, night, zone)
        EventType.SOLIDS -> solidsTotal(forType, children)
        EventType.SLEEP -> sleepTotal(forType, children)
        EventType.GROWTH -> growthTotal(forType, children, units, zone)
        EventType.HEALTH -> healthTotal(forType, children, zone, units)
    }
}

// ---- per activity ---------------------------------------------------------

private fun bottleTotal(
    events: List<BabyEvent>,
    children: List<Child>,
    units: UnitPreferences,
): ActivityTotal {
    val totalMl = events.sumOf { it.amountMl ?: 0 }
    val withAmount = events.count { it.amountMl != null }
    val (headline, unit) = volumeHeadline(totalMl, units.volume)
    return ActivityTotal(
        type = EventType.BOTTLE,
        headline = headline,
        unit = unit,
        rows = listOf(
            TotalRow("Sessions", events.size.toString()),
            TotalRow(
                "Average bottle",
                if (withAmount == 0) DASH else formatVolume((totalMl.toDouble() / withAmount), units.volume),
            ),
            TotalRow("Time between feeds", averageGap(events)),
        ),
        perChild = children.map { child ->
            val ml = events.filter { it.childId == child.id }.sumOf { it.amountMl ?: 0 }
            TotalRow(child.name, formatVolume(ml.toDouble(), units.volume))
        },
    )
}

private fun diaperTotal(
    events: List<BabyEvent>,
    children: List<Child>,
    night: NightWindow,
    zone: ZoneId,
): ActivityTotal {
    val nightCount = events.count { isNight(it.startedAt, zone, night) }
    return ActivityTotal(
        type = EventType.DIAPER,
        headline = events.size.toString(),
        rows = listOf(
            TotalRow("Day", (events.size - nightCount).toString()),
            TotalRow("Night", nightCount.toString()),
        ),
        perChild = children.map { child ->
            TotalRow(child.name, events.count { it.childId == child.id }.toString())
        },
    )
}

private fun solidsTotal(events: List<BabyEvent>, children: List<Child>): ActivityTotal {
    val foods = events.flatMap { it.foodIds }.distinct()
    return ActivityTotal(
        type = EventType.SOLIDS,
        headline = events.size.toString(),
        rows = listOf(TotalRow("Foods logged", foods.size.toString())),
        perChild = children.map { child ->
            TotalRow(child.name, events.count { it.childId == child.id }.toString())
        },
    )
}

private fun sleepTotal(events: List<BabyEvent>, children: List<Child>): ActivityTotal {
    // A running session has no end, so it contributes a session but no duration. Counting it as zero
    // minutes is honest; guessing "until now" would make the total grow while nobody is looking.
    val totalMs = events.sumOf { event -> event.endedAt?.let { it - event.startedAt } ?: 0L }
    return ActivityTotal(
        type = EventType.SLEEP,
        headline = elapsed(totalMs),
        rows = listOf(TotalRow("Sessions", events.size.toString())),
        perChild = children.map { child ->
            val ms = events.filter { it.childId == child.id }
                .sumOf { event -> event.endedAt?.let { it - event.startedAt } ?: 0L }
            TotalRow(child.name, elapsed(ms))
        },
    )
}

private fun growthTotal(
    events: List<BabyEvent>,
    children: List<Child>,
    units: UnitPreferences,
    zone: ZoneId,
): ActivityTotal {
    val measured = events.filter { it.weightGrams != null || it.lengthMm != null || it.headMm != null }
    // Per-FIELD latest, matching the Activity card's rule: a weight logged today and a length logged last
    // week are both the current answer, and taking only the newest entry hid the older measurement.
    val latestWeight = measured.filter { it.weightGrams != null }.maxByOrNull { it.startedAt }
    val latestLength = measured.filter { it.lengthMm != null }.maxByOrNull { it.startedAt }
    val latestHead = measured.filter { it.headMm != null }.maxByOrNull { it.startedAt }
    val latest = latestWeight ?: latestLength ?: latestHead
    if (latest == null) {
        return ActivityTotal(
            type = EventType.GROWTH,
            headline = DASH,
            empty = true,
            perChild = children.map { TotalRow(it.name, DASH) },
        )
    }

    val weight = latestWeight?.weightGrams
    val length = latestLength?.lengthMm
    val head = latestHead?.headMm
    // Headline is the weight when there is one, since that is the measurement parents track; otherwise
    // the first measurement that exists. The rest become rows rather than disappearing.
    val (headline, unit) = when {
        weight != null -> weightHeadline(weight, units.weight)
        length != null -> lengthHeadline(length, units.length)
        else -> lengthHeadline(head!!, units.length)
    }
    // Each of the three shown against its OWN date, because with per-field latest they can differ.
    val dateOf = { at: Long ->
        Instant.ofEpochMilli(at).atZone(zone).toLocalDate().format(measuredOn)
    }
    val rows = buildList {
        if (length != null && weight != null) {
            add(TotalRow("Length · ${dateOf(latestLength!!.startedAt)}", formatLength(length.toDouble(), units.length)))
        }
        if (head != null && (weight != null || length != null)) {
            add(TotalRow("Head · ${dateOf(latestHead!!.startedAt)}", formatLength(head.toDouble(), units.length)))
        }
        if (weight != null && length == null && head == null) {
            add(TotalRow("Weight", formatWeight(weight.toDouble(), units.weight)))
        }
        add(TotalRow("Measured", dateOf(latest.startedAt)))
    }
    return ActivityTotal(
        type = EventType.GROWTH,
        headline = headline,
        unit = unit,
        rows = rows,
        perChild = children.map { child ->
            val grams = measured.filter { it.childId == child.id && it.weightGrams != null }
                .maxByOrNull { it.startedAt }?.weightGrams
            TotalRow(child.name, grams?.let { formatWeight(it.toDouble(), units.weight) } ?: DASH)
        },
    )
}

private fun healthTotal(events: List<BabyEvent>, children: List<Child>, zone: ZoneId, units: UnitPreferences): ActivityTotal {
    val ordered = events.sortedByDescending { it.startedAt }
    val latestReading = ordered.firstOrNull { it.temperatureC != null }
    val latestMedication = ordered.firstOrNull { it.medication != null }
    val latestDose = ordered.firstOrNull { it.doseAmount != null && it.doseUnit != null }
    val latestVaccine = ordered.firstOrNull { it.vaccineName != null }
    val rows = buildList {
        add(TotalRow("Records", events.size.toString()))
        // The latest of EACH KIND, not the newest record: a vaccine logged this morning must not vanish
        // from the totals because a temperature was logged at noon. Same rule as the card's columns.
        // Medication and dose are separate rows on purpose - "which medicines" and "how much of this one"
        // are different questions, and they need not come from the same record.
        latestMedication?.let { add(TotalRow("Medication", it.medication!!)) }
        latestDose?.let { add(TotalRow("Dose", formatDose(it.doseAmount, it.doseUnit)!!)) }
        latestVaccine?.let { add(TotalRow("Vaccine", it.vaccineName!!)) }
        latestReading?.let {
            add(TotalRow("Measured", Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().format(measuredOn)))
        }
    }
    return ActivityTotal(
        type = EventType.HEALTH,
        headline = latestReading?.temperatureC?.let { formatTemperature(it, units.temperature).removeSuffix(" ${units.temperature.label}") } ?: DASH,
        unit = if (latestReading != null) units.temperature.label else null,
        rows = rows,
        perChild = children.map { child ->
            TotalRow(child.name, events.count { it.childId == child.id }.toString())
        },
        // A range holding only a medication or a vaccine is NOT empty - it holds one record, and saying
        // "nothing logged in this range" beside a count of 1 is a contradiction a parent would not trust.
        empty = events.isEmpty(),
        temperatureBand = temperatureBand(latestReading?.temperatureC)?.takeIf {
            it != TemperatureBand.NORMAL
        },
    )
}

// ---- helpers --------------------------------------------------------------

private const val DASH = "\u2014"

/** The big value split into number and unit, exactly as the activity cards do it. */
private fun volumeHeadline(millilitres: Int, unit: VolumeUnit): Pair<String, String?> = when (unit) {
    VolumeUnit.MILLILITRES -> millilitres.toString() to "ml"
    VolumeUnit.FLUID_OUNCES -> formatVolume(millilitres.toDouble(), unit).removeSuffix(" oz") to "oz"
}

private fun weightHeadline(grams: Int, unit: WeightUnit): Pair<String, String?> = when (unit) {
    WeightUnit.KILOGRAMS -> formatWeight(grams.toDouble(), unit).removeSuffix(" kg") to "kg"
    WeightUnit.POUNDS_OUNCES -> poundsOunces(grams.toDouble()).let { (lb, oz) -> lb.toString() to "lb $oz oz" }
}

private fun lengthHeadline(millimetres: Int, unit: LengthUnit): Pair<String, String?> {
    val text = formatLength(millimetres.toDouble(), unit)
    return text.substringBeforeLast(" ") to text.substringAfterLast(" ")
}

/** The mean gap between consecutive feeds, or a dash when there are fewer than two. */
private fun averageGap(events: List<BabyEvent>): String {
    if (events.size < 2) return DASH
    val times = events.map { it.startedAt }.sorted()
    val gaps = times.zipWithNext { earlier, later -> later - earlier }
    return elapsed(gaps.sum() / gaps.size)
}
