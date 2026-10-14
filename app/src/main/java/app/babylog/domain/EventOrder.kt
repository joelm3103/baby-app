package app.babylog.domain

/**
 * The one ordering every timeline uses: newest first, with a deterministic tie-break.
 *
 * The tie-break is not hypothetical bookkeeping. A Both-mode save writes one row per child with an
 * **identical** `startedAt`, so `ORDER BY startedAt DESC` on its own leaves their relative order to
 * SQLite's discretion — and the same two feeds can end up swapping places from recomposition to
 * recomposition, which reads as "the app changed my log".
 *
 * Ties are broken by [childOrder] (the order the children are configured in), so First Twin's row precedes
 * Second Twin's, and by `childId` for any child missing from that list — an archived child can still appear in
 * old entries, and the comparator must stay total. Nothing here depends on the input order, so a caller
 * that has already sorted (the DAO does) and one that has not agree.
 */
fun newestFirst(childOrder: List<String> = emptyList()): Comparator<BabyEvent> {
    val rank = childOrder.withIndex().associate { (index, childId) -> childId to index }
    return compareByDescending<BabyEvent> { it.startedAt }
        .thenBy { rank[it.childId] ?: Int.MAX_VALUE }
        .thenBy { it.childId }
}

/**
 * [newestFirst] as an extension, because every call site wants the list and not the comparator.
 * Kotlin's sort is stable, so pre-sorting with this and then grouping (e.g. `groupByDay`) preserves
 * this order inside each day.
 */
fun List<BabyEvent>.sortedNewestFirst(childOrder: List<String> = emptyList()): List<BabyEvent> =
    sortedWith(newestFirst(childOrder))
