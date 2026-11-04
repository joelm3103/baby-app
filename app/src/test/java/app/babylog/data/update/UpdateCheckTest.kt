package app.babylog.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check that decides whether the app nags a parent.
 *
 * Every case here is one that a wrong answer would show on the About row: a version compare by text would
 * call `1.10.0` older than `1.4.1`, and an interval that ignores the switch would send a request from a phone
 * whose owner turned it off. Plain JUnit, no Robolectric — one of these has to be fast enough to run while
 * the suite is growing.
 */
class UpdateCheckTest {

    @Test
    fun aVersionIsItsNumericRun() {
        assertEquals(listOf(1, 5, 0), parseVersion("1.5.0"))
        assertEquals(listOf(1, 4, 1), parseVersion("v1.4.1"))
        assertEquals(listOf(1, 5, 0), parseVersion("  1.5.0  "))
        // A pre-release suffix ends the numeric run rather than being compared: the app only ever asks
        // "is the published one newer", and it reads its own tags.
        assertEquals(listOf(1, 5, 0), parseVersion("1.5.0-beta.2"))
        assertEquals(listOf(2), parseVersion("2"))
    }

    @Test
    fun aSegmentTooLargeForAnIntMakesTheTagUnreadable() {
        // Cutting the segment short would compare `1.5.0.99999999999999999999` as `1.5.0` — the newer release
        // would read as equal to the installed one, and the About row would say it was up to date.
        assertNull(parseVersion("1.5.0.99999999999999999999"))
        assertFalse(isNewerRelease("1.5.0", "1.5.0.99999999999999999999"))
    }

    @Test
    fun aVersionWithNoNumberIsUnreadable() {
        assertNull(parseVersion(""))
        assertNull(parseVersion("beta"))
        assertNull(parseVersion("v"))
        assertNull(compareVersions("beta", "1.5.0"))
    }

    @Test
    fun theComparisonIsNumericNotAlphabetic() {
        assertTrue(compareVersions("1.4.1", "1.10.0")!! > 0)
        assertTrue(compareVersions("1.10.0", "1.4.1")!! < 0)
        assertEquals(0, compareVersions("1.5.0", "v1.5.0"))
        // Missing parts are zero, so these are the same version rather than a spurious update.
        assertEquals(0, compareVersions("1.5", "1.5.0"))
        assertTrue(compareVersions("1.5", "1.6")!! > 0)
    }

    @Test
    fun onlyAStrictlyNewerReleaseCounts() {
        assertTrue(isNewerRelease("1.4.1", "v1.5.0"))
        assertFalse(isNewerRelease("1.5.0", "1.5.0"))
        assertFalse(isNewerRelease("1.5.1", "1.5.0"))
        // An unreadable tag is never evidence of a newer build: sending a parent to a page for a version the
        // app could not parse is worse than saying nothing.
        assertFalse(isNewerRelease("1.5.0", "latest"))
    }

    @Test
    fun theSwitchOffMeansNoRequestAtAll() {
        val now = 1_000_000_000_000L
        assertFalse(shouldCheckForUpdates(enabled = false, lastCheckAtMillis = 0L, nowMillis = now))
        // Not even a stale one: off is off, however long it has been.
        assertFalse(shouldCheckForUpdates(false, now - 30L * 24 * 60 * 60 * 1000, now))
    }

    @Test
    fun aDayIsTheInterval() {
        val now = 1_000_000_000_000L
        val hour = 60 * 60 * 1000L
        assertTrue(shouldCheckForUpdates(true, 0L, now))
        assertFalse(shouldCheckForUpdates(true, now - hour, now))
        assertFalse(shouldCheckForUpdates(true, now - 23 * hour, now))
        assertTrue(shouldCheckForUpdates(true, now - 25 * hour, now))
        // Exactly a day is due rather than a minute short of one: the boundary is inclusive, and a test that
        // only brackets it (23 and 25) would pass with either comparison in place.
        assertTrue(shouldCheckForUpdates(true, now - 24 * hour, now))
    }

    @Test
    fun aClockThatWentBackwardsDoesNotFixItselfByAskingForever() {
        val now = 1_000_000_000_000L
        val hour = 60 * 60 * 1000L
        // A future timestamp (the parent moved the clock, or an NTP correction) yields a negative age, which
        // fails the interval. The alternative is a request on every open until the clock catches up.
        assertFalse(shouldCheckForUpdates(true, now + hour, now))
    }

    @Test
    fun theApiEndpointIsDerivedFromTheOneRepositoryLink() {
        assertEquals(
            "https://api.github.com/repos/joelm3103/baby-app/releases/latest",
            releasesApiFor("https://github.com/joelm3103/baby-app"),
        )
        assertEquals(
            "https://api.github.com/repos/joelm3103/baby-app/releases/latest",
            releasesApiFor("https://github.com/joelm3103/baby-app/"),
        )
        assertEquals(
            "https://github.com/joelm3103/baby-app/releases/latest",
            releasesPageFor("https://github.com/joelm3103/baby-app"),
        )
        assertNull(releasesApiFor("https://github.com/joelm3103"))
    }

    @Test
    fun aLookupBecomesOneOutcomeAndAtMostOneVersion() {
        val newer = UpdateLookup.Found(ReleaseInfo("v1.5.0", "https://example/1.5.0"))
        assertEquals(UpdateOutcome.AVAILABLE to "v1.5.0", outcomeFor("1.4.1", newer))

        val same = UpdateLookup.Found(ReleaseInfo("v1.4.1", "https://example/1.4.1"))
        assertEquals(UpdateOutcome.UP_TO_DATE to "", outcomeFor("1.4.1", same))

        assertEquals(UpdateOutcome.NO_RELEASE to "", outcomeFor("1.4.1", UpdateLookup.NoRelease))
        assertEquals(UpdateOutcome.UNREACHABLE to "", outcomeFor("1.4.1", UpdateLookup.Unreachable))
    }

}
