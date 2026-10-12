package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.EventType

/**
 * Fixed order, by how often each is used. Sleep is LAST and present: the owner rarely tracks it but asked
 * for the card to exist for the occasional night he does. Do not "optimise" it away.
 */
internal val CARD_ORDER = listOf(
    EventType.BOTTLE,
    EventType.DIAPER,
    EventType.SOLIDS,
    EventType.GROWTH,
    EventType.HEALTH,
    EventType.SLEEP,
)

/** How many recent entries a card shows under its value. */
internal const val CARD_RECENT_LIMIT = 3

/**
 * Derives the six cards from one flat, newest-first event list. Pure, so the ordering and
 * "latest per type" rules are testable without a database or a screen — which matters because those
 * rules are what the user actually reads at 3am.
 */
internal fun buildCards(all: List<BabyEvent>): List<CardUi> {
    // Sort here rather than trusting the caller. The DAO does return newest-first, but a wrong "last
    // event" is invisible in review and wrong on the card, and the input is a few dozen rows — so
    // order-independence is worth more than the negligible sort. A test asserts both orderings agree.
    val ordered = all.sortedByDescending { it.startedAt }
    return CARD_ORDER.map { type ->
    val forType = ordered.filter { it.type == type }
    CardUi(
        type = type,
        last = forType.firstOrNull(),
        recent = forType.take(CARD_RECENT_LIMIT),
        // A running event is by definition the most recent one of its type, since the DAO sorts by
        // startedAt descending - but find it explicitly rather than assuming.
        running = forType.firstOrNull { it.isRunning },
    )
    }
}
