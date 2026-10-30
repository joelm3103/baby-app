package app.babylog.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
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

/**
 * The window a chart covers.
 *
 * Five chips now, because the log is old enough to ask a year-long question: `1W`, `2W` and `1M` still
 * draw one point per local day, `1Y` draws 52 weekly buckets and `All` draws one bucket per calendar month
 * starting at the **first event** — not at epoch, which would invent months of zeros before birth and then
 * average them into every headline number.
 *
 * Different windows from [app.babylog.domain.HistoryRange] on purpose — a chart of today is one point — so the
 * labels differ too: `1W · 2W · 1M · 1Y · All` here against `D · 1W · 2W` on Summary. `1M` is the 30-day
 * window this chip actually draws; it is not a calendar month, and the label must not claim one.
 *
 * Because a bucket is no longer always a day, every caption that counts them is derived from [bucket]
 * rather than written as "days": a chart of 52 weeks that says "7 of 30 days logged" is a lie about its
 * own axis.
 */
enum class TrendRange(
    val bucket: TrendBucket,
    /** Days covered, or null for [ALL], whose width is however old the log is. */
    val days: Int?,
    val label: String,
    val spoken: String,
    /** The window in words, for captions: `the last 7 days`, `all time`. */
    val windowText: String,
) {
    WEEK(TrendBucket.DAY, 7, "1W", "1 week", "the last 7 days"),
    FORTNIGHT(TrendBucket.DAY, 14, "2W", "2 weeks", "the last 14 days"),
    MONTH(TrendBucket.DAY, 30, "1M", "1 month", "the last 30 days"),
    YEAR(TrendBucket.WEEK, 364, "1Y", "1 year", "the last 52 weeks"),
    ALL(TrendBucket.MONTH, null, "All", "all time", "all time"),
}

/** How wide one point on a chart is. The noun is used verbatim in captions, so it has to be right. */
enum class TrendBucket(val noun: String, val plural: String) {
    DAY("day", "days"),
    WEEK("week", "weeks"),
    MONTH("month", "months"),
    ;

    /** `Days`, for a caption that begins with the noun. */
    val pluralCapitalised: String get() = plural.replaceFirstChar { it.uppercase() }
}

/** What a day's number measures. The unit comes from the user's preferences at the display edge. */
enum class TrendMetric {
    VOLUME,
    COUNT,
    DURATION,
}

/**
 * One bucket of one child's series.
 *
 * [recorded] is separate from [value] on purpose: a logged bottle with no amount is a record worth
 * counting and a volume of zero, and collapsing the two would report a bucket as untouched when the parent
 * had in fact logged something.
 *
 * [bucketStart] is the local midnight of the day, the Monday of the week, or the first of the month, depending
 * on [TrendSeries.range].
 */
data class TrendPoint(
    val bucketStart: Long,
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

    /** Buckets with at least one record, which is what "was anything logged" means. */
    val bucketsRecorded: Int get() = points.count { it.recorded }

    /** Averaged over EVERY bucket in the window, so an empty one pulls the average down honestly. */
    val averagePerBucket: Double get() = if (points.isEmpty()) 0.0 else total / points.size

    /** The biggest recorded bucket, or null when nothing was logged. */
    val bestBucket: TrendPoint? get() = points.filter { it.recorded }.maxByOrNull { it.value }
}

/** A whole pane's worth of series: both children when Both is selected, one otherwise. */
data class TrendSeries(
    val type: EventType,
    val range: TrendRange,
    val metric: TrendMetric,
    val lines: List<TrendLine>,
) {
    val hasData: Boolean get() = lines.any { it.bucketsRecorded > 0 }

    /** The bucket starts, oldest first. Empty only when there are no children in scope. */
    val buckets: List<Long> get() = lines.firstOrNull()?.points?.map { it.bucketStart } ?: emptyList()

    /** How many points the chart draws, which is what the captions count. */
    val bucketCount: Int get() = buckets.size

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
    firstEventAt: Long? = null,
): TrendSeries {
    val metric = metricFor(type)
    val bucketStarts = bucketStarts(range, now, zone, firstEventAt)
    val indexOfBucket = bucketStarts.withIndex().associate { (index, start) -> start to index }

    val lines = children.map { child ->
        val buckets = DoubleArray(bucketStarts.size)
        val recorded = BooleanArray(bucketStarts.size)
        events.asSequence()
            // Tombstones are the DAO's job, but dropping them here too is cheap insurance: a deleted
            // event reaching a chart would show a bucket the parent had already corrected.
            .filterNot { it.deleted }
            .filter { it.childId == child.id && it.type == type }
            .forEach { event ->
                val bucketStart = bucketStartOf(event.startedAt, range.bucket, zone)
                val index = indexOfBucket[bucketStart] ?: return@forEach
                buckets[index] += valueOf(event, metric)
                recorded[index] = true
            }
        TrendLine(
            childId = child.id,
            childName = child.name,
            points = bucketStarts.mapIndexed { index, start ->
                TrendPoint(bucketStart = start, value = buckets[index], recorded = recorded[index])
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

/**
 * The bucket starts a range draws, oldest first.
 *
 * - per-day ranges: the last N local midnights, ending today;
 * - [TrendRange.YEAR]: the last 52 Mondays, ending with the current week's Monday, because a week is a
 *   calendar week here and Monday is what the log's own `dayOfWeek` arithmetic already uses;
 * - [TrendRange.ALL]: every month from the month of the FIRST EVENT to the current month. Falling back to
 *   today's month when the log is empty is deliberate: a window from 1970 would draw decades of zeros.
 */
internal fun bucketStarts(
    range: TrendRange,
    now: Long,
    zone: ZoneId,
    firstEventAt: Long? = null,
): List<Long> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (range.bucket) {
        TrendBucket.DAY -> dayStarts(now, zone, range.days ?: 1)
        TrendBucket.WEEK -> {
            val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (WEEKS_IN_YEAR - 1 downTo 0).map { back ->
                thisWeek.minusWeeks(back.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
        TrendBucket.MONTH -> {
            val firstDate = firstEventAt
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: today
            val last = YearMonth.from(today)
            // A FIRST event later than today -- a mistyped year on a sheet, or a phone whose clock came back
            // from the future -- must not produce an empty bucket list: `trendWindow` takes its start from
            // the first bucket, so an empty list used to throw instead of drawing. Clamping to the current
            // month keeps the invariant "a range always has at least one bucket" true for every range.
            var month = minOf(YearMonth.from(firstDate), last)
            buildList {
                while (!month.isAfter(last)) {
                    add(month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli())
                    month = month.plusMonths(1)
                }
            }
        }
    }
}

/** The local start of the bucket [at] falls in: midnight, that week's Monday, or the first of the month. */
internal fun bucketStartOf(at: Long, bucket: TrendBucket, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    return when (bucket) {
        TrendBucket.DAY -> date.atStartOfDay(zone).toInstant().toEpochMilli()
        TrendBucket.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()
        TrendBucket.MONTH -> date.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/**
 * The absolute window a range asks for, as `[from, to)`.
 *
 * [TrendRange.ALL] takes its start from the first bucket rather than from `days`, so asking for "all time"
 * over a log that begins in December cannot include the eleven months of nothing before it.
 */
fun trendWindow(range: TrendRange, now: Long, zone: ZoneId, firstEventAt: Long? = null): TimeRange {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val days = range.days
    val from = if (days != null) {
        today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    } else {
        bucketStarts(range, now, zone, firstEventAt).first()
    }
    return TimeRange(from = from, to = to)
}

private const val WEEKS_IN_YEAR = 52

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
        feverDays = fever.map { bucketStartOf(it.at, TrendBucket.DAY, zone) }.distinct().size,
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
fun trendAxisUnit(
    metric: TrendMetric,
    units: UnitPreferences,
    bucket: TrendBucket = TrendBucket.DAY,
): String = when (metric) {
    TrendMetric.VOLUME ->
        if (units.volume == VolumeUnit.MILLILITRES) "ml per ${bucket.noun}" else "oz per ${bucket.noun}"
    TrendMetric.DURATION -> "hours per ${bucket.noun}"
    TrendMetric.COUNT -> "per ${bucket.noun}"
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
fun trendHeadline(metric: TrendMetric, average: Double, units: UnitPreferences): String {
    val display = trendDisplayValue(metric, average, units)
    val text = formatTrendNumber(display)
    // Found on the phone: 13 nappies across 30 days read as "0 per day" while the total read 13. A
    // non-zero average that rounds away says so instead of claiming nothing happened.
    return if (text == "0" && display > 0.0) "< 0.1" else text
}

/**
 * The unit that sits small beside [trendHeadline].
 *
 * It names the BUCKET, not the day: the average over 52 weekly buckets is per week, and a caption that
 * said "per day" above it would be describing a different chart. Defaults to a day, which is what every
 * per-day range draws.
 */
fun trendHeadlineUnit(
    metric: TrendMetric,
    units: UnitPreferences,
    bucket: TrendBucket = TrendBucket.DAY,
): String = when (metric) {
    TrendMetric.VOLUME ->
        if (units.volume == VolumeUnit.MILLILITRES) "ml / ${bucket.noun}" else "oz / ${bucket.noun}"
    TrendMetric.DURATION -> "hours / ${bucket.noun}"
    TrendMetric.COUNT -> "per ${bucket.noun}"
}

/** A total over the window, in display units and with its unit: "1,240 ml", "16h 20m", "115". */
fun formatTrendTotal(metric: TrendMetric, total: Double, units: UnitPreferences): String = when (metric) {
    TrendMetric.VOLUME -> formatVolume(total, units.volume)
    // Minutes are the stored scale; elapsed() speaks milliseconds, so convert exactly once here.
    TrendMetric.DURATION -> elapsed((total * 60_000.0).toLong())
    TrendMetric.COUNT -> total.roundToInt().toString()
}
