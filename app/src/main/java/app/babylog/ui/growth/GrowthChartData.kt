package app.babylog.ui.growth

import app.babylog.data.growth.GrowthMeasure
import app.babylog.data.growth.GrowthReference
import app.babylog.domain.BabyEvent
import app.babylog.domain.Child
import app.babylog.domain.badgesFor
import app.babylog.domain.GRAMS_PER_POUND
import app.babylog.domain.LengthUnit
import app.babylog.domain.MM_PER_INCH
import app.babylog.domain.PERCENTILE_CURVES
import app.babylog.domain.Sex
import app.babylog.domain.UnitPreferences
import app.babylog.domain.WeightUnit
import app.babylog.domain.ageDays
import app.babylog.domain.ageDaysForCharts
import app.babylog.domain.ageText
import app.babylog.domain.percentile
import app.babylog.domain.valueAtZ
import app.babylog.domain.zForCurve
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the Growth screen draws, computed in plain Kotlin.
 *
 * The chart is a view of the same numbers the callout prints, so both come from here rather than each
 * being derived on the screen: a marker that sat on one curve while its label read another percentile
 * would be worse than showing nothing.
 *
 * Values in this file are in DISPLAY units (kg or lb, cm or in) because that is what the axes show. The
 * percentile is always computed from the canonical metric value, per the design contract: the unit
 * preference changes the axis, never the maths.
 */

/** Which measurement the chart is showing. */
enum class GrowthMetric(val measure: GrowthMeasure, val label: String, val code: String) {
    WEIGHT(GrowthMeasure.WEIGHT_FOR_AGE, "Weight", "W"),
    LENGTH(GrowthMeasure.LENGTH_HEIGHT_FOR_AGE, "Length", "L"),
    HEAD(GrowthMeasure.HEAD_CIRCUMFERENCE_FOR_AGE, "Head", "H"),
}

/** The unit the chart's y-axis is drawn in, and how to print a tick. */
enum class ChartUnit(val label: String) {
    KILOGRAMS("kg"),
    POUNDS("lb"),
    CENTIMETRES("cm"),
    INCHES("in");

    /** One decimal: the axis is a scale, not a measurement, and two would crowd the labels. */
    fun format(value: Double): String = String.format(Locale.ENGLISH, "%.1f", value)
}

/** A point on a curve, in display units against age in days. */
data class CurvePoint(val axis: Double, val value: Double)

/** One percentile curve of the bundle. */
data class PercentileCurve(val percentile: Int, val points: List<CurvePoint>)

/**
 * A child's measurement.
 *
 * [percentile] is null when the reference does not cover that age — a birth measurement at the premature range
 * has no WHO percentile, because the WHO 0–5 standards describe term babies. Null is rendered as a
 * value with a reason, never as a percentile.
 */
data class GrowthMarker(
    val childId: String,
    val childName: String,
    val badge: String,
    val value: Double,
    val axis: Double,
    val percentile: Double?,
    val measuredOn: LocalDate,
    val ageLabel: String,
    val wasAdjusted: Boolean,
    /** Taken before term, so [axis] is the DUE DATE (adjusted day 0), not a day since birth. */
    val preTerm: Boolean = false,
    /** Why there is no percentile, when there is not one. Null means the percentile is the answer. */
    val noPercentileReason: String? = null,
)

/** One point of a child's own line, in display units. */
data class TrajectoryPoint(val axis: Double, val value: Double, val preTerm: Boolean)

/**
 * One child's own growth, OLDEST measurement first so the line reads left to right.
 *
 * [GrowthChartData.markers] is newest-first because that is how a list is read; a trajectory is the other
 * order, so it is built and kept separately rather than re-sorting markers at the drawing site.
 */
data class GrowthTrajectory(
    val childId: String,
    val badge: String,
    val points: List<TrajectoryPoint>,
) {
    /** One measurement is a dot, not a line. */
    val drawable: Boolean get() = points.size >= 2
}

/**
 * A segment is dashed when either end was taken before term.
 *
 * The line is unbroken on purpose — one story per child, which is what a parent reads — but the segment
 * that crosses the pre-term boundary says so, because the birth point sits at the DUE DATE and the slope
 * across that join is not growth that happened.
 */
internal fun segmentIsDashed(from: TrajectoryPoint, to: TrajectoryPoint): Boolean =
    from.preTerm || to.preTerm

/** Everything the Growth screen renders for one metric and one child selection. */
data class GrowthChartData(
    val metric: GrowthMetric,
    val unit: ChartUnit,
    val curves: List<PercentileCurve>,
    val markers: List<GrowthMarker>,
    /** Each child's own line, oldest first. Empty for a child with a single measurement. */
    val trajectories: List<GrowthTrajectory> = emptyList(),
    /**
     * The highlight box(es): one per child in scope, each that child's newest measurement for this metric.
     *
     * One per child rather than the single newest overall, because in Both mode "newest overall" is a
     * winner picked by name order when both twins were measured the same day — a box that silently showed
     * one child's number was the same defect as the card's combined headline. A child with no measurement
     * for this metric simply has no box.
     */
    val callouts: List<GrowthMarker> = emptyList(),
    val identityLine: String,
    /** e.g. "WHO boys" — the curve bundle is one sex's, and that must be visible. */
    val referenceLabel: String,
    val xMaxDays: Double,
) {
    val hasData: Boolean get() = markers.isNotEmpty()
}

/** The one sentence a preterm birth measurement deserves, rather than a percentile it cannot have. */
const val PRETERM_NOTE: String =
    "Measured before term: the WHO 0-5 standards begin at birth at 40 weeks, so a baby born at the premature range " +
        "has no percentile on them yet. Later measurements use adjusted age."

/** The other honest answer, for a child past the end of the tables. */
const val OUT_OF_RANGE_NOTE: String =
    "Past the end of the WHO 0-5 standards, so there is no percentile to read."

/** How far the x-axis runs, in days: two years, or the child's own age plus a month once they pass it. */
internal fun xAxisEndDays(oldestAgeDays: Double): Double =
    minOf(1856.0, maxOf(730.0, oldestAgeDays + 30.0))

/**
 * Build the chart for [children] and [events].
 *
 * `children` is the selection in scope: one child, or both in Both mode, in which case both markers are
 * drawn on one bundle of curves. Twins of the same sex read correctly; if the two ever differ in sex the
 * bundle is the first child's and [GrowthChartData.referenceLabel] says whose it is, because silently
 * plotting a girl against boys' curves would be wrong in a way nothing on screen would reveal.
 */
fun growthChart(
    children: List<Child>,
    events: List<BabyEvent>,
    reference: GrowthReference,
    metric: GrowthMetric,
    units: UnitPreferences,
    zone: ZoneId,
): GrowthChartData {
    val unit = unitFor(metric, units)
    val curveSex = children.firstOrNull()?.sex ?: Sex.BOY
    // One badge per child, numbered when two names share a first letter, so the marker text and the
    // legend cannot disagree about which child is which.
    val badges = badgesFor(children)

    val markers = children
        .flatMap { child -> markersFor(child, badges.getValue(child.id), events, reference, metric, unit, zone) }
        .sortedWith(compareByDescending<GrowthMarker> { it.measuredOn }.thenBy { it.childName })

    val xMax = xAxisEndDays(markers.maxOfOrNull { it.axis } ?: 0.0)
    val curves = PERCENTILE_CURVES.mapNotNull { percentileOf ->
        zForCurve(percentileOf)?.let { z ->
            PercentileCurve(
                percentile = percentileOf,
                points = curvePoints(reference, metric, curveSex, z, xMax)
                    .map { (day, metricValue) -> CurvePoint(day, toDisplay(metricValue, metric, unit)) },
            )
        }
    }

    val callouts = children
        .mapNotNull { child -> markers.firstOrNull { it.childId == child.id } }
        .sortedBy { it.measuredOn }

    // Each child's own line, oldest first. Points the reference cannot place are still plotted and still
    // joined; they are marked pre-term so the join into them is drawn dashed.
    val trajectories = children.mapNotNull { child ->
        val points = markers
            .filter { it.childId == child.id }
            .sortedBy { it.measuredOn }
            .map { TrajectoryPoint(axis = it.axis, value = it.value, preTerm = it.preTerm) }
        GrowthTrajectory(
            childId = child.id,
            badge = badges.getValue(child.id),
            points = points,
        ).takeIf { it.drawable }
    }

    return GrowthChartData(
        metric = metric,
        unit = unit,
        curves = curves,
        markers = markers,
        trajectories = trajectories,
        callouts = callouts,
        identityLine = reference.identityLine,
        referenceLabel = "WHO ${if (curveSex == Sex.BOY) "boys" else "girls"}",
        xMaxDays = xMax,
    )
}

private fun markersFor(
    child: Child,
    badge: String,
    events: List<BabyEvent>,
    reference: GrowthReference,
    metric: GrowthMetric,
    unit: ChartUnit,
    zone: ZoneId,
): List<GrowthMarker> = events
    .filter { it.childId == child.id }
    .mapNotNull { event ->
        val metricValue = metricValueOf(event, metric) ?: return@mapNotNull null
        val measuredOn = Instant.ofEpochMilli(event.startedAt).atZone(zone).toLocalDate()
        // Adjusted age is the whole reason this is a named function rather than a subtraction.
        val day = ageDaysForCharts(child, measuredOn).toDouble()
        // A measurement taken before the child's due date is not described by the WHO standards at all:
        // they begin at birth at term. Plotting a 30-week baby's 1.37 kg at "day 0" would place them far
        // below the 2nd percentile and read as a growth failure rather than as immaturity.
        val chronologicalDays = ageDays(child.birthDate, measuredOn)
        val beforeTerm = child.prematureDays > 0 && chronologicalDays < child.prematureDays
        val lms = reference.lms(metric.measure, child.sex, day)
        val percentileValue = when {
            beforeTerm -> null
            lms == null -> null
            else -> percentile(metricValue, lms.l, lms.m, lms.s)
        }
        val reason = when {
            beforeTerm -> PRETERM_NOTE
            lms == null -> OUT_OF_RANGE_NOTE
            percentileValue == null -> OUT_OF_RANGE_NOTE
            else -> null
        }
        GrowthMarker(
            childId = child.id,
            childName = child.name,
            badge = badge,
            value = toDisplay(metricValue, metric, unit),
            axis = day,
            percentile = percentileValue,
            measuredOn = measuredOn,
            // The app's own age formatter, so the marker's age and the switcher's cannot disagree
            // about whether this child's age is adjusted.
            ageLabel = ageText(child = child, on = measuredOn),
            wasAdjusted = child.useAdjustedAge && child.prematureDays > 0,
            preTerm = beforeTerm,
            noPercentileReason = reason,
        )
    }

/** The metric value of an event in canonical units (kg, cm), or null if it did not record one. */
internal fun metricValueOf(event: BabyEvent, metric: GrowthMetric): Double? = when (metric) {
    GrowthMetric.WEIGHT -> event.weightGrams?.let { it / 1000.0 }
    GrowthMetric.LENGTH -> event.lengthMm?.let { it / 10.0 }
    GrowthMetric.HEAD -> event.headMm?.let { it / 10.0 }
}

/** Curves are drawn at weekly resolution; the tables are daily, so a curve is a lookup, not a fit. */
private const val CURVE_STEP_DAYS = 7.0

private fun curvePoints(
    reference: GrowthReference,
    metric: GrowthMetric,
    sex: Sex,
    z: Double,
    xMaxDays: Double,
): List<Pair<Double, Double>> {
    val out = mutableListOf<Pair<Double, Double>>()
    var day = 0.0
    while (day <= xMaxDays) {
        reference.lms(metric.measure, sex, day)?.let { lms ->
            valueAtZ(z, lms.l, lms.m, lms.s)?.let { out.add(day to it) }
        }
        day += CURVE_STEP_DAYS
    }
    return out
}

private fun unitFor(metric: GrowthMetric, units: UnitPreferences): ChartUnit = when (metric) {
    GrowthMetric.WEIGHT -> if (units.weight == WeightUnit.KILOGRAMS) ChartUnit.KILOGRAMS else ChartUnit.POUNDS
    GrowthMetric.LENGTH, GrowthMetric.HEAD ->
        if (units.length == LengthUnit.CENTIMETRES) ChartUnit.CENTIMETRES else ChartUnit.INCHES
}

/** Display-unit conversion for the axes. Only the two factors the app already stores are needed. */
private fun toDisplay(metricValue: Double, metric: GrowthMetric, unit: ChartUnit): Double = when {
    metric == GrowthMetric.WEIGHT && unit == ChartUnit.POUNDS -> metricValue * 1000.0 / GRAMS_PER_POUND
    metric != GrowthMetric.WEIGHT && unit == ChartUnit.INCHES -> metricValue * 10.0 / MM_PER_INCH
    else -> metricValue
}

/** `12 Mar 2026`, for a measurement row. */
fun measuredOnLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

/** The x-axis tick: age in months, because that is how growth is discussed. */
fun monthLabel(days: Double): String {
    val months = (days / 30.4375).roundToInt()
    return if (months == 0) "birth" else "$months"
}
