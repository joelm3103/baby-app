package app.babylog.data.growth

/**
 * The seam between the app and wherever a newer growth dataset is hosted.
 *
 * Two reads, no writes: the client never uploads a dataset, it only takes one down. Firestore is the
 * first implementation; tests use a fake, the same way sync does.
 */
interface GrowthDatasetGateway {
    /** The small descriptor. Fetched first, so a bad or stale descriptor costs one round trip. */
    suspend fun fetchManifest(): GrowthDatasetManifest

    /** The full dataset JSON, as a string. Verified against the manifest's sha256 before parsing. */
    suspend fun fetchDataset(): String
}