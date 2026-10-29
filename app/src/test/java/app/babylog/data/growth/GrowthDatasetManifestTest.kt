package app.babylog.data.growth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The manifest's parse rules, which are the app's only reading of what the server is asking for.
 *
 * The flag this pins is the exception rather than the rule: a withdrawal is published by hand in the
 * Firestore console, so the client has to keep accepting the three documents that already exist — every one
 * of them has no `withdrawn` field at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrowthDatasetManifestTest {

    private val sha = "2df3475" + "0".repeat(57)

    @Test
    fun `a manifest published before the flag existed still parses, and is not a retraction`() {
        // Exactly the shape docs/firebase-setup.md tells the owner to publish: three fields, no more.
        val manifest = GrowthDatasetManifest.parse(
            """{"datasetVersion":"who-2006.2026.10","sha256":"$sha","retrievedAt":"2026-10-15"}""",
        )

        assertEquals("who-2006.2026.10", manifest.datasetVersion)
        assertEquals(sha, manifest.sha256)
        assertEquals("2026-10-15", manifest.retrievedAt)
        assertFalse("an absent flag is not a withdrawal", manifest.withdrawn)

        // `false` written out is a different parse path from an absent key, and must agree with it.
        val explicitFalse = GrowthDatasetManifest.parse(
            """{"datasetVersion":"who-2006.2026.10","sha256":"$sha","retrievedAt":"2026-10-15","withdrawn":false}""",
        )
        assertFalse(explicitFalse.withdrawn)
    }

    @Test
    fun `a withdrawn manifest is read as the retraction it is`() {
        val manifest = GrowthDatasetManifest.parse(
            """{"datasetVersion":"who-2006.2026.11","sha256":"$sha","retrievedAt":"2026-11-01","withdrawn":true}""",
        )

        assertTrue(manifest.withdrawn)
        // The version still travels: it is the one thing that says WHICH dataset is being recalled.
        assertEquals("who-2006.2026.11", manifest.datasetVersion)
    }
}
