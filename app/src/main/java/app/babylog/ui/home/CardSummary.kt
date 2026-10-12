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
    EventType.GROWTH -> listOfNotNull(
        event.weightGrams?.let { formatWeight(it.toDouble(), units.weight) },
        event.lengthMm?.let { formatLength(it.toDouble(), units.length) },
        event.headMm?.let { formatLength(it.toDouble(), units.length) },
    ).joinToString(" \u00b7 ")
    EventType.HEALTH -> listOfNotNull(event.medication, event.vaccineName).joinToString(" \u00b7 ")
        .ifBlank { "Health" }
}
