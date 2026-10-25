package app.babylog.data.reminders

import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.REMINDER_TYPES
import app.babylog.domain.ReminderKey
import app.babylog.domain.ReminderMode
import app.babylog.domain.TimeFormat
import app.babylog.domain.nextReminderAt
import java.time.ZoneId

/**
 * The reminder engine: one place that decides when a reminder is due, and one place that posts it.
 *
 * [refresh] recomputes and re-arms every alarm; [fire] is what a fired alarm does. Both go through the same
 * [ReminderDeps], so the alarm path and the app path cannot disagree about what "the next reminder" means.
 */
class Reminders(
    private val deps: ReminderDeps,
    private val notifications: ReminderNotifications,
    private val timeFormat: suspend () -> TimeFormat,
) {

    fun ensureChannel() = notifications.ensureChannel()

    fun notificationsEnabled(): Boolean = notifications.enabled()

    /**
     * Recompute every reminder and re-arm the alarms.
     *
     * The single entry point on purpose: called on app start, after every event write, after every reminder
     * save, on boot and after an app update. Five callers, one implementation, so no caller can invent a
     * subtly different notion of "the next reminder".
     *
     * Disabled reminders are CANCELLED here rather than merely skipped: an alarm left armed from before the
     * rule was switched off would fire anyway, and a notification the parent has turned off is worse than no
     * notification at all.
     */
    suspend fun refresh() {
        val now = deps.clock.millis()
        // One migration pass: an enabled rule with no anchor predates the field and could never fire. Stamping it
        // makes it ring an interval from now rather than never, and is what puts a time on its card.
        deps.store.configs()
            .filter { (_, config) -> config.enabled && config.anchorAt == null }
            .keys
            .forEach { key -> deps.store.stampAnchor(key, now) }

        // The same pass also rescues a rule left in the fixed-times mode: the sheet no longer offers that mode, so
        // such a rule could never fire and could never be corrected from the UI. It becomes an interval rule with
        // the interval it already had.
        deps.store.configs()
            .filter { (_, config) ->
                config.enabled && config.mode == app.babylog.domain.ReminderMode.FIXED_TIMES &&
                    config.timesOfDay.isEmpty()
            }
            .keys
            .forEach { key -> deps.store.toInterval(key) }

        val configs = deps.store.configs()
        val children = deps.children.all()
        val night = deps.night()

        children.forEach { child ->
            // Every type is visited so a rule for an activity that is no longer reminder-capable is CANCELLED
            // rather than left armed, but only [REMINDER_TYPES] can be scheduled.
            EventType.entries.forEach { type ->
                val key = ReminderKey(type, child.id)
                val config = configs[key] ?: ReminderConfig()
                val at = if (config.enabled && type in REMINDER_TYPES) {
                    nextReminderAt(config, deps.anchors.latestAnchor(type, child.id), now, deps.zone, night)
                } else {
                    null
                }
                if (at == null) deps.scheduler.cancel(key) else deps.scheduler.schedule(key, at)
            }
        }
    }

    /**
     * One reminder is due: post it, then arm whatever comes next.
     *
     * `AFTER_LAST` arms nothing. The anchor has not moved, so re-arming would fire the same reminder again and
     * again for a parent who simply has not fed yet — the nagging the owner ruled out. `FIXED_TIMES` arms the
     * following time of day, because that reminder is about the clock rather than about the last log.
     */
    suspend fun fire(type: EventType, childId: String, zone: ZoneId, now: Long) {
        val key = ReminderKey(type, childId)
        val config = deps.store.configs()[key] ?: ReminderConfig()
        if (!config.enabled) return

        val anchor = deps.anchors.latestAnchor(type, childId)
        val childName = deps.children.all().firstOrNull { it.id == childId }?.name ?: "Baby"
        notifications.post(
            type = type,
            childId = childId,
            childName = childName,
            config = config,
            anchorMs = anchor,
            now = now,
            zone = zone,
            format = timeFormat(),
            accent = ReminderNotifications.accentForType(type),
        )

        if (config.mode == ReminderMode.FIXED_TIMES) {
            val next = nextReminderAt(config, anchor, now, zone, deps.night())
            if (next == null) deps.scheduler.cancel(key) else deps.scheduler.schedule(key, next)
        }
    }
}
