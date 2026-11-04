package app.babylog.domain

import app.babylog.data.update.UpdateOutcome
import app.babylog.data.update.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the About row says, and how old it admits to being.
 *
 * The rule these pin: a failed check never reads like a broken app, an off switch never reads like a failure,
 * and an age is always stated rather than implied — "Up to date" with no age would be a claim about now.
 */
class UpdateCopyTest {

    private val now = 1_000_000_000_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun offIsNotAFailure() {
        val line = updateStatusLine(UpdateStatus(enabled = false), now)
        assertEquals("Off — nothing is sent to GitHub.", line)
    }

    @Test
    fun aFreshInstallSaysSo() {
        assertEquals("Not checked yet.", updateStatusLine(UpdateStatus(), now))
    }

    @Test
    fun anAvailableVersionIsNamedWithoutItsPrefix() {
        val status = UpdateStatus(outcome = UpdateOutcome.AVAILABLE, version = "v1.5.0", lastCheckAtMillis = now)
        assertEquals("BabyLog 1.5.0 is available.", updateStatusLine(status, now))
    }

    @Test
    fun noReleasePublishedYetIsNotAnError() {
        assertEquals("No release published yet.", updateStatusLine(UpdateStatus(outcome = UpdateOutcome.NO_RELEASE), now))
    }

    @Test
    fun unreachableReassuresRatherThanAlarms() {
        val line = updateStatusLine(UpdateStatus(outcome = UpdateOutcome.UNREACHABLE), now)
        assertEquals("Could not reach GitHub. Your log is unaffected.", line)
    }

    @Test
    fun upToDateCarriesItsAge() {
        val status = UpdateStatus(outcome = UpdateOutcome.UP_TO_DATE, lastCheckAtMillis = now - 3 * hour)
        assertEquals("Up to date — checked 3h ago.", updateStatusLine(status, now))
    }

    @Test
    fun anAgeIsFriendlyAndBounded() {
        assertEquals("never", checkedAgo(0L, now))
        assertEquals("just now", checkedAgo(now - 30_000L, now))
        assertEquals("5m ago", checkedAgo(now - 5 * 60_000L, now))
        assertEquals("3h ago", checkedAgo(now - 3 * hour, now))
        assertEquals("2d ago", checkedAgo(now - 50 * hour, now))
        // A clock that went backwards reads "just now" rather than a negative age.
        assertEquals("just now", checkedAgo(now + hour, now))
    }
}
