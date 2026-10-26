package app.babylog.data.growth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.babylog.data.growth.GrowthMeasure
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrowthDatasetRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var bundled: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = File(context.cacheDir, "growth-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(tempDir, "growth.preferences_pb") }
        bundled = minimalDataset("who-2006.2026.10", "2026-10-15")
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
        File(context.filesDir, "growth").deleteRecursively()
    }

    @Test
    fun `a newer dataset with a matching checksum replaces the bundled one`() = runBlocking {
        val updatedJson = minimalDataset("who-2006.2026.12", "2026-12-01")
        val gateway = FakeGrowthDatasetGateway(
            manifest = GrowthDatasetManifest("who-2006.2026.12", sha256(updatedJson), "2026-12-01"),
            dataset = updatedJson,
        )
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertEquals("WHO 2006 · v2026.10 · updated 15 Oct 2026", repo.current().identityLine)
        val result = repo.checkForUpdates(true)
        assertTrue(result is GrowthDatasetUpdateResult.Updated)
        assertEquals("who-2006.2026.12", (result as GrowthDatasetUpdateResult.Updated).version)
        assertEquals("WHO 2006 · v2026.12 · updated 1 Dec 2026", repo.current().identityLine)
    }

    @Test
    fun `a stale version is not applied`() = runBlocking {
        val gateway = FakeGrowthDatasetGateway(
            manifest = GrowthDatasetManifest("who-2006.2026.09", sha256(minimalDataset("who-2006.2026.09", "2026-09-01")), "2026-09-01"),
            dataset = minimalDataset("who-2006.2026.09", "2026-09-01"),
        )
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertEquals(GrowthDatasetUpdateResult.NoUpdate, repo.checkForUpdates(true))
        assertEquals("who-2006.2026.10", repo.current().version)
    }

    @Test
    fun `a checksum mismatch keeps the bundled dataset`() = runBlocking {
        val updatedJson = minimalDataset("who-2006.2026.12", "2026-12-01")
        val gateway = FakeGrowthDatasetGateway(
            manifest = GrowthDatasetManifest("who-2006.2026.12", "0".repeat(64), "2026-12-01"),
            dataset = updatedJson,
        )
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertEquals(GrowthDatasetUpdateResult.Failed, repo.checkForUpdates(true))
        assertEquals("who-2006.2026.10", repo.current().version)
    }

    @Test
    fun `an unreachable gateway keeps the bundled dataset`() = runBlocking {
        val gateway = FakeGrowthDatasetGateway(fail = true)
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertEquals(GrowthDatasetUpdateResult.Failed, repo.checkForUpdates(true))
        assertEquals("who-2006.2026.10", repo.current().version)
    }

    @Test
    fun `a corrupt cached file falls back to the bundled dataset`() = runBlocking {
        File(context.filesDir, "growth").mkdirs()
        File(context.filesDir, "growth/updated.json").writeText("{not json")
        store.edit { }
        // seed preferences pointing at the corrupt file
        store.edit { prefs ->
            prefs[androidx.datastore.preferences.core.stringPreferencesKey("growth.dataset.version")] = "who-2006.2026.12"
            prefs[androidx.datastore.preferences.core.stringPreferencesKey("growth.dataset.sha256")] = "a".repeat(64)
        }
        val repo = GrowthDatasetRepository(context, store, null, bundled)
        assertEquals("who-2006.2026.10", repo.current().version)
    }

    @Test
    fun `an accepted update publishes the new identity line`() = runBlocking {
        // What the phone caught in Phase 13.5: Settings read the identity line once, on entry, so a
        // successful check printed `Updated to v2026.12` directly above a stale `v2026.10` line until
        // the screen was left and re-entered. The line is now observed, so a collector sees the change.
        val updatedJson = minimalDataset("who-2006.2026.12", "2026-12-01")
        val gateway = FakeGrowthDatasetGateway(
            manifest = GrowthDatasetManifest("who-2006.2026.12", sha256(updatedJson), "2026-12-01"),
            dataset = updatedJson,
        )
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertNull(repo.identityLine.value)
        repo.current()
        assertEquals("WHO 2006 · v2026.10 · updated 15 Oct 2026", repo.identityLine.value)
        assertTrue(repo.checkForUpdates(true) is GrowthDatasetUpdateResult.Updated)
        assertEquals("WHO 2006 · v2026.12 · updated 1 Dec 2026", repo.identityLine.value)
    }

    private fun minimalDataset(version: String, retrievedAt: String): String {
        val row = "[0.0,0.3487,3.3464,0.14602]"
        val measure = """{"1":[$row],"2":[$row]}"""
        val measures = GrowthMeasure.entries.joinToString(",") { "\"${it.name}\":$measure" }
        val files = GrowthMeasure.entries.joinToString(",") {
            """{"name":"x.txt","measure":"${it.name}","axis":"AGE_DAYS","rowsPerSex":1,"sha256":"${"0".repeat(64)}","url":"u"}"""
        }
        return """{"dataset":"WHO Child Growth Standards","version":"$version","publishedAt":"2006-04-27","retrievedAt":"$retrievedAt","citation":"c","license":"l","sexCodes":{},"measures":{$measures},"files":[$files]}"""
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

class FakeGrowthDatasetGateway(
    private val manifest: GrowthDatasetManifest? = null,
    private val dataset: String? = null,
    private val fail: Boolean = false,
) : GrowthDatasetGateway {
    override suspend fun fetchManifest(): GrowthDatasetManifest {
        if (fail) throw IllegalStateException("offline")
        return manifest ?: throw IllegalStateException("no manifest")
    }

    override suspend fun fetchDataset(): String {
        if (fail) throw IllegalStateException("offline")
        return dataset ?: throw IllegalStateException("no dataset")
    }
}
