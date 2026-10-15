package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType
import app.babylog.domain.UnitPreferences
import app.babylog.domain.elapsed
import app.babylog.domain.formatLength
import app.babylog.domain.formatTemperature
import app.babylog.domain.formatVolume
import app.babylog.domain.formatWeight
import app.babylog.domain.poundsOunces
import app.babylog.domain.WeightUnit

/**
 * The two strings every card needs: the big number and its unit, and the one-line detail under it.
 * Pure functions of (event, unit preference) so they can be tested without rendering anything — which
 * matters because "what does the big value show" is the app's central design decision, not a styling
 * afterthought.
 */

/** Returns null when there is no value to shout about (sleep shows a duration or a timer instead). */
fun bigValue(event: BabyEvent, units: UnitPreferences): Pair<String, String>? = when (event.type) {
    EventType.BOTTLE -> event.amountMl?.let { ml ->
        when (units.volume) {
            app.babylog.domain.VolumeUnit.MILLILITRES -> ml.toString() to "ml"
            app.babylog.domain.VolumeUnit.FLUID_OUNCES ->
                formatVolume(ml.toDouble(), units.volume).removeSuffix(" oz") to "oz"
        }
    }
    EventType.DIAPER -> null
    EventType.SOLIDS -> null
    EventType.SLEEP -> event.durationMs?.let { elapsed(it) to "" }
    EventType.GROWTH -> event.weightGrams?.let { g ->
        when (units.weight) {
            WeightUnit.KILOGRAMS -> formatWeight(g.toDouble(), units.weight).removeSuffix(" kg") to "kg"
            WeightUnit.POUNDS_OUNCES -> poundsOunces(g.toDouble()).let { (lb, oz) -> lb.toString() to "lb $oz oz" }
        }
    } ?: event.lengthMm?.let { mm ->
        formatLength(mm.toDouble(), units.length).let { v -> v.substringBeforeLast(" ") to v.substringAfterLast(" ") }
    } ?: event.headMm?.let { mm ->
        formatLength(mm.toDouble(), units.length).let { v -> v.substringBeforeLast(" ") to v.substringAfterLast(" ") }
    }
    EventType.HEALTH -> event.temperatureC?.let { c ->
        formatTemperature(c).removeSuffix(" \u00b0C") to "\u00b0C"
    }
}

/** The line under the big value: what actually happened. */
fun detailLine(event: BabyEvent, units: UnitPreferences): String = when (event.type) {
    EventType.BOTTLE -> event.formulaBrand ?: "Bottle"
    EventType.DIAPER -> buildString {
        append(event.diaperKind?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Diaper")
        if (event.rash == true) append(" \u00b7 rash")
    }
    EventType.SOLIDS -> event.foodIds.takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { it.replace('-', ' ') }
        ?: (event.mealSlot?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Solids")
    EventType.SLEEP -> if (event.isRunning) "Asleep now" else "Slept ${elapsed(event.durationMs ?: 0)}"
    EventType.GROWTH -> growthValues(event, units)
    EventType.HEALTH -> listOfNotNull(event.medication, event.vaccineName).joinToString(" \u00b7 ")
        .ifBlank { "Health" }
}

/**
 * The value(s) an entry recorded, each labelled: `W 6 kg \u00b7 L 66 cm \u00b7 H 42 cm`.
 *
 * The codes are not decoration. Unlabelled, "66 cm" and "42 cm" are indistinguishable — either could be
 * length or head circumference — and on a card that shows several measurements at once that is a reading
 * error waiting to happen. Order is the sheet's: weight, length, head.
 */
internal fun growthValues(event: BabyEvent, units: UnitPreferences): String = listOfNotNull(
    event.weightGrams?.let { "W " + formatWeight(it.toDouble(), units.weight) },
    event.lengthMm?.let { "L " + formatLength(it.toDouble(), units.length) },
    event.headMm?.let { "H " + formatLength(it.toDouble(), units.length) },
).joinToString(" \u00b7 ")

/**
 * The newest value recorded for EACH growth measurement, which need not come from the same entry.
 *
 * A growth entry carries up to three fields, and a parent rarely measures all three every time: weight on
 * one day, length and head circumference a week later. Reading only the newest entry therefore hides real
 * measurements. Given weight on three days (10, 12, 20 kg), length on two (50, 55 cm) and a head
 * circumference on one (25 cm), the current answer is 20 kg, 55 cm and 25 cm — each the latest of its own
 * kind — not the newest entry's fields, which would show 20 kg alone.
 */
data class GrowthLatest(val weight: BabyEvent?, val length: BabyEvent?, val head: BabyEvent?) {
    /** The big number: weight, because that is what parents track; otherwise the first taken. */
    val headline: BabyEvent? get() = weight ?: length ?: head

    val isEmpty: Boolean get() = headline == null
}

/**
 * Picks the newest entry that recorded each measurement. Expects the card's newest-first list but sorts
 * defensively, so a caller cannot change the answer by reordering.
 */
fun growthLatest(entries: List<BabyEvent>): GrowthLatest {
    val growth = entries
        .filter { it.type == EventType.GROWTH }
        .sortedByDescending { it.startedAt }
    return GrowthLatest(
        weight = growth.firstOrNull { it.weightGrams != null },
        length = growth.firstOrNull { it.lengthMm != null },
        head = growth.firstOrNull { it.headMm != null },
    )
}

/** The Growth card's big value, from [GrowthLatest] rather than from the newest entry. */
fun growthBigValue(latest: GrowthLatest, units: UnitPreferences): Pair<String, String>? =
    latest.headline?.let { bigValue(it, units) }

/**
 * All three latest measurements for ONE child, in the sheet's order, with the ones never recorded simply
 * absent — no placeholder for a measurement that was not taken.
 */
fun growthDetail(latest: GrowthLatest, units: UnitPreferences): String = listOfNotNull(
    latest.weight?.weightGrams?.let { "W " + formatWeight(it.toDouble(), units.weight) },
    latest.length?.lengthMm?.let { "L " + formatLength(it.toDouble(), units.length) },
    latest.head?.headMm?.let { "H " + formatLength(it.toDouble(), units.length) },
).joinToString(" \u00b7 ")

/**
 * One child's latest measurements, for the two-column summary the card shows in Both mode.
 *
 * The columns exist because a combined headline has no single owner: "6 kg" in Both mode is one child's
 * weight dressed up as the twins'. Each column is the child's name followed by ONE MEASUREMENT PER LINE
 * — weight, then length, then head — because a single line holding all three wrapped into an unreadable
 * block at half the card's width (the owner's report: "looks too cramped").
 */
data class ChildGrowth(val childId: String, val lines: List<String>, val hasData: Boolean)

fun growthByChild(
    entries: List<BabyEvent>,
    childIds: List<String>,
    units: UnitPreferences,
): List<ChildGrowth> = childIds.map { childId ->
    val latest = growthLatest(entries.filter { it.childId == childId })
    ChildGrowth(
        childId = childId,
        lines = listOfNotNull(
            latest.weight?.weightGrams?.let { "W " + formatWeight(it.toDouble(), units.weight) },
            latest.length?.lengthMm?.let { "L " + formatLength(it.toDouble(), units.length) },
            latest.head?.headMm?.let { "H " + formatLength(it.toDouble(), units.length) },
        ),
        hasData = !latest.isEmpty,
    )
}

/**
 * The value shown in a card's RECENT ROW — a different question from [detailLine]'s.
 *
 * [detailLine] answers "what was this?" under the big value, where a formula brand is genuinely
 * useful. A row has to answer "which one, and how much?", and for a bottle that means the VOLUME: the
 * brand is identical on every row and distinguishes nothing, which is why Both mode used to render two
 * identical "Bottle" rows. The child comes from the badge beside this text.
 */
fun recentRowLabel(event: BabyEvent, units: UnitPreferences): String = when (event.type) {
    EventType.BOTTLE -> event.amountMl?.let { formatVolume(it.toDouble(), units.volume) } ?: "Bottle"
    else -> detailLine(event, units)
}
