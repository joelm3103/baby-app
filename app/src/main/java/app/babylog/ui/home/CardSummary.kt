package app.babylog.ui.home

import app.babylog.domain.TemperatureUnit
import app.babylog.domain.TemperatureBand
import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperColor
import app.babylog.domain.EventType
import app.babylog.domain.markedBand
import app.babylog.domain.UnitPreferences
import app.babylog.domain.elapsed
import app.babylog.domain.formatLength
import app.babylog.domain.formatTemperature
import app.babylog.domain.formatVolume
import app.babylog.domain.formatWeight
import app.babylog.domain.poundsOunces
import app.babylog.domain.WeightUnit
import app.babylog.domain.formatDose

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
        formatTemperature(c, units.temperature).removeSuffix(" ${units.temperature.label}") to units.temperature.label
    }
}

/** The line under the big value: what actually happened. */
fun detailLine(event: BabyEvent, units: UnitPreferences): String = when (event.type) {
    EventType.BOTTLE -> event.formulaBrand ?: "Bottle"
    EventType.DIAPER -> diaperDetail(event)
    EventType.SOLIDS -> event.foodIds.takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { it.replace('-', ' ') }
        ?: (event.mealSlot?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Solids")
    EventType.SLEEP -> if (event.isRunning) "Asleep now" else "Slept ${elapsed(event.durationMs ?: 0)}"
    EventType.GROWTH -> growthValues(event, units)
    // The word `fever` is deliberately NOT in this string: wherever a temperature is shown the marker is
    // the `Fever` pill, so a text copy would say it twice. The card reads `38.2 °C` big, with
    // `Calpol 2.5 ml` underneath and a pill beside the number.
    EventType.HEALTH -> listOfNotNull(
        medicationWithDose(event),
        event.vaccineName?.let(::vaccineLabel),
    ).joinToString(" \u00b7 ").ifBlank { "Health" }
}

/**
 * A diaper change in one line: what it was, what the stool was like, and whether there was a rash.
 *
 * `Wet` · `Dirty` · `Wet + dirty` · `Dirty · mushy, runny` · `Dirty · rash`.
 *
 * "Both" reads `Wet + dirty` rather than `Mixed`. The storage value is still `MIXED` — the column has
 * carried those three names since schema v1 and nothing about a row's meaning may move — but the question
 * the parent answered was two yes/no answers, and the words should say so back. `Mixed` also invited the
 * reading "mixed = some wet, some dirty", which was never what it meant.
 *
 * CONSISTENCY IS WORDS, COLOUR IS NOT: colour is drawn as dots by [diaperColors] because a row showing
 * "brown" beside a brown circle would say it twice, and the owner asked for the circle. So this string
 * never contains a colour name — the only place those words appear is TalkBack, where a dot cannot speak.
 *
 * An unrecorded colour or consistency contributes NOTHING. It must not say "unknown" on every row: the
 * absence is the truth, exactly as it is for `rash`, where `null` and `false` are different claims.
 */
fun diaperDetail(event: BabyEvent): String = buildString {
    val kind = event.diaperKind
    append(
        when {
            kind == null -> "Diaper"
            kind.wet && kind.dirty -> "Wet + dirty"
            kind.dirty -> "Dirty"
            else -> "Wet"
        },
    )
    // Only a dirty nappy has a consistency, so a wet-only row cannot carry one even if the data somehow
    // did; the sheet clears them on save, and this is the second line of defence for a synced row.
    val consistency = if (kind?.dirty == true) event.diaperConsistency else emptyList()
    if (consistency.isNotEmpty()) {
        append(" · ")
        append(consistency.joinToString(", ") { it.name.lowercase() })
    }
    if (event.rash == true) append(" · rash")
}

/** The colours to replay as dots on this row — empty for a wet-only change, whatever the row holds. */
fun diaperColors(event: BabyEvent): List<DiaperColor> =
    if (event.diaperKind?.dirty == true) event.diaperColors else emptyList()

/**
 * `Calpol 2.5 ml` — the medicine and how much of it, as one phrase.
 *
 * Joined here rather than at each call site so the card, the entry list and the History rows cannot
 * disagree about whether a dose is shown; History pulls the two apart again into its own rows, because
 * "which medicines has she had" is a different question from "how much was that one".
 */
internal fun medicationWithDose(event: BabyEvent): String? =
    listOfNotNull(event.medication, formatDose(event.doseAmount, event.doseUnit))
        .joinToString(" ")
        .ifEmpty { null }

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
    // A health row has to say WHICH record it is. The old label showed only the medication, so a fever
    // row read "Health" and looked identical to a vaccine - while the temperature itself was the card's
    // headline and never appeared in the list. The rows and the headline now agree, which is the rule
    // `PLAN.md`'s Phase 9.1 set for every other card.
    EventType.HEALTH -> healthRecordLine(event, units.temperature)
        .ifEmpty { event.vaccineName?.let(::vaccineLabel) ?: "Health" }
    else -> detailLine(event, units)
}

/**
 * The Health card's three kinds of record for one child.
 *
 * Latest of EACH KIND, not the newest entry — the rule [GrowthLatest] follows, and this card needs it for
 * a sharper reason. The first version held "record" (temperature OR medication) and "vaccine", so a
 * medication logged at 12:27 hid a 38.5 °C fever logged at 12:08: the fever was not merely dimmed on the
 * card, it was ABSENT from it. Found by the owner looking at the phone, not by a test.
 */
data class HealthLatest(
    val temperature: BabyEvent?,
    val medication: BabyEvent?,
    val vaccine: BabyEvent?,
) {
    val isEmpty: Boolean get() = temperature == null && medication == null && vaccine == null
}

/**
 * Picks the newest entry of each kind. Expects the card's newest-first list but sorts defensively, so a
 * caller cannot change the answer by reordering.
 */
fun healthLatest(entries: List<BabyEvent>): HealthLatest {
    val health = entries
        .filter { it.type == EventType.HEALTH }
        .sortedByDescending { it.startedAt }
    return HealthLatest(
        temperature = health.firstOrNull { it.temperatureC != null },
        medication = health.firstOrNull { it.medication != null || it.doseAmount != null },
        vaccine = health.firstOrNull { it.vaccineName != null },
    )
}

/**
 * The lines the Both-mode Health card shows for one child: the reading, then what was given, then the
 * vaccine — with the reading and the medication combined into ONE line when they came from the same
 * record, which is how a fever and its dose are normally logged.
 *
 * [band] on a line is what the card turns into the band pill, so the marker travels with the reading
 * rather than being re-derived by the renderer. Null means NORMAL or no reading at all.
 */
fun healthLines(latest: HealthLatest, unit: TemperatureUnit = TemperatureUnit.CELSIUS): List<ChildLine> {
    val temperature = latest.temperature
    val medication = latest.medication
    val sameRecord = temperature != null && medication != null && temperature.id == medication.id
    return listOfNotNull(
        when {
            sameRecord -> ChildLine(
                healthRecordLine(temperature!!, unit),
                band = markedBand(temperature.temperatureC),
            )
            temperature != null -> ChildLine(
                formatTemperature(temperature.temperatureC!!, unit),
                band = markedBand(temperature.temperatureC),
            )
            else -> null
        },
        // The medication gets its own line only when it is not already on the reading's line.
        if (sameRecord) null else medication?.let { ChildLine(medicationWithDose(it)!!) },
        latest.vaccine?.vaccineName?.let { ChildLine(vaccineLabel(it)) },
    )
}

/**
 * `38.2 °C · fever · Calpol` — the reading, the word that interprets it, then what was given.
 *
 * A bare "Calpol" does not say when the temperature was taken, and a bare temperature does not say what
 * it was for, so the row carries both while the card's detail line stays free of the number the headline
 * already shows.
 */
fun healthRecordLine(event: BabyEvent, unit: TemperatureUnit = TemperatureUnit.CELSIUS): String = listOfNotNull(
    event.temperatureC?.let { formatTemperature(it, unit) },
    medicationWithDose(event),
).joinToString(" \u00b7 ")

/**
 * `6-in-1 vaccine`, unless the parent already wrote the word — a record typed as "Vaccine 6-in-1" must
 * not read "Vaccine 6-in-1 vaccine".
 */
fun vaccineLabel(name: String): String =
    if (name.contains("vaccin", ignoreCase = true)) name else "$name vaccine"

/** One child's lines for the Both-mode Health card. [hasData] false means show the empty line instead. */
data class ChildHealth(val childId: String, val lines: List<ChildLine>, val hasData: Boolean)

fun healthByChild(entries: List<BabyEvent>, childIds: List<String>, unit: TemperatureUnit = TemperatureUnit.CELSIUS): List<ChildHealth> =
    childIds.map { childId ->
        val latest = healthLatest(entries.filter { it.childId == childId })
        ChildHealth(
            childId = childId,
            lines = healthLines(latest, unit),
            hasData = !latest.isEmpty,
        )
    }

/**
 * One child's column on a card that shows the children side by side.
 *
 * Growth and Health both split in Both mode, for the same reason: a single combined value has no owner,
 * and "6 kg" or "38.2 °C" in Both mode is one child's reading dressed up as the twins'. The two are
 * unified here so the card has ONE renderer, and a third activity that ever needs columns adds a branch
 * instead of a second copy of the layout.
 */
data class ChildColumn(val childId: String, val lines: List<ChildLine>, val hasData: Boolean)

/**
 * One line inside a column. [band] is the reading's band when it carries a marker, so the card can draw
 * the pill without re-deriving the rule — the column and the headline must flag the same readings.
 * NORMAL and a line with no temperature are both null: NORMAL has no pill by design.
 */
data class ChildLine(val text: String, val band: TemperatureBand? = null)


/** The columns for an activity that splits per child, or null for one that does not. */
fun cardColumns(
    type: EventType,
    entries: List<BabyEvent>,
    childIds: List<String>,
    units: UnitPreferences,
): List<ChildColumn>? = when (type) {
    EventType.GROWTH -> growthByChild(entries, childIds, units)
        .map { column -> ChildColumn(column.childId, column.lines.map { ChildLine(it) }, column.hasData) }
    EventType.HEALTH -> healthByChild(entries, childIds, units.temperature)
        .map { column ->
            ChildColumn(
                childId = column.childId,
                lines = column.lines.map { ChildLine(it.text, band = it.band) },
                hasData = column.hasData,
            )
        }
    else -> null
}
