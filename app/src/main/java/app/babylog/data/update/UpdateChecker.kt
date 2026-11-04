package app.babylog.data.update

import app.babylog.data.repo.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that decides whether the app asks GitHub anything, and what it then stores.
 *
 * Two entry points and no third. [checkIfDue] is the app-start path and respects both the switch and the
 * day's interval; [checkNow] is the About row's button and respects only the switch, because a parent who
 * taps it has asked the question. Nothing else may call the source — a second path would be a second set of
 * rules about when a phone talks to the internet, and the About screen's sentence is only true if there is
 * exactly one.
 */
class UpdateChecker(
    private val settings: SettingsRepository,
    private val currentVersion: String,
    private val source: UpdateSource = GitHubUpdateSource(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Runs only when the parent has left it on and the last answer is more than a day old. */
    suspend fun checkIfDue() {
        val status = settings.updateStatus.first()
        if (!shouldCheckForUpdates(status.enabled, status.lastCheckAtMillis, now())) return
        run()
    }

    /** The manual check. Ignores the interval, never the switch. */
    suspend fun checkNow() {
        if (!settings.updateStatus.first().enabled) return
        run()
    }

    private suspend fun run() {
        val (outcome, version) = outcomeFor(currentVersion, source.latestRelease())
        settings.recordUpdateCheck(now(), outcome, version)
    }
}
