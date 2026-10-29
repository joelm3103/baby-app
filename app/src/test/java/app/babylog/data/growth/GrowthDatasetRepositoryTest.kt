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
import kotlinx.coroutines.flow.first
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

    // ---- withdrawal ----------------------------------------------------------------------------
    // `PLAN.md` recorded this gap on 2026-10-26: `isNewer()` refuses anything that is not newer, so a
    // reference a phone had accepted could be superseded but never recalled, and the only rescue was
    // clearing the app's data. These four cases pin the behaviour, including the one the note names as
    // needing a force-stop — the parsed reference held in memory.

    @Test
    fun `a withdrawn dataset reverts to the bundled one and clears what was downloaded`() = runBlocking {
        val gateway = FakeGrowthDatasetGateway()
        val downloaded = minimalDataset("who-2006.2026.12", "2026-12-01")
        gateway.manifest = GrowthDatasetManifest("who-2006.2026.12", sha256(downloaded), "2026-12-01")
        gateway.dataset = downloaded
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertTrue(repo.checkForUpdates(true) is GrowthDatasetUpdateResult.Updated)
        assertEquals("who-2006.2026.12", repo.current().version)
        assertTrue("the accepted dataset is cached on disk", cacheFile().exists())
        assertEquals("who-2006.2026.12", cachedVersion())

        gateway.manifest = GrowthDatasetManifest("who-2006.2026.12", sha256(downloaded), "2026-12-01", withdrawn = true)

        // A SECOND instance, so this proves the disk side: a fresh process reads the cache file, not memory.
        val fresh = GrowthDatasetRepository(context, store, gateway, bundled)
        val result = fresh.checkForUpdates(true)
        assertEquals(GrowthDatasetUpdateResult.Withdrawn("who-2006.2026.12"), result)
        assertEquals("who-2006.2026.10", fresh.current().version)
        assertEquals("WHO 2006 · v2026.10 · updated 15 Oct 2026", fresh.identityLine.value)
        assertTrue("the cache file must be deleted", !cacheFile().exists())
        assertNull("both bookkeeping keys must be cleared", cachedVersion())
    }

    @Test
    fun `a withdrawal reverts the reference this process is already holding`() = runBlocking {
        // The trap the 2026-10-26 note documents: restoring the shipped reference by hand needed a
        // force-stop, because `cachedReference` lives for the life of the process. A withdrawal must not.
        val gateway = FakeGrowthDatasetGateway()
        val downloaded = minimalDataset("who-2006.2026.12", "2026-12-01")
        gateway.manifest = GrowthDatasetManifest("who-2006.2026.12", sha256(downloaded), "2026-12-01")
        gateway.dataset = downloaded
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertTrue(repo.checkForUpdates(true) is GrowthDatasetUpdateResult.Updated)

        gateway.manifest = GrowthDatasetManifest("who-2006.2026.12", sha256(downloaded), "2026-12-01", withdrawn = true)
        assertTrue(repo.checkForUpdates(true) is GrowthDatasetUpdateResult.Withdrawn)

        assertEquals("no restart required", "who-2006.2026.10", repo.current().version)
        assertEquals("WHO 2006 · v2026.10 · updated 15 Oct 2026", repo.identityLine.value)
    }

    @Test
    fun `a withdrawal for a dataset this phone never accepted changes nothing`() = runBlocking {
        val gateway = FakeGrowthDatasetGateway(
            manifest = GrowthDatasetManifest("who-2006.2026.12", "0".repeat(64), "2026-12-01", withdrawn = true),
        )
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)

        assertEquals(GrowthDatasetUpdateResult.NoUpdate, repo.checkForUpdates(true))
        assertEquals("who-2006.2026.10", repo.current().version)
        assertEquals("WHO 2006 · v2026.10 · updated 15 Oct 2026", repo.identityLine.value)
    }

    @Test
    fun `a withdrawal recalls only the version it names, not a newer one this phone holds`() = runBlocking {
        val gateway = FakeGrowthDatasetGateway()
        val downloaded = minimalDataset("who-2006.2026.12", "2026-12-01")
        gateway.manifest = GrowthDatasetManifest("who-2006.2026.12", sha256(downloaded), "2026-12-01")
        gateway.dataset = downloaded
        val repo = GrowthDatasetRepository(context, store, gateway, bundled)
        assertTrue(repo.checkForUpdates(true) is GrowthDatasetUpdateResult.Updated)

        // The server retracts the November dataset; this phone moved past it to December.
        gateway.manifest = GrowthDatasetManifest("who-2006.2026.11", "0".repeat(64), "2026-11-01", withdrawn = true)

        assertEquals(GrowthDatasetUpdateResult.NoUpdate, repo.checkForUpdates(true))
        assertEquals("who-2006.2026.12", repo.current().version)
        assertEquals("who-2006.2026.12", cachedVersion())
    }

    @Test
    fun `every result has one sentence, and the withdrawal names the revert`() {
        assertEquals("Updated to who-2006.2026.12", GrowthDatasetUpdateResult.Updated("who-2006.2026.12").message())
        assertEquals(
            "Dataset withdrawn — back to the bundled reference",
            GrowthDatasetUpdateResult.Withdrawn("who-2006.2026.12").message(),
        )
        assertEquals("Already up to date", GrowthDatasetUpdateResult.NoUpdate.message())
        assertEquals("Could not check", GrowthDatasetUpdateResult.Failed.message())
    }

    private fun cacheFile(): File = File(context.filesDir, "growth/updated.json")

    private suspend fun cachedVersion(): String? =
        store.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("growth.dataset.version")]

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
    // `var`: a withdrawal test has to change what the server says between two checks on one repository.
    var manifest: GrowthDatasetManifest? = null,
    var dataset: String? = null,
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
