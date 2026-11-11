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

    /** What is installed while these run. The row is read against it, so every case has to name it. */
    private val installed = "1.8.1"

    @Test
    fun offIsNotAFailure() {
        val line = updateStatusLine(UpdateStatus(enabled = false), now, installed)
        assertEquals("Off — nothing is sent to GitHub.", line)
    }

    @Test
    fun aFreshInstallSaysSo() {
        assertEquals("Not checked yet.", updateStatusLine(UpdateStatus(), now, installed))
    }

    @Test
    fun anAvailableVersionIsNamedWithoutItsPrefix() {
        val status = UpdateStatus(outcome = UpdateOutcome.AVAILABLE, version = "v1.5.0", lastCheckAtMillis = now)
        assertEquals("BabyLog 1.5.0 is available.", updateStatusLine(status, now, "1.4.2"))
    }

    @Test
    fun noReleasePublishedYetIsNotAnError() {
        assertEquals("No release published yet.", updateStatusLine(UpdateStatus(outcome = UpdateOutcome.NO_RELEASE), now, installed))
    }

    @Test
    fun unreachableReassuresRatherThanAlarms() {
        val line = updateStatusLine(UpdateStatus(outcome = UpdateOutcome.UNREACHABLE), now, installed)
        assertEquals("Could not reach GitHub. Your log is unaffected.", line)
    }

    @Test
    fun upToDateCarriesItsAge() {
        val status = UpdateStatus(outcome = UpdateOutcome.UP_TO_DATE, lastCheckAtMillis = now - 3 * hour)
        assertEquals("Up to date — checked 3h ago.", updateStatusLine(status, now, installed))
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

    // Phase 31, 2026-11-10. A parent updated FROM this row and was then told for the rest of the day that
    // the version they had just installed was available, because the stored answer outlived the build that
    // made it and nothing compared the two.

    @Test
    fun theVersionJustInstalledIsNotOffered() {
        val status = UpdateStatus(
            outcome = UpdateOutcome.AVAILABLE,
            version = "v1.8.1",
            lastCheckAtMillis = now - 3 * hour,
        )
        assertEquals("Up to date — checked 3h ago.", updateStatusLine(status, now, "1.8.1"))
    }

    @Test
    fun aBuildNewerThanTheStoredAnswerIsAlsoUpToDate() {
        val status = UpdateStatus(outcome = UpdateOutcome.AVAILABLE, version = "v1.8.1", lastCheckAtMillis = now)
        assertEquals("Up to date — checked just now.", updateStatusLine(status, now, "1.9.0"))
    }

    @Test
    fun aGenuinelyNewerReleaseIsStillOffered() {
        val status = UpdateStatus(
            outcome = UpdateOutcome.AVAILABLE,
            version = "v1.8.1",
            lastCheckAtMillis = now - 2 * hour,
        )
        assertEquals("BabyLog 1.8.1 is available.", updateStatusLine(status, now, "1.7.2"))
    }

    @Test
    fun theButtonAndTheLineAgreeAboutTheSameAnswer() {
        // This is the pair that failed together: the line said "available" and the button had become
        // "Open the release", so the one control that could ask again was replaced by a page for a version
        // already installed.
        val caughtUp = UpdateStatus(outcome = UpdateOutcome.AVAILABLE, version = "v1.8.1")
        assertEquals(false, caughtUp.offersNewerThan("1.8.1"))
        assertEquals(false, caughtUp.offersNewerThan("1.8.2"))
        assertEquals(true, caughtUp.offersNewerThan("1.8.0"))
    }

    @Test
    fun onlyAnAvailableAnswerCanOfferAnything() {
        // An up-to-date answer names no version, so nothing else may read as an offer — an empty `version`
        // would otherwise parse as no number and compare as zero.
        val upToDate = UpdateStatus(outcome = UpdateOutcome.UP_TO_DATE)
        assertEquals(false, upToDate.offersNewerThan("0.1.0"))
        val unreachable = UpdateStatus(outcome = UpdateOutcome.UNREACHABLE)
        assertEquals(false, unreachable.offersNewerThan("0.1.0"))
    }
}
