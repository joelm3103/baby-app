package app.babylog.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Units: three families, each independently selectable, plus Imperial/Metric presets.
 *
 * Canonical storage is always metric-precise — millimetres, grams, millilitres, Celsius —
 * and conversion happens only at the display/input edge. That keeps every calculation
 * (percentiles, totals, comparisons) on one unit system and makes setting changes
 * non-destructive: flipping to imperial never rewrites stored values.
 *
 * **Temperature has no imperial option, deliberately.** Celsius only, no setting, no
 * conversion path. See `.hermes/skills/babylog-ui/references/style-guide.md`.
 *
 * Exact factors (all defined, not approximated):
 *  - 1 inch          = 25.4 mm
 *  - 1 pound         = 453.59237 g
 *  - 1 ounce         = 1/16 lb = 28.349523125 g
 *  - 1 US fluid oz   = 29.5735295625 ml   (= 3.785411784 L / 128)
 *
 * NOTE: that is the **US** fluid ounce. The UK fluid ounce is 28.4130625 ml, about 4%
 * different — for a 4 oz bottle that is a visible difference, so if these numbers are
 * ever meant to be imperial-UK, this constant and its tests must change together.
 */
enum class LengthUnit { INCHES, CENTIMETRES }
enum class WeightUnit { POUNDS_OUNCES, KILOGRAMS }
enum class VolumeUnit { FLUID_OUNCES, MILLILITRES }

enum class UnitSystem { IMPERIAL, METRIC, CUSTOM }

private const val MM_PER_INCH = 25.4
private const val GRAMS_PER_POUND = 453.59237
private const val GRAMS_PER_OUNCE = GRAMS_PER_POUND / 16.0 // 28.349523125
private const val ML_PER_US_FL_OUNCE = 29.5735295625

/**
 * The user's unit choice. `system` is DERIVED, never stored — so a custom mix reads as
 * CUSTOM and re-selecting Imperial/Metric is predictable.
 */
data class UnitPreferences(
    val length: LengthUnit = LengthUnit.CENTIMETRES,
    val weight: WeightUnit = WeightUnit.KILOGRAMS,
    val volume: VolumeUnit = VolumeUnit.MILLILITRES,
) {
    val system: UnitSystem
        get() = when {
            this == Imperial -> UnitSystem.IMPERIAL
            this == Metric -> UnitSystem.METRIC
            else -> UnitSystem.CUSTOM
        }

    companion object {
        val Imperial = UnitPreferences(LengthUnit.INCHES, WeightUnit.POUNDS_OUNCES, VolumeUnit.FLUID_OUNCES)
        val Metric = UnitPreferences(LengthUnit.CENTIMETRES, WeightUnit.KILOGRAMS, VolumeUnit.MILLILITRES)
    }
}

// ---------------------------------------------------------------- display

/** "5.4 kg" or "12 lb 0 oz". */
fun formatWeight(grams: Double, unit: WeightUnit): String = when (unit) {
    WeightUnit.KILOGRAMS -> "${trimZero(grams / 1000.0, 1)} kg"
    WeightUnit.POUNDS_OUNCES -> poundsOunces(grams).let { (lb, oz) -> "$lb lb $oz oz" }
}

/**
 * The compound parts, for the two-part big-value display ("12" large, "lb 0 oz" small).
 *
 * Rounding happens on TOTAL ounces before splitting, which is what avoids the classic
 * carry bug: 453 g is 15.98 oz, and rounding the ounce component alone would render
 * "0 lb 16 oz".
 */
fun poundsOunces(grams: Double): Pair<Int, Int> {
    val totalOunces = (grams / GRAMS_PER_OUNCE).roundToInt()
    return (totalOunces / 16) to (totalOunces % 16)
}

/** "57.1 cm" or "22.5 in". */
fun formatLength(millimetres: Double, unit: LengthUnit): String = when (unit) {
    LengthUnit.CENTIMETRES -> "${trimZero(millimetres / 10.0, 1)} cm"
    LengthUnit.INCHES -> "${trimZero(millimetres / MM_PER_INCH, 1)} in"
}

/** "120 ml" or "4.1 oz". Bottle values land on whole/half ounces because the stepper steps by 0.5. */
fun formatVolume(millilitres: Double, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.MILLILITRES -> "${millilitres.roundToInt()} ml"
    VolumeUnit.FLUID_OUNCES -> "${trimZero(millilitres / ML_PER_US_FL_OUNCE, 1)} oz"
}

/**
 * Always Celsius, always one decimal. There is no Fahrenheit path in this app — a
 * temperature in °F is not a unit preference, it is a different number waiting to be
 * misread during a fever.
 */
fun formatTemperature(celsius: Double): String = "${trimZero(celsius, 1)} °C"

// ---------------------------------------------------------------- input → canonical

fun toGrams(value: Double, unit: WeightUnit): Double = when (unit) {
    WeightUnit.KILOGRAMS -> value * 1000.0
    WeightUnit.POUNDS_OUNCES -> value * GRAMS_PER_POUND
}

fun toGrams(pounds: Int, ounces: Int): Double = pounds * GRAMS_PER_POUND + ounces * GRAMS_PER_OUNCE

fun toMillimetres(value: Double, unit: LengthUnit): Double = when (unit) {
    LengthUnit.CENTIMETRES -> value * 10.0
    LengthUnit.INCHES -> value * MM_PER_INCH
}

fun toMillilitres(value: Double, unit: VolumeUnit): Double = when (unit) {
    VolumeUnit.MILLILITRES -> value
    VolumeUnit.FLUID_OUNCES -> value * ML_PER_US_FL_OUNCE
}

/**
 * The inverse of [toMillilitres], for pre-filling an input from a stored value.
 *
 * The amount stepper needs this: it steps in the *display* unit (0.5 oz / 10 ml) and converts once on
 * save, so stepping never accumulates the rounding error that converting a canonical ml value on every
 * press would introduce.
 */
fun fromMillilitres(millilitres: Double, unit: VolumeUnit): Double = when (unit) {
    VolumeUnit.MILLILITRES -> millilitres
    VolumeUnit.FLUID_OUNCES -> millilitres / ML_PER_US_FL_OUNCE
}

/** Steppers need a unit-appropriate increment: 0.5 oz is a spoonful, 0.5 ml is nothing. */
fun volumeStep(unit: VolumeUnit): Double = when (unit) {
    VolumeUnit.FLUID_OUNCES -> 0.5
    VolumeUnit.MILLILITRES -> 10.0
}

fun lengthStep(unit: LengthUnit): Double = when (unit) {
    LengthUnit.INCHES -> 0.25
    LengthUnit.CENTIMETRES -> 0.5
}

fun weightStep(unit: WeightUnit): Double = when (unit) {
    WeightUnit.POUNDS_OUNCES -> 1.0 / 16.0 // one ounce
    WeightUnit.KILOGRAMS -> 0.01
}

/** Drops a trailing ".0" so 4.0 renders "4" while 3.5 stays "3.5". */
private fun trimZero(value: Double, decimals: Int): String {
    val factor = when (decimals) { 1 -> 10.0; 2 -> 100.0; else -> 1.0 }
    val rounded = (value * factor).roundToInt() / factor
    return if (abs(rounded - rounded.toInt()) < 1e-9) rounded.toInt().toString()
    else String.format(java.util.Locale.ENGLISH, "%.${decimals}f", rounded)
}
