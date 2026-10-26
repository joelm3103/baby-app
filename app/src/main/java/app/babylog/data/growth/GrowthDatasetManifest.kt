package app.babylog.data.growth

import org.json.JSONObject

/**
 * What the server says about a newer growth dataset.
 *
 * The manifest is deliberately small: it identifies the dataset and carries the checksum the client
 * verifies against the payload, but never the tables themselves. A manifest that fails any of those
 * checks is ignored, and the bundled dataset stays.
 */
data class GrowthDatasetManifest(
    val datasetVersion: String,
    val sha256: String,
    val retrievedAt: String,
) {
    companion object {
        fun parse(json: String): GrowthDatasetManifest {
            val root = JSONObject(json)
            val version = root.optString("datasetVersion").trim()
            val sha = root.optString("sha256").trim().lowercase()
            val retrievedAt = root.optString("retrievedAt").trim()
            require(version.isNotBlank()) { "manifest has no datasetVersion" }
            require(sha.matches(Regex("[0-9a-f]{64}"))) { "manifest sha256 is not a sha-256 hex string" }
            require(retrievedAt.isNotBlank()) { "manifest has no retrievedAt" }
            return GrowthDatasetManifest(version, sha, retrievedAt)
        }
    }
}