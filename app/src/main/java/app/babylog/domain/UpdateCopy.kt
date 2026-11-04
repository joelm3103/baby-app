package app.babylog.domain

import app.babylog.data.update.UpdateOutcome
import app.babylog.data.update.UpdateStatus

/**
 * What Settings → About says about the last check.
 *
 * One function, because the row has to be honest in five states and a `when` inside the composable is how a
 * screen comes to describe a state it cannot reach. `UNREACHABLE` names GitHub and then says the log is
 * unaffected, which is the only reassurance a parent needs from a failed version check.
 */
fun updateStatusLine(status: UpdateStatus, nowMillis: Long): String = when {
    !status.enabled -> "Off — nothing is sent to GitHub."
    status.outcome == UpdateOutcome.NEVER_CHECKED -> "Not checked yet."
    status.outcome == UpdateOutcome.AVAILABLE -> "BabyLog ${status.version.removePrefix("v")} is available."
    status.outcome == UpdateOutcome.NO_RELEASE -> "No release published yet."
    status.outcome == UpdateOutcome.UNREACHABLE -> "Could not reach GitHub. Your log is unaffected."
    else -> "Up to date — checked ${checkedAgo(status.lastCheckAtMillis, nowMillis)}."
}

/**
 * "just now", "5m ago", "3h ago", "2d ago".
 *
 * Never a timestamp a parent has to do arithmetic on, and never a bare "today": the whole point of the age is
 * that a day-old answer is normal and a month-old one is not. A clock that went backwards reads "just now"
 * rather than a negative age.
 */
fun checkedAgo(lastCheckAtMillis: Long, nowMillis: Long): String {
    if (lastCheckAtMillis <= 0L) return "never"
    val elapsed = nowMillis - lastCheckAtMillis
    if (elapsed < 90_000L) return "just now"
    val minutes = elapsed / 60_000L
    if (minutes < 60L) return "${minutes}m ago"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}h ago"
    return "${hours / 24L}d ago"
}
