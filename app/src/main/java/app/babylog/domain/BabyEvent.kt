package app.babylog.domain

/**
 * The four things this app logs, and nothing else. Adding a value here is a product
 * decision, not a technical one — see .hermes/skills/babylog-ui/references/style-guide.md.
 */
enum class EventType {
    FEED_BOTTLE,
    FEED_NURSING,
    SLEEP,
    DIAPER,
    PUMP,
}

enum class NursingSide { LEFT, RIGHT, BOTH }

/** Deliberately three options. Both source apps shipped a clinical taxonomy
 *  (size, six colours, five consistencies, rash flag) that nobody asks for at 3 AM. */
enum class DiaperKind { PEE, POO, BOTH }

/**
 * A single logged event. `endedAt == null` means a timer is still running.
 *
 * Timestamps are epoch millis in UTC; display conversion happens at the edge, in
 * [app.babylog.domain.Formatting] and the UI, never here.
 */
data class BabyEvent(
    val id: String,
    val childId: String,
    val type: EventType,
    val startedAt: Long,
    val endedAt: Long? = null,
    val amountMl: Int? = null,
    val side: NursingSide? = null,
    val diaperKind: DiaperKind? = null,
    val note: String? = null,
    val createdBy: String,
    val updatedAt: Long,
    /** Soft-delete tombstone: sync needs to propagate deletions, never hard-delete. */
    val deleted: Boolean = false,
) {
    val isRunning: Boolean get() = endedAt == null

    /** Duration for events that have one. Running events have no total yet. */
    val durationMs: Long? get() = endedAt?.let { it - startedAt }
}
