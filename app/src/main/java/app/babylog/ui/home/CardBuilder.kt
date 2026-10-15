package app.babylog.ui.home

import app.babylog.domain.BabyEvent
import app.babylog.domain.EVENT_DISPLAY_ORDER
import app.babylog.domain.EventType
import app.babylog.domain.dayRange
import app.babylog.domain.sortedNewestFirst
import java.time.ZoneId

/**
 * The shared display order, kept under this name so the card code reads locally. The order itself lives
 * in the domain (`EVENT_DISPLAY_ORDER`) because History uses it too — one list, so the two screens
 * cannot drift apart.
 */
internal val CARD_ORDER = EVENT_DISPLAY_ORDER

/**
 * How many of today's entries a card lists before "See all" takes over.
 *
 * Five, and today's five: the card answers "what has happened today", so it is not padded with
 * yesterday's feeds to reach the count. `See all` is the escape hatch for everything else.
 */
internal const val CARD_TODAY_LIMIT = 5

/**
 * Derives the six cards from one flat, newest-first event list. Pure, so the ordering and
 * "latest per type" rules are testable without a database or a screen — which matters because those
 * rules are what the user actually reads at 3am.
 *
 * [childOrder] is only the tie-break for identical timestamps (see `domain/EventOrder.kt`); it is
 * passed in rather than derived because the card builder has no business knowing what a Child is.
 */
internal fun buildCards(all: List<BabyEvent>, childOrder: List<String> = emptyList()): List<CardUi> {
    // Sort here rather than trusting the caller. The DAO does return newest-first, but a wrong "last
    // event" is invisible in review and wrong on the card, and the input is a few dozen rows — so
    // order-independence is worth more than the negligible sort. A test asserts both orderings agree.
    val ordered = all.sortedNewestFirst(childOrder)
    return CARD_ORDER.map { type ->
        val forType = ordered.filter { it.type == type }
        CardUi(
            type = type,
            last = forType.firstOrNull(),
            // Every entry of this activity, newest first: the card draws today's slice of it and
            // "See all" draws the rest. Feeds that need to agree therefore read from ONE list.
            entries = forType,
            // A running event is by definition the most recent one of its type, since the list is
            // sorted - but find it explicitly rather than assuming.
            running = forType.firstOrNull { it.isRunning },
        )
    }
}

/** What a card actually draws: today's entries, and whether anything is left for "See all". */
internal data class CardRows(val shown: List<BabyEvent>, val hasMore: Boolean)

/**
 * Whether a card shows its `See all` link.
 *
 * As soon as the activity has ONE entry, for every card. The rule used to be "an entry is not already on
 * the card", which left the link invisible whenever today's few rows happened to be everything — exactly
 * when a parent wants the full list, and the reason the Growth card had no route to its own data. The
 * owner's call: the same rule for all cards.
 */
internal fun showsFooter(entryCount: Int): Boolean = entryCount > 0

/**
 * The card's row list: today's entries, newest first, capped at [limit].
 *
 * Two rules, both of which the previous `take(3)` + `drop(1)` pair got wrong:
 *
 *  1. It INCLUDES the entry the big number shows. The headline used to swallow the newest feed, so a
 *     per-child list and the Both list could never be compared — Second Twin's 14:14 feed was the headline on
 *     Second Twin's card and a row on the Both card, and its 13:09 feed looked missing.
 *  2. It is TODAY only. Rows used to be "the last three of this type, ever", printed with a bare clock
 *     time, so a feed from yesterday was indistinguishable from one an hour ago.
 *
 * A day with four entries shows four; yesterday is never padding. `hasMore` is true whenever at least
 * one entry of this activity is not on the card — today's overflow, or anything older — which is
 * exactly when "See all" has something to show.
 *
 * `now` is the screen's ticking clock and `zone` the device's, because "today" is a local calendar day:
 * computing it in SQLite would use UTC and file a late-evening feed under the wrong day.
 */
internal fun cardRows(
    entries: List<BabyEvent>,
    now: Long,
    zone: ZoneId,
    limit: Int = CARD_TODAY_LIMIT,
): CardRows {
    val today = dayRange(now, zone, 1)
    val shown = entries.filter { it.startedAt >= today.from && it.startedAt < today.to }.take(limit)
    return CardRows(shown = shown, hasMore = entries.size > shown.size)
}
