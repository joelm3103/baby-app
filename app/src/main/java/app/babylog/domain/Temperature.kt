package app.babylog.domain

import kotlin.math.roundToInt

/**
 * The four temperature bands, marked by SHAPE on the card rather than by hue.
 *
 * [FEVER_C] (`38.0`, in `BabyEvent.kt`) keeps its meaning as the top boundary — exactly 38.0 is a fever —
 * so every existing call site that reasons about "is this a fever" still gets the same answer.
 */
const val NORMAL_MIN_C = 36.5

/** The bottom of the mild-fever band; below [FEVER_C] and above [NORMAL_MIN_C]. */
const val MILD_FEVER_MIN_C = 37.6

/** Range of a reading, as the card labels it. */
enum class TemperatureBand { LOW, NORMAL, MILD_FEVER, FEVER }

/**
 * The band a reading falls in, or null for no reading.
 *
 * **The band is computed from the number as it is DISPLAYED**, i.e. rounded to one decimal first — the
 * same rounding `trimZero(value, 1)` applies in `formatTemperature`, so a marker can never disagree with
 * the reading printed beside it. That cuts both ways and is deliberate: `37.99` renders `38.0`, so it is a
 * FEVER, while `37.94` renders `37.9` and is MILD_FEVER. Classifying the raw double instead would let a
 * card print `38.0 °C` with a `Mild` pill.
 *
 * Bands are always computed in Celsius, so the °C/°F preference cannot move a reading between bands.
 */
fun temperatureBand(celsius: Double?): TemperatureBand? {
    if (celsius == null) return null
    // Exactly the display rounding: (v * 10).roundToInt() / 10.0 is what trimZero(_, 1) does.
    val shown = (celsius * 10.0).roundToInt() / 10.0
    return when {
        shown < NORMAL_MIN_C -> TemperatureBand.LOW
        shown < MILD_FEVER_MIN_C -> TemperatureBand.NORMAL
        shown < FEVER_C -> TemperatureBand.MILD_FEVER
        else -> TemperatureBand.FEVER
    }
}

/**
 * True when a stored temperature is at or above [FEVER_C].
 *
 * One rule with [temperatureBand] rather than a second comparison, so the pill and the colour on a card
 * cannot drift apart.
 */
fun isFever(celsius: Double?): Boolean = temperatureBand(celsius) == TemperatureBand.FEVER

/**
 * The band a UI marker should show, or null when it needs none.
 *
 * NORMAL collapses to null here rather than at every draw site: a pill on a normal reading is noise on a
 * card that already prints the number. LOW is kept — a low reading is worth marking, it is simply not
 * something to watch over days, which is why it stays out of the Trends table.
 */
fun markedBand(celsius: Double?): TemperatureBand? =
    temperatureBand(celsius)?.takeIf { it != TemperatureBand.NORMAL }
