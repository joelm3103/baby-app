package app.babylog.data.reminders

import app.babylog.domain.Child
import app.babylog.domain.EventType
import app.babylog.domain.NightWindow
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import java.time.Clock
import java.time.ZoneId

/**
 * What the reminder engine needs from the rest of the app, as three small interfaces rather than Room and
 * DataStore directly.
 *
 * This is the `SyncGateway` pattern again, for the same reason: Robolectric cannot collect a Room-backed Flow
 * (the paused main looper never pumps Room's invalidation callbacks), so anything that must be *tested* has to
 * be reachable without a database. The reminder engine is exactly the code where a mistake is invisible until
 * a notification fails to arrive.
 */
interface ReminderStore {
    suspend fun configs(): Map<ReminderKey, ReminderConfig>

    /**
     * Write a rule back with an anchor.
     *
     * Used for one migration: a rule written before [ReminderConfig.anchorAt] existed is enabled but has no anchor,
     * so it could never fire and its card showed a rule with no time. Stamping it once, at the moment it is first
     * seen, makes it ring an interval from then instead of never.
     */
    suspend fun stampAnchor(key: ReminderKey, at: Long)

    /** Rescue a rule left in the fixed-times mode the sheet no longer offers. */
    suspend fun toInterval(key: ReminderKey)
}

interface ReminderAnchors {
    /** That child's newest non-deleted row of that activity, or null if they have never had one. */
    suspend fun latestAnchor(type: EventType, childId: String): Long?
}

interface ReminderChildren {
    suspend fun all(): List<Child>
}

/** The collaborators a refresh or a fire needs, bundled so the constructor stays readable. */
class ReminderDeps(
    val store: ReminderStore,
    val anchors: ReminderAnchors,
    val children: ReminderChildren,
    val scheduler: ReminderScheduler,
    val night: suspend () -> NightWindow,
    val clock: Clock = Clock.systemDefaultZone(),
    val zone: ZoneId = ZoneId.systemDefault(),
)
