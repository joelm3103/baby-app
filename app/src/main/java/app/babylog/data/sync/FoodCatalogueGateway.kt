package app.babylog.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * The solids catalogue's two read surfaces, behind one seam (ADR-0002).
 *
 * Reads only: it is separate from `SyncGateway` because the shapes are different. The catalogue is not a
 * set of rows to merge — one document is the owner's list, and the other is a list of additions — so
 * nothing here has an `updatedAt` to compare or a tombstone to respect.
 *
 * Firestore is the only implementation; tests use a fake, the same way sync does. A build with no Firebase
 * configuration gets null and is the single-phone logger it already is, with the bundled asset and nothing
 * else.
 */
interface FoodCatalogueGateway {
    /**
     * The published catalogue, as snapshots of one document.
     *
     * A flow rather than a one-shot fetch: the owner publishes from the console, and a phone that is
     * already running should pick a rename up without being restarted. The first emission is the current
     * document, so an attach needs no separate fetch.
     */
    fun observeCatalogue(): Flow<PublishedCatalogue>

    /** Every pending addition, as the whole collection per snapshot. */
    fun observePending(): Flow<List<PendingFood>>
}

/**
 * The catalogue as it exists with no server: nothing published, nothing pending.
 *
 * Used by tests of the local-only paths and as the honest default, so a caller never has to branch on
 * "is there a gateway" to describe an empty catalogue.
 */
val NoPublishedCatalogue = PublishedCatalogue(version = "", foods = emptyList())
