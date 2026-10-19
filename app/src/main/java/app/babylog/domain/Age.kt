package app.babylog.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Whole months, then whole weeks, then leftover days — the same decomposition the reference app displays. */
data class AgeParts(val months: Int, val weeks: Int, val days: Int)

/**
 * Age arithmetic. Pure, and every function takes the `on` date explicitly rather than reading the
 * clock, so the tests are deterministic and cannot fail at midnight.
 *
 * The adjusted-age concept is the whole point of this file: the children were born at the premature range, so
 * their chronological and adjusted ages differ by 70 days. At nine months that is a large percentile
 * difference, and reading their growth against chronological age would be simply wrong.
 */
fun adjustedBirthDate(child: Child): LocalDate = child.birthDate.plusDays(child.prematureDays.toLong())

/** Complete calendar months, then weeks and days. Matches the reference app's "9 months, 1 week, 5 days". */
fun ageParts(birth: LocalDate, on: LocalDate): AgeParts {
    if (on.isBefore(birth)) return AgeParts(0, 0, 0)
    var months = ChronoUnit.MONTHS.between(birth, on).toInt()
    var anchor = birth.plusMonths(months.toLong())
    // Defensive: never let the anchor overshoot the target date.
    if (anchor.isAfter(on)) {
        months -= 1
        anchor = birth.plusMonths(months.toLong())
    }
    val remainder = ChronoUnit.DAYS.between(anchor, on).toInt()
    return AgeParts(months = months, weeks = remainder / 7, days = remainder % 7)
}

/** "9 months, 1 week, 5 days". Omits zero units, and never renders an empty string. */
fun formatAge(parts: AgeParts): String {
    val out = mutableListOf<String>()
    if (parts.months > 0) out += plural(parts.months, "month")
    if (parts.weeks > 0) out += plural(parts.weeks, "week")
    if (parts.days > 0 || out.isEmpty()) out += plural(parts.days, "day")
    return out.joinToString(", ")
}

fun ageDays(birth: LocalDate, on: LocalDate): Long =
    ChronoUnit.DAYS.between(birth, on).coerceAtLeast(0)

/**
 * Age in days for a lookup in the WHO tables, which are keyed by day 0–1856.
 *
 * Honours the per-child `useAdjustedAge` flag by default. Getting this wrong does not produce an
 * error, it produces a confident wrong percentile, which is why it is a named function rather than
 * an inline subtraction at the call site.
 */
fun ageDaysForCharts(
    child: Child,
    on: LocalDate,
    useAdjustedAge: Boolean = child.useAdjustedAge,
): Long = ageDays(if (useAdjustedAge) adjustedBirthDate(child) else child.birthDate, on)

fun ageText(
    child: Child,
    on: LocalDate,
    useAdjustedAge: Boolean = child.useAdjustedAge,
): String = formatAge(ageParts(if (useAdjustedAge) adjustedBirthDate(child) else child.birthDate, on))

private fun plural(n: Int, unit: String) = "$n $unit${if (n == 1) "" else "s"}"
