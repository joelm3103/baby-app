package app.babylog.data.reminders

import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.TimeFormat
import androidx.compose.ui.graphics.Color
import java.time.ZoneId

/**
 * What the reminder engine needs from the notification shade.
 *
 * An interface for the same reason [ReminderStore] and [ReminderAnchors] are: the engine's decisions have to be
 * testable on the JVM, and `NotificationManagerCompat` cannot be reached from a unit test. [ReminderNotifications]
 * is the only real implementation; tests drive a fake.
 *
 * [postedAt] is what makes clearing possible at all. Without it the engine can post but can never know whether
 * the thing it posted is still true — and a shade full of last night's reminders, each still saying
 * `3h 5m since the last feed`, is exactly what the owner reported.
 */
interface ReminderNotifier {

    fun ensureChannel()

    /** True when the parent has allowed notifications; false is a state the app explains rather than hides. */
    fun enabled(): Boolean

    fun post(
        type: EventType,
        childId: String,
        childName: String,
        config: ReminderConfig,
        anchorMs: Long?,
        now: Long,
        zone: ZoneId,
        format: TimeFormat,
        accent: Color,
    )

    /** Takes this (activity, child) notification out of the shade. A no-op when there is none. */
    fun cancel(type: EventType, childId: String)

    /**
     * When the live notification for this (activity, child) was posted, or null when there is none.
     *
     * Read from the shade rather than remembered in the preferences: the parent can swipe a notification away or
     * turn the channel off outside the app, and anything remembered here would then disagree with what is
     * actually on screen.
     */
    fun postedAt(type: EventType, childId: String): Long?
}
