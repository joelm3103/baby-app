package app.babylog.domain

/**
 * One row's version, as the merge sees it: how recently it changed, and whether it is a tombstone.
 *
 * Deliberately not the entity itself. The decision only ever needs these two numbers, and keeping it
 * that way means the merge is a pure function that a JVM test can drive without Room, Firestore or an
 * emulator.
 */
data class SyncRow(val updatedAt: Long, val deleted: Boolean)

/** What the receiving phone should do with one incoming document. */
enum class SyncVerdict {
    /** The server's copy is the newer truth; write it locally. */
    TAKE_REMOTE,

    /** The local copy is newer (or the only survivor of a tie); queue it for upload instead. */
    KEEP_LOCAL,

    /** Same version and same tombstone state; there is nothing to write. */
    IDENTICAL,
}

/**
 * Reconciling one row: **later `updatedAt` wins, and a deletion wins a tie**.
 *
 * Both halves of that sentence are load-bearing.
 *
 * - `updatedAt` and not arrival order, because the two phones are offline-first: the write that arrives
 *   last may well be the older edit, sent from a phone that was in airplane mode.
 * - A deletion wins a tie, because the alternative is a resurrection: two phones that deleted and
 *   re-saved within the same millisecond would otherwise flip a tombstone back to a live row, and a
 *   deleted bottle would silently reappear in the log with no one having logged it.
 *
 * A row that does not exist locally yet is always taken — including when it arrives already deleted,
 * which the caller resolves by doing nothing at all (see `SyncRepository`): there is nothing to delete,
 * so a tombstone for an unknown id is not an instruction to invent one.
 */
fun resolveSync(local: SyncRow?, remote: SyncRow): SyncVerdict = when {
    local == null -> SyncVerdict.TAKE_REMOTE
    remote.updatedAt > local.updatedAt -> SyncVerdict.TAKE_REMOTE
    remote.updatedAt < local.updatedAt -> SyncVerdict.KEEP_LOCAL
    // Equal timestamps from here down.
    remote.deleted && !local.deleted -> SyncVerdict.TAKE_REMOTE
    local.deleted && !remote.deleted -> SyncVerdict.KEEP_LOCAL
    else -> SyncVerdict.IDENTICAL
}
