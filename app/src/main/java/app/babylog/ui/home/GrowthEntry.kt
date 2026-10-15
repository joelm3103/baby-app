package app.babylog.ui.home

import app.babylog.domain.LengthUnit
import app.babylog.domain.UnitPreferences
import app.babylog.domain.WeightUnit
import app.babylog.domain.formatLength
import app.babylog.domain.formatWeight
import app.babylog.domain.poundsOunces
import app.babylog.domain.toGrams
import app.babylog.domain.toMillimetres
import kotlin.math.roundToInt

/**
 * Reading a measurement out of the growth sheet, kept out of the Compose file on purpose.
 *
 * Everything that decides what gets STORED lives here, so it can be tested on the JVM without a
 * composable, a device or a screenshot: the unit conversion, the plausibility ranges, and the rule that
 * an untouched prefill keeps its stored value.
 *
 * Storage is canonical (grams, millimetres). The unit preference only ever changes what is displayed
 * and what is typed, so switching to pounds cannot rewrite a single stored row.
 */

/** Below and above these a measurement is a typo, and storing it would wreck the chart's axis. */
private const val MIN_WEIGHT_G = 300
private const val MAX_WEIGHT_G = 30_000
private const val MIN_LENGTH_MM = 100
private const val MAX_LENGTH_MM = 1_500

/** A typo guard for the ounce field; 16+ is accepted because the carry is real, not an error. */
private const val MAX_OUNCES_TYPED = 32

/** What the parent typed. Blank means "not measured this time", which is a legitimate answer. */
data class GrowthFields(
    val weight: String = "",
    val pounds: String = "",
    val ounces: String = "",
    val length: String = "",
    val head: String = "",
)

/** Canonical values. Null means that measurement was left blank. */
data class GrowthValues(
    val weightGrams: Int? = null,
    val lengthMm: Int? = null,
    val headMm: Int? = null,
) {
    val isEmpty: Boolean get() = weightGrams == null && lengthMm == null && headMm == null
}

/**
 * The result of reading the fields: what would be stored, and what to tell the parent.
 *
 * [error] non-null means a field was filled in but could not be read, and Save must stay disabled — a
 * weight of "6.3.5" is a typo, not a 6 kg baby.
 */
data class GrowthRead(val values: GrowthValues, val error: String?)

/**
 * The weight field as editable text: "6.35" for kg, at 10 g resolution.
 *
 * Deliberately finer than the card's display (which is one decimal, the shipped convention): a
 * measurement sheet should echo what was stored, and for babies weighed to 10 g - the children were
 * 1.37 kg at birth - one decimal hides the difference between 6.35 and 6.4 kg.
 */
fun editableKilograms(grams: Int): String = trimTo(grams / 1000.0, 2)

/** The compound pair as editable text, so editing opens on the same numbers the card shows. */
fun editablePounds(grams: Int): String = poundsOunces(grams.toDouble()).first.toString()

fun editableOunces(grams: Int): String = poundsOunces(grams.toDouble()).second.toString()

/**
 * "57.1" for cm (1 mm), "22.48" for in (0.25 mm).
 *
 * Two decimals in inches is not fussiness: 0.01 in is 0.254 mm, so the imperial box round-trips a
 * stored millimetre exactly, while one decimal would lose up to 1.3 mm on every visit to the sheet.
 */
fun editableLength(millimetres: Int, unit: LengthUnit): String = when (unit) {
    LengthUnit.CENTIMETRES -> trimTo(millimetres / 10.0, 1)
    LengthUnit.INCHES -> trimTo(millimetres / 25.4, 2)
}

/** The fields a stored row opens with. Also the text that counts as "untouched". */
fun growthFieldsFor(values: GrowthValues, units: UnitPreferences): GrowthFields = GrowthFields(
    weight = values.weightGrams?.let(::editableKilograms).orEmpty(),
    pounds = values.weightGrams?.let(::editablePounds).orEmpty(),
    ounces = values.weightGrams?.let(::editableOunces).orEmpty(),
    length = values.lengthMm?.let { editableLength(it, units.length) }.orEmpty(),
    head = values.headMm?.let { editableLength(it, units.length) }.orEmpty(),
)

/**
 * Read the fields into canonical values, or explain why they cannot be read.
 *
 * [initial] is the stored row when a measurement is being EDITED. When a field's text still matches
 * what the stored value renders as, the stored value is kept verbatim rather than re-parsed through
 * the display unit. That is not a nicety: 3460 g renders as "7 lb 10 oz", and re-parsing that gives
 * 3459 g, so opening a sheet and pressing Save would silently change the log by a gram — the exact
 * defect Phase 9 found in the bottle amount.
 */
fun readGrowth(
    fields: GrowthFields,
    units: UnitPreferences,
    initial: GrowthValues? = null,
): GrowthRead {
    val seed = initial?.let { growthFieldsFor(it, units) }

    val weight = when (units.weight) {
        WeightUnit.KILOGRAMS -> readKilograms(fields.weight, seed, initial)
        WeightUnit.POUNDS_OUNCES -> readPoundsOunces(fields, seed, initial)
    }

    val length = readLength(fields.length, seed?.length, initial?.lengthMm, units.length)
    val head = readLength(fields.head, seed?.head, initial?.headMm, units.length)

    val error = listOfNotNull(weight.second, length.second, head.second).firstOrNull()
    return GrowthRead(
        values = GrowthValues(
            weightGrams = weight.first,
            lengthMm = length.first,
            headMm = head.first,
        ),
        error = error,
    )
}

private fun readKilograms(
    text: String,
    seed: GrowthFields?,
    initial: GrowthValues?,
): Pair<Int?, String?> {
    if (text.isBlank()) return null to null
    if (seed != null && text == seed.weight && initial?.weightGrams != null) {
        return initial.weightGrams to null
    }
    val kg = text.trim().toDoubleOrNull()
        ?: return null to "Check the weight — \"$text\" is not a number."
    val grams = toGrams(kg, WeightUnit.KILOGRAMS).roundToInt()
    if (grams !in MIN_WEIGHT_G..MAX_WEIGHT_G) {
        return null to "Weight must be between " +
            formatWeight(MIN_WEIGHT_G.toDouble(), WeightUnit.KILOGRAMS) + " and " +
            formatWeight(MAX_WEIGHT_G.toDouble(), WeightUnit.KILOGRAMS) + "."
    }
    return grams to null
}

private fun readPoundsOunces(
    fields: GrowthFields,
    seed: GrowthFields?,
    initial: GrowthValues?,
): Pair<Int?, String?> {
    if (fields.pounds.isBlank() && fields.ounces.isBlank()) return null to null
    if (seed != null && fields.pounds == seed.pounds && fields.ounces == seed.ounces &&
        initial?.weightGrams != null
    ) {
        return initial.weightGrams to null
    }
    val pounds = fields.pounds.trim().ifBlank { "0" }.toIntOrNull()
    val ounces = fields.ounces.trim().ifBlank { "0" }.toIntOrNull()
    if (pounds == null || ounces == null || pounds < 0 || ounces !in 0..MAX_OUNCES_TYPED) {
        return null to "Check the weight — pounds and ounces must be whole numbers."
    }
    val grams = toGrams(pounds, ounces).roundToInt()
    if (grams !in MIN_WEIGHT_G..MAX_WEIGHT_G) {
        return null to "Weight must be between " +
            formatWeight(MIN_WEIGHT_G.toDouble(), WeightUnit.POUNDS_OUNCES) + " and " +
            formatWeight(MAX_WEIGHT_G.toDouble(), WeightUnit.POUNDS_OUNCES) + "."
    }
    return grams to null
}

private fun readLength(
    text: String,
    seedText: String?,
    initialMm: Int?,
    unit: LengthUnit,
): Pair<Int?, String?> {
    if (text.isBlank()) return null to null
    if (seedText != null && text == seedText && initialMm != null) return initialMm to null
    val value = text.trim().toDoubleOrNull()
        ?: return null to "Check the measurement — \"$text\" is not a number."
    val millimetres = toMillimetres(value, unit).roundToInt()
    if (millimetres !in MIN_LENGTH_MM..MAX_LENGTH_MM) {
        return null to "Measurements must be between " +
            formatLength(MIN_LENGTH_MM.toDouble(), unit) + " and " +
            formatLength(MAX_LENGTH_MM.toDouble(), unit) + "."
    }
    return millimetres to null
}

/** Fixed-decimal text with trailing zeros dropped, matching `domain/Units.kt`'s display convention. */
private fun trimTo(value: Double, decimals: Int): String {
    val factor = if (decimals == 2) 100.0 else 10.0
    val rounded = (value * factor).roundToInt() / factor
    return if (kotlin.math.abs(rounded - rounded.toInt()) < 1e-9) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.ENGLISH, "%.${decimals}f", rounded)
    }
}
