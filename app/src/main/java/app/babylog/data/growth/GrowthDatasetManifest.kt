package app.babylog.data.growth

import org.json.JSONObject

/**
 * What the server says about a newer growth dataset.
 *
 * The manifest is deliberately small: it identifies the dataset and carries the checksum the client
 * verifies against the payload, but never the tables themselves. A manifest that fails any of those
 * checks is ignored, and the bundled dataset stays.
 *
 * [withdrawn] is the one instruction that is not a version comparison: a dataset a phone has already
 * accepted can be recalled by publishing a manifest that names it and says so. `PLAN.md` records the gap
 * this closes — before it, a bad reference could be superseded but never taken back, and the only rescue
 * was clearing the app's data. It is optional and defaults to false, so every manifest published before
 * 2026-10-28 keeps parsing exactly as it did.
 */
data class GrowthDatasetManifest(
    val datasetVersion: String,
    val sha256: String,
    val retrievedAt: String,
    val withdrawn: Boolean = false,
) {
    companion object {
        fun parse(json: String): GrowthDatasetManifest {
            val root = JSONObject(json)
            val version = root.optString("datasetVersion").trim()
            val sha = root.optString("sha256").trim().lowercase()
            val retrievedAt = root.optString("retrievedAt").trim()
            // Read with a default rather than `get`: a retraction is the exception, and a document that
            // predates the flag must not start failing its checks over a field nobody wrote.
            val withdrawn = root.optBoolean("withdrawn", false)
            require(version.isNotBlank()) { "manifest has no datasetVersion" }
            require(sha.matches(Regex("[0-9a-f]{64}"))) { "manifest sha256 is not a sha-256 hex string" }
            require(retrievedAt.isNotBlank()) { "manifest has no retrievedAt" }
            return GrowthDatasetManifest(version, sha, retrievedAt, withdrawn)
        }
    }
}