package app.babylog.domain

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Trends: one number per local day, per child, for the activities that have a quantity worth drawing.
 *
 * Pure Kotlin for the same reason [totalsFor] is: a day boundary depends on the local timezone, and
 * SQLite cannot resolve one. Bucketing an 8pm feed by UTC would move it to tomorrow and the chart would
 * be confidently wrong, which is worse than having no chart.
 *
 * A day with NO records is zero, not a gap. That matches History, where a day with no diapers counts as
 * zero diapers, and it is the only reading the app can defend: it cannot tell "no nappies" apart from
 * "nobody logged one", so it must not draw a hole and imply it knows the difference. Every pane says so
 * in words under the chart.
 *
 * Growth is deliberately absent: a percentile chart is plotted against AGE, not against the last N
 * days, so it keeps its own pane (`ui/growth`). Health is absent too — a temperature is a reading at a
 * point in time, not a per-day quantity, so it gets a dated list instead of a chart; see [healthTrend].
 */

/** The window a chart covers. Three chips, because a fourth would need a scroll and none of the three is a guess. */
enum class TrendRange(val days: Int, val label: String) {
    WEEK(7, "7d"),
    FORTNIGHT(14, "14d"),
    MONTH(30, "30d"),
}

/** What a day's number measures. The unit comes from the user's preferences at the display edge. */
enum class TrendMetric {
    VOLUME,
    COUNT,
    DURATION,
}

/**
 * One day of one child's series.
 *
 * [recorded] is separate from [value] on purpose: a logged bottle with no amount is a record worth
 * counting and a volume of zero, and collapsing the two would report a day as untouched when the parent
 * had in fact logged something.
 */
data class TrendPoint(
    val dayStart: Long,
    val value: Double,
    val recorded: Boolean,
)

/** One child's series. The name is carried so the legend and the summary never need a second lookup. */
data class TrendLine(
    val childId: String,
    val childName: String,
    val points: List<TrendPoint>,
) {
    val total: Double get() = points.sumOf { it.value }

    /** Days with at least one record, which is what "was anything logged" means. */
    val daysRecorded: Int get() = points.count { it.recorded }

    /** Averaged over EVERY day in the window, so a day off the log pulls the average down honestly. */
    val averagePerDay: Double get() = if (points.isEmpty()) 0.0 else total / points.size

    /** The biggest recorded day, or null when nothing was logged. */
    val bestDay: TrendPoint? get() = points.filter { it.recorded }.maxByOrNull { it.value }
}

/** A whole pane's worth of series: both children when Both is selected, one otherwise. */
data class TrendSeries(
    val type: EventType,
    val range: TrendRange,
    val metric: TrendMetric,
    val lines: List<TrendLine>,
) {
    val hasData: Boolean get() = lines.any { it.daysRecorded > 0 }

    /** The day starts, oldest first. Empty only when there are no children in scope. */
    val days: List<Long> get() = lines.firstOrNull()?.points?.map { it.dayStart } ?: emptyList()

    /** True when more than one child is charted, which is when the legend has to name them. */
    val multipleChildren: Boolean get() = lines.size > 1
}

/**
 * Buckets [events] into `[range.days]` local calendar days ending with today, one line per child.
 *
 * [events] must already be scoped to the children in view and to a window covering the range; the range
 * is re-derived here so the buckets and the axis cannot disagree. Deleted rows are expected to have
 * been filtered by the DAO.
 *
 * Growth and Health have no daily quantity, so they fall through to a count and must not be charted —
 * the UI never passes them.
 */
fun trendSeries(
    type: EventType,
    children: List<Child>,
    events: List<BabyEvent>,
    range: TrendRange,
    now: Long,
    zone: ZoneId,
    units: UnitPreferences,
): TrendSeries {
    val metric = metricFor(type)
    val dayStarts = dayStarts(now, zone, range.days)
    val indexOfDay = dayStarts.withIndex().associate { (index, start) -> start to index }

    val lines = children.map { child ->
        val buckets = DoubleArray(dayStarts.size)
        val recorded = BooleanArray(dayStarts.size)
        events.asSequence()
            // Tombstones are the DAO's job, but dropping them here too is cheap insurance: a deleted
            // event reaching a chart would show a day the parent had already corrected.
            .filterNot { it.deleted }
            .filter { it.childId == child.id && it.type == type }
            .forEach { event ->
                val dayStart = startOfDay(event.startedAt, zone)
                val index = indexOfDay[dayStart] ?: return@forEach
                buckets[index] += valueOf(event, metric)
                recorded[index] = true
            }
        TrendLine(
            childId = child.id,
            childName = child.name,
            points = dayStarts.mapIndexed { index, start ->
                TrendPoint(dayStart = start, value = buckets[index], recorded = recorded[index])
            },
        )
    }

    return TrendSeries(type = type, range = range, metric = metric, lines = lines)
}

/** Which kinds of activity have a per-day quantity. Everything else is either age-plotted or a reading. */
val CHARTED_TYPES: List<EventType> = listOf(
    EventType.BOTTLE,
    EventType.SOLIDS,
    EventType.DIAPER,
    EventType.SLEEP,
)

fun metricFor(type: EventType): TrendMetric = when (type) {
    EventType.BOTTLE -> TrendMetric.VOLUME
    EventType.SLEEP -> TrendMetric.DURATION
    else -> TrendMetric.COUNT
}

/**
 * One event's contribution. Canonical units in, always: millilitres for volume and MINUTES for
 * duration, so the axis maths and the axis labels share one scale and the user's unit choice only
 * touches the label.
 *
 * A running sleep contributes no duration yet. Counting it as "until now" would make the last bar grow
 * while nobody is looking, which is the same reason History's total refuses to guess.
 */
private fun valueOf(event: BabyEvent, metric: TrendMetric): Double = when (metric) {
    TrendMetric.VOLUME -> event.amountMl?.toDouble() ?: 0.0
    TrendMetric.DURATION -> event.durationMs?.let { it / 60_000.0 } ?: 0.0
    TrendMetric.COUNT -> 1.0
}

/** Midnight for each of the last [days] local days, oldest first, ending with today. */
internal fun dayStarts(now: Long, zone: ZoneId, days: Int): List<Long> {
    require(days >= 1) { "a trend covers at least one day" }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return (days - 1 downTo 0).map { back ->
        today.minusDays(back.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

private fun startOfDay(at: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(at).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

// ---- Health: a table, not a chart -------------------------------------------------------------

/** One flagged reading, as the table names it. */
data class FeverReading(
    val childName: String,
    val childBadge: String,
    val at: Long,
    val temperatureC: Double,
    /** MILD_FEVER or FEVER — the word the row prints beside the reading. */
    val band: TemperatureBand,
)

/**
 * Health over the window: how many readings were taken, and which of them were flagged.
 *
 * A temperature has no meaningful per-day quantity — averaging two readings is not a number a parent
 * should act on — so this is a list plus a count of distinct DAYS. Readings at or above
 * [MILD_FEVER_MIN_C] are listed, each with its band: the whole reason to look back is to see how long a
 * temperature ran, and a list that also held every normal reading would bury that. A LOW reading is
 * labelled on the cards and in Summary but is deliberately NOT listed here — this is the flagged table,
 * and a cold reading is not something to watch over days.
 */
data class HealthTrend(
    val readings: Int,
    val feverReadings: List<FeverReading>,
    val feverDays: Int,
) {
    val hasFever: Boolean get() = feverReadings.isNotEmpty()
}

fun healthTrend(children: List<Child>, events: List<BabyEvent>, zone: ZoneId): HealthTrend {
    val names = children.associate { it.id to it.name }
    val badges = badgesFor(children)
    val measured = events.filterNot { it.deleted }.filter { it.temperatureC != null && it.childId in names }
    val fever = measured.sortedByDescending { it.startedAt }
        .mapNotNull { event ->
            val band = temperatureBand(event.temperatureC)
            if (band != TemperatureBand.MILD_FEVER && band != TemperatureBand.FEVER) return@mapNotNull null
            FeverReading(
                childName = names.getValue(event.childId),
                childBadge = badges.getValue(event.childId),
                at = event.startedAt,
                temperatureC = event.temperatureC!!,
                band = band,
            )
        }
    return HealthTrend(
        readings = measured.size,
        feverReadings = fever,
        // Distinct local days, so three readings in one evening are one day of fever rather than three.
        feverDays = fever.map { startOfDay(it.at, zone) }.distinct().size,
    )
}

// ---- display ----------------------------------------------------------------------------------

/**
 * A canonical value on the user's scale: millilitres to their volume unit, minutes to hours, a count
 * unchanged. The chart's y axis and the summary card both go through this, so they cannot disagree.
 */
fun trendDisplayValue(metric: TrendMetric, canonical: Double, units: UnitPreferences): Double =
    when (metric) {
        TrendMetric.VOLUME -> fromMillilitres(canonical, units.volume)
        TrendMetric.DURATION -> canonical / 60.0
        TrendMetric.COUNT -> canonical
    }

/** What one unit of the axis IS, for the caption under the chart. */
fun trendAxisUnit(metric: TrendMetric, units: UnitPreferences): String = when (metric) {
    TrendMetric.VOLUME -> if (units.volume == VolumeUnit.MILLILITRES) "ml per day" else "oz per day"
    TrendMetric.DURATION -> "hours per day"
    TrendMetric.COUNT -> "per day"
}

/**
 * A bare number for the axis or the big value — no unit, because the caption carries it.
 *
 * One decimal, with the trailing ".0" dropped. A count is rounded to a whole number: an axis tick
 * reading "2.0 diapers" is a category error, not extra precision.
 */
fun formatTrendNumber(value: Double): String = when {
    !value.isFinite() -> "0"
    else -> {
        val rounded = (value * 10).roundToInt() / 10.0
        if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString()
        else String.format(Locale.ENGLISH, "%.1f", rounded)
    }
}

/**
 * A whole day's count is not rounded to a whole number here, and that is deliberate: two solids over
 * thirty days is `0.1` a day, and printing `0` beside a total of 2 is the kind of contradiction a parent
 * stops trusting. See [trendHeadline] for the sub-0.1 case.
 */

/** The big number in a summary card: the average day, in display units. */
fun trendHeadline(metric: TrendMetric, averagePerDay: Double, units: UnitPreferences): String {
    val display = trendDisplayValue(metric, averagePerDay, units)
    val text = formatTrendNumber(display)
    // Found on the phone: 13 nappies across 30 days read as "0 per day" while the total read 13. A
    // non-zero average that rounds away says so instead of claiming nothing happened.
    return if (text == "0" && display > 0.0) "< 0.1" else text
}

/** The unit that sits small beside [trendHeadline]. */
fun trendHeadlineUnit(metric: TrendMetric, units: UnitPreferences): String = when (metric) {
    TrendMetric.VOLUME -> if (units.volume == VolumeUnit.MILLILITRES) "ml / day" else "oz / day"
    TrendMetric.DURATION -> "hours / day"
    TrendMetric.COUNT -> "per day"
}

/** A total over the window, in display units and with its unit: "1,240 ml", "16h 20m", "115". */
fun formatTrendTotal(metric: TrendMetric, total: Double, units: UnitPreferences): String = when (metric) {
    TrendMetric.VOLUME -> formatVolume(total, units.volume)
    // Minutes are the stored scale; elapsed() speaks milliseconds, so convert exactly once here.
    TrendMetric.DURATION -> elapsed((total * 60_000.0).toLong())
    TrendMetric.COUNT -> total.roundToInt().toString()
}
