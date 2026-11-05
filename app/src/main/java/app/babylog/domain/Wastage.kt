package app.babylog.domain

import kotlin.math.roundToInt

/**
 * How much was poured, how much was drunk, and how much went down the sink (Phase 26).
 *
 * One implementation, two surfaces: Summary's Bottle total card reads the rows, and Trends' Bottle tab
 * draws the card. A second computation for the chart would be a second answer to the same question, and the
 * first time they disagreed a parent would have no way to tell which one was right.
 *
 * Every figure is canonical millilitres, because storage is canonical and the unit choice is a display
 * concern. A feed with no amount contributes nothing — the app must not invent a volume for a record that
 * has none, which is the same rule that makes an amount-less bottle say `No amount recorded` rather than
 * repeating its own title.
 */
data class WastageLine(
    val childId: String,
    val childName: String,
    /** What was drunk: the sum of `amountMl`. */
    val consumedMl: Int,
    /** What was left: the sum of `leftBehindMl`, with "not recorded" read as nothing left. */
    val wastedMl: Int,
    val feeds: Int,
    /** Feeds that recorded a leftover — the count behind the number, not a share of anything. */
    val feedsWithLeftover: Int,
) {
    /** What went into the bottles. Derived, never stored: drunk plus left is exactly what was poured. */
    val pouredMl: Int get() = consumedMl + wastedMl

    /**
     * The share of the poured volume that was abandoned, or null when nothing was poured.
     *
     * Null rather than 0 on an empty range on purpose: "0% wasted" is a claim about a bottle that does not
     * exist, and a report that makes it up is worse than one that says nothing.
     */
    val wastedShare: Double? get() = if (pouredMl == 0) null else wastedMl.toDouble() / pouredMl
}

/** A range's wastage, per child, with the window's length so an average can be divided honestly. */
data class WastageReport(
    val lines: List<WastageLine>,
    /** Days (or weeks, or months) in the window. An empty bucket counts as zero — the Trends rule. */
    val days: Int,
) {
    val consumedMl: Int get() = lines.sumOf { it.consumedMl }
    val wastedMl: Int get() = lines.sumOf { it.wastedMl }
    val pouredMl: Int get() = consumedMl + wastedMl
    val feeds: Int get() = lines.sumOf { it.feeds }
    val feedsWithLeftover: Int get() = lines.sumOf { it.feedsWithLeftover }
    val wastedShare: Double? get() = if (pouredMl == 0) null else wastedMl.toDouble() / pouredMl

    /** Wasted per bucket of the window, over every bucket — a day off the log pulls it down, not up. */
    val averageWastedPerBucket: Double get() = wastedMl.toDouble() / days.coerceAtLeast(1)

    /** False when the range holds no bottle at all, which is when the report has nothing to say. */
    val hasData: Boolean get() = feeds > 0
}

/**
 * [events] must already be scoped to the range and to the children in view; [children] is the scope, so a
 * child with no bottle in the range still gets a zero line rather than vanishing from a Both-mode card.
 */
fun wastageReport(events: List<BabyEvent>, children: List<Child>, days: Int = 1): WastageReport {
    val bottles = events.filter { it.type == EventType.BOTTLE && !it.deleted }
    return WastageReport(
        lines = children.map { child ->
            val mine = bottles.filter { it.childId == child.id }
            WastageLine(
                childId = child.id,
                childName = child.name,
                consumedMl = mine.sumOf { it.amountMl ?: 0 },
                wastedMl = mine.sumOf { it.leftBehindOrZero },
                feeds = mine.size,
                feedsWithLeftover = mine.count { it.leftBehindOrZero > 0 },
            )
        },
        days = days.coerceAtLeast(1),
    )
}

/**
 * A wastage share as the number a parent reads: `7%`.
 *
 * A share that is real but rounds away reads `<1%`, never `0%` — the same rule Trends follows for an
 * average that is genuinely there, and the one that stops "we waste nothing" being printed over a log that
 * says otherwise. A null share (nothing poured) is a dash.
 */
fun wastedPercentText(share: Double?): String {
    if (share == null) return "\u2014"
    val percent = (share * 100).roundToInt()
    return when {
        percent == 0 && share > 0 -> "<1%"
        else -> "$percent%"
    }
}
