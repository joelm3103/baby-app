package app.babylog.ui.home

import app.babylog.domain.TemperatureUnit
import app.babylog.domain.toCelsius
import app.babylog.domain.BabyEvent
import app.babylog.domain.DoseUnit
import app.babylog.domain.EventType
import app.babylog.domain.doseNumber
import app.babylog.domain.formatDose
import app.babylog.domain.formatTemperature

/**
 * Reading the two Health sheets out of their fields, kept out of the Compose files on purpose.
 *
 * Everything that decides what gets STORED lives here, so it can be tested on the JVM without a
 * composable, a device or a screenshot: the plausibility range, both "at least one field" rules, and
 * the rule that an untouched prefill keeps its stored value.
 *
 * Temperature input uses the selected unit, validates the canonical Celsius range, and
 * preserves an untouched stored value rather than rounding it through the display.
 */

/** Below and above these a temperature is a typo, or a Fahrenheit reading in a Celsius box. */
internal const val MIN_TEMPERATURE_C = 30.0
internal const val MAX_TEMPERATURE_C = 45.0

/** A dose of 0 is nothing; above this it is a typo (or millilitres typed into a milligrams field). */
private const val MAX_DOSE = 1000.0

/** What the parent typed on the health sheet. Blank means "not recorded", which is a legitimate answer. */
data class HealthFields(
    val temperature: String = "",
    val medication: String = "",
    val dose: String = "",
    val doseUnit: DoseUnit? = null,
)

/** Canonical values. Null means that field was left blank. */
data class HealthValues(
    val temperatureC: Double? = null,
    val medication: String? = null,
    /** Always set or unset together with [doseUnit]: half a dose is not a dose. */
    val doseAmount: Double? = null,
    val doseUnit: DoseUnit? = null,
) {
    /**
     * A record needs a temperature or a medication. A dose on its own is not a record — of what? — and
     * this is the rule that keeps the Phase-5 "health event with no values" stub from coming back.
     */
    val isEmpty: Boolean get() = temperatureC == null && medication == null
}

/**
 * The result of reading the health fields: what would be stored, and what to tell the parent.
 *
 * [error] non-null means a field was filled in but could not be read, and Save must stay disabled —
 * "38.2.1" is a typo, not a temperature. A blank sheet is not an error: it is an incomplete one, and
 * the sheet says so in words rather than leaving a dead button unexplained.
 */
data class HealthRead(
    val values: HealthValues,
    val temperatureError: String?,
    val doseError: String?,
) {
    val error: String? get() = temperatureError ?: doseError
}

/** What the parent typed on the vaccine sheet. */
data class VaccineFields(val vaccineName: String = "")

/** Null [vaccineName] means the field is blank, so there is nothing to save yet. */
data class VaccineRead(val vaccineName: String?, val error: String? = null)

/**
 * The temperature as editable text: "38.2" for 38.2, "38" for 38.0.
 *
 * Derived from `formatTemperature` rather than formatted again here, so the sheet opens on exactly the
 * number the card shows — a second formatting rule is how the two drift apart.
 */
fun editableTemperature(celsius: Double, unit: TemperatureUnit = TemperatureUnit.CELSIUS): String = formatTemperature(celsius, unit).removeSuffix(" ${unit.label}")

/** The fields a stored row opens with. Also the text that counts as "untouched". */
fun healthFieldsFor(values: HealthValues, unit: TemperatureUnit = TemperatureUnit.CELSIUS): HealthFields = HealthFields(
    temperature = values.temperatureC?.let { editableTemperature(it, unit) }.orEmpty(),
    medication = values.medication.orEmpty(),
    dose = values.doseAmount?.let(::editableDose).orEmpty(),
    doseUnit = values.doseUnit,
)

/**
 * The dose amount as editable text: "2.5" for 2.5, "5" for 5.0 — the same trimming [formatDose] uses,
 * so the box opens on the number the row shows.
 */
fun editableDose(amount: Double): String = doseNumber(amount)

/**
 * Read the health fields into canonical values, or explain why they cannot be read.
 *
 * [initial] is the stored row when a record is being EDITED. When the temperature text still matches
 * what the stored value renders as, the stored double is kept verbatim rather than re-parsed through
 * the display format — which is what makes opening a pre-filled sheet and pressing Save a no-op. This
 * is the Phase-9 defect that rewrote a bottle's 90 ml as 89 ml, closed here the same way
 * `readGrowth` closes it.
 */
fun readHealth(fields: HealthFields, initial: HealthValues? = null, unit: TemperatureUnit = TemperatureUnit.CELSIUS): HealthRead {
    val seed = initial?.let { healthFieldsFor(it, unit) }
    val temperature = readTemperature(fields.temperature, seed?.temperature, initial?.temperatureC, unit)
    val dose = readDose(fields.dose, fields.doseUnit, seed?.dose, initial)
    return HealthRead(
        values = HealthValues(
            temperatureC = temperature.first,
            medication = fields.medication.trim().ifEmpty { null },
            doseAmount = dose.first,
            doseUnit = dose.second,
        ),
        temperatureError = temperature.second,
        doseError = dose.third,
    )
}

/**
 * Read the dose. Returns (amount, unit, error), where the two halves are always both present or both
 * null.
 *
 * The asymmetry here is deliberate. An AMOUNT with no unit is an error rather than a silent drop: the
 * parent typed 2.5, and saving the record without it would be the same class of defect as the bottle
 * amount Phase 9 found (the log changing quietly under the parent's hands). A UNIT with no amount is
 * simply no dose — nothing was typed, so nothing is lost, and a half-filled picker cannot block a save.
 */
private fun readDose(
    text: String,
    unit: DoseUnit?,
    seedText: String?,
    initial: HealthValues?,
): Triple<Double?, DoseUnit?, String?> {
    if (text.isBlank()) return Triple(null, null, null)
    if (unit == null) {
        return Triple(null, null, "Choose the dose unit \u2014 how much of what?")
    }
    // An untouched prefill keeps its stored value, so opening a dose of 2.5 and saving cannot round it.
    if (seedText != null && text == seedText && initial?.doseAmount != null && initial.doseUnit == unit) {
        return Triple(initial.doseAmount, unit, null)
    }
    val amount = text.trim().toDoubleOrNull()
        ?: return Triple(null, null, "Check the dose \u2014 \"$text\" is not a number.")
    if (amount <= 0.0 || amount > MAX_DOSE) {
        return Triple(null, null, "The dose must be between 0 and ${editableDose(MAX_DOSE)} ${unit.label}.")
    }
    return Triple(amount, unit, null)
}

/**
 * The dose last recorded for [medication] for each child in [childIds], as `childId to "2.5 ml"`.
 *
 * A HINT, never a value: the sheet shows it in grey and leaves the field empty. Two reasons. A correct
 * infant dose depends on weight and age and changes as the children grow, so the app proposing a number
 * would be inventing advice. And the parent's own last entry is still the most useful thing on screen
 * while holding a bottle and a syringe.
 *
 * The name must match exactly (ignoring case and surrounding space), because "Calpol" and "Paracetamol
 * (Calpol)" are two different strings a parent may use, and guessing which relates to which is how a
 * hint becomes a wrong hint.
 */
fun lastDoses(
    entries: List<BabyEvent>,
    childIds: List<String>,
    medication: String,
): List<Pair<String, String>> {
    val wanted = medication.trim()
    if (wanted.isEmpty()) return emptyList()
    return childIds.mapNotNull { childId ->
        val latest = entries
            .filter { it.type == EventType.HEALTH && it.childId == childId }
            .filter { it.medication?.trim().equals(wanted, ignoreCase = true) }
            .filter { it.doseAmount != null && it.doseUnit != null }
            .maxByOrNull { it.startedAt }
        latest?.let { childId to formatDose(it.doseAmount, it.doseUnit)!! }
    }
}

/**
 * Read the vaccine name. The name IS the record, so a blank field is not a savable vaccine — there is
 * no other field that could carry the meaning.
 */
fun readVaccine(fields: VaccineFields): VaccineRead =
    VaccineRead(vaccineName = fields.vaccineName.trim().ifEmpty { null })

private fun readTemperature(
    text: String,
    seedText: String?,
    initialCelsius: Double?,
    unit: TemperatureUnit,
): Pair<Double?, String?> {
    if (text.isBlank()) return null to null
    if (seedText != null && text == seedText && initialCelsius != null) return initialCelsius to null
    val value = text.trim().toDoubleOrNull()
        ?: return null to "Check the temperature \u2014 \"$text\" is not a number."
    val celsius = toCelsius(value, unit)
    if (!celsius.isFinite() || celsius < MIN_TEMPERATURE_C || celsius > MAX_TEMPERATURE_C) {
        return null to "Temperature must be between " +
            "${editableTemperature(MIN_TEMPERATURE_C, unit)} and ${editableTemperature(MAX_TEMPERATURE_C, unit)} " +
            "${unit.label}."
    }
    return celsius to null
}
