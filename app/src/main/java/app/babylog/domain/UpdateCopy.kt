package app.babylog.domain

import app.babylog.data.update.UpdateOutcome
import app.babylog.data.update.UpdateStatus
import app.babylog.data.update.isNewerRelease

/**
 * Whether the last answer still offers a release this build does not have.
 *
 * A check is a conclusion drawn by the build that ran it, and the app installs over itself: the stored row
 * outlives the version it was about. So an `AVAILABLE` outcome is not evidence on its own — it is a version
 * that has to be compared with what is running now, every time the row is read (Phase 31, 2026-11-10).
 *
 * The failure this closes: a parent updated FROM the About row, so the phone was running the very release the
 * row kept calling available until the next day's check. The screen had no way to notice, and the button that
 * would have asked again had been replaced by `Open the release`.
 */
fun UpdateStatus.offersNewerThan(currentVersion: String): Boolean =
    outcome == UpdateOutcome.AVAILABLE && isNewerRelease(currentVersion, version)

/**
 * What Settings → About says about the last check, read against the version that is installed.
 *
 * One function, because the row has to be honest in five states and a `when` inside the composable is how a
 * screen comes to describe a state it cannot reach. `UNREACHABLE` names GitHub and then says the log is
 * unaffected, which is the only reassurance a parent needs from a failed version check.
 *
 * `currentVersion` is passed in rather than read from BuildConfig here so the case that went wrong can be
 * tested: an available version that equals this build is not available, it is what the parent just installed.
 */
fun updateStatusLine(status: UpdateStatus, nowMillis: Long, currentVersion: String): String = when {
    !status.enabled -> "Off — nothing is sent to GitHub."
    status.outcome == UpdateOutcome.NEVER_CHECKED -> "Not checked yet."
    status.offersNewerThan(currentVersion) -> "BabyLog ${status.version.removePrefix("v")} is available."
    status.outcome == UpdateOutcome.NO_RELEASE -> "No release published yet."
    status.outcome == UpdateOutcome.UNREACHABLE -> "Could not reach GitHub. Your log is unaffected."
    // Everything else — including an AVAILABLE the installed build has caught up with — is up to date, and
    // each of them says how old the answer is, because "up to date" with no age would be a claim about now.
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
