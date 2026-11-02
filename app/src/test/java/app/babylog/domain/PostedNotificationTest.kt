package app.babylog.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a posted reminder notification has stopped being true.
 *
 * The notification is written once, when the alarm fires, and cannot update itself — so an unchanged shade is
 * how a parent ends up looking at `3h 5m since the last feed` at nine in the morning having fed at seven. The
 * two facts that make it false are pinned here because neither is visible in a build: a notification that
 * clears itself on the wrong write and one that never clears at all both look fine until somebody is awake at
 * the wrong time of night.
 */
class PostedNotificationTest {

    private val evening = 1_700_000_000_000L

    @Test
    fun nothingInTheShadeIsNothingToDecide() {
        // postedAt null is the ordinary case: refresh() asks about every (activity, child) after every write,
        // and most of them never had a notification at all.
        assertFalse(shouldClearPostedNotification(ReminderConfig(), postedAt = null, latestAnchorAt = evening))
        assertFalse(
            shouldClearPostedNotification(
                ReminderConfig(enabled = true),
                postedAt = null,
                latestAnchorAt = null,
            ),
        )
    }

    @Test
    fun theRuleGoingOffTakesItsNotificationWithIt() {
        // A disabled rule is the explicit "stop telling me" — and a reminder the parent switched off is worse
        // than no notification, which is the same rule refresh() already applies to the alarm itself.
        assertTrue(
            shouldClearPostedNotification(
                ReminderConfig(enabled = false),
                postedAt = evening,
                latestAnchorAt = null,
            ),
        )
        // Even when nothing has been logged since: the rule, not the log, is the reason.
        assertTrue(
            shouldClearPostedNotification(
                ReminderConfig(enabled = false),
                postedAt = evening,
                latestAnchorAt = evening + 1,
            ),
        )
    }

    @Test
    fun loggingTheActivityClearsTheReminderItCaused() {
        assertTrue(
            shouldClearPostedNotification(
                ReminderConfig(enabled = true),
                postedAt = evening,
                latestAnchorAt = evening + 60_000,
            ),
        )
    }

    @Test
    fun aNotificationPostedAfterTheNewestLogStaysUp() {
        // A stale post is not a satisfied one: the reminder that fired at 21:00 is still the thing to act on
        // when the newest feed was at 18:00, which is exactly why it fired.
        assertFalse(
            shouldClearPostedNotification(
                ReminderConfig(enabled = true),
                postedAt = evening,
                latestAnchorAt = evening - 3 * 60 * 60 * 1000L,
            ),
        )
        // A log at the very moment of the post is not "since" it. `>` rather than `>=`: the write that caused
        // the notification to become due is older than the notification by definition.
        assertFalse(
            shouldClearPostedNotification(
                ReminderConfig(enabled = true),
                postedAt = evening,
                latestAnchorAt = evening,
            ),
        )
    }

    @Test
    fun anEnabledRuleWithNoLogLeavesTheNotificationAlone() {
        // A rule set for a child who has never had this activity logged: there is no row to compare against,
        // so nothing says the notification is out of date. It stays until the parent acts on it.
        assertFalse(
            shouldClearPostedNotification(
                ReminderConfig(enabled = true),
                postedAt = evening,
                latestAnchorAt = null,
            ),
        )
    }
}
