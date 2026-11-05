package app.babylog.domain

import kotlin.math.roundToInt

/**
 * What one feed's two typed numbers become (Phase 26).
 *
 * [consumedMl] is what the baby DRANK and is what `amountMl` stores; [leftBehindMl] is what was abandoned
 * and is a sibling fact, not a second reading of the same number. `pouredMl` is derived, never stored —
 * which is what makes the pair lossless: a parent who poured 6 fl oz and left 1 gets 5 + 1 back exactly.
 *
 * Both null is a feed with no amount at all, which the sheet still allows: it must not invent a volume for
 * a parent who did not measure one.
 */
data class BottleAmount(
    val consumedMl: Int?,
    val leftBehindMl: Int?,
) {
    /** What went into the bottle: drunk plus left. Null when nothing was measured. */
    val pouredMl: Int? get() = consumedMl?.let { it + (leftBehindMl ?: 0) }
}

/**
 * The stored pair, from the two volumes the sheet has already resolved to millilitres.
 *
 * **This is the one place the subtraction happens.** The sheet resolves its two texts (in the parent's
 * unit, with the untouched-prefill guard applied), this turns them into what is written, and nothing at
 * read time ever evaluates an arithmetic expression nobody stored.
 *
 * Null means "this pair is not a feed", and there are exactly two ways to earn it:
 *  - a leftover with no amount beside it — the amount field is what the leftover is *of*, so half the pair
 *    is not a record;
 *  - a leftover LARGER than what was poured, which is not something a bottle can do.
 *
 * A leftover EQUAL to the amount is legal on purpose: a bottle made and then refused is a real event, it
 * stores zero drunk, and it is exactly the feed a wastage report exists to show. Negative volumes cannot
 * be typed (`decimalInput` strips the sign) and are rejected here anyway rather than trusted.
 */
fun bottleAmount(pouredMl: Double?, leftBehindMl: Double?): BottleAmount? {
    if (pouredMl == null) {
        return if (leftBehindMl == null) BottleAmount(null, null) else null
    }
    if (pouredMl < 0) return null
    if (leftBehindMl == null) return BottleAmount(pouredMl.roundToInt(), null)
    if (leftBehindMl < 0 || leftBehindMl > pouredMl) return null
    return BottleAmount(
        consumedMl = (pouredMl - leftBehindMl).roundToInt(),
        // Rounded ONCE, from the millilitres, and kept as the parent recorded it: the display unit's own
        // rounding is what the field showed, so "1 oz" is 30 ml here rather than 29.57.
        leftBehindMl = leftBehindMl.roundToInt(),
    )
}
