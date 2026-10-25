package app.babylog.domain

import java.time.DayOfWeek

/**
 * A [ReminderConfig] as one DataStore string: `1|AFTER_LAST|180||0|MON,…,SUN|1761390000000`.
 *
 * One entry per (activity, child) keeps the preference file readable, and a single string means a config is
 * written atomically — six separate keys per reminder would let a crash between two writes leave a rule that
 * is half old and half new. The shape follows `encodeChildSelection` and the bottle presets: a hand-rolled
 * codec small enough to test exhaustively beats a serialisation dependency for one record type.
 *
 * Decoding is deliberately forgiving in the same direction as `Mappers.enumOrNull`: an unknown mode, an
 * unparseable interval or a day name from a future build falls back to the default rather than throwing. A
 * reminder is not worth crashing the app the parent is holding at 3am.
 */
private const val FIELD = "|"
private const val LIST = ","
private const val TIME_LIST = " "

fun encodeReminder(config: ReminderConfig): String = listOf(
    if (config.enabled) "1" else "0",
    config.mode.name,
    config.intervalMinutes.toString(),
    config.timesOfDay.distinct().sorted().joinToString(TIME_LIST),
    if (config.skipNight) "1" else "0",
    config.days.sorted().joinToString(LIST) { it.name },
    // Added after the first build: a rule with no anchor never fired. Appended rather than inserted so an older
    // value still decodes field for field.
    config.anchorAt?.toString() ?: "",
).joinToString(FIELD)

fun decodeReminder(encoded: String?): ReminderConfig {
    val defaults = ReminderConfig()
    if (encoded.isNullOrBlank()) return defaults
    val parts = encoded.split(FIELD)
    // A shorter string than expected is a config written by an older build; take what is there and let the
    // rest default rather than refusing the whole rule.
    fun at(index: Int): String? = parts.getOrNull(index)

    return ReminderConfig(
        enabled = at(0) == "1",
        mode = at(1)?.let { name -> ReminderMode.entries.firstOrNull { it.name == name } } ?: defaults.mode,
        intervalMinutes = at(2)?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.intervalMinutes,
        timesOfDay = at(3).orEmpty()
            .split(TIME_LIST)
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until 24 * 60 }
            .distinct()
            .sorted(),
        skipNight = at(4) == "1",
        anchorAt = at(6)?.trim()?.toLongOrNull()?.takeIf { it > 0 },
        days = at(5)?.let { raw ->
            raw.split(LIST)
                .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
                .toSet()
        }?.takeIf { it.isNotEmpty() } ?: defaults.days,
    )
}
