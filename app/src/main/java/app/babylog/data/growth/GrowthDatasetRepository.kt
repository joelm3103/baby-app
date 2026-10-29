package app.babylog.data.growth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface GrowthDatasetUpdateResult {
    data object NoUpdate : GrowthDatasetUpdateResult
    data class Updated(val version: String) : GrowthDatasetUpdateResult

    /**
     * The server retracted [version], which this phone was reading, so it is back on the bundled reference.
     *
     * A distinct result rather than [NoUpdate], because something did change and the screen has to say so: a
     * percentile that moves after a silent reference change is the surprise this closes.
     */
    data class Withdrawn(val version: String) : GrowthDatasetUpdateResult
    data object Failed : GrowthDatasetUpdateResult
}

/**
 * What the check row says for [this] result.
 *
 * One function rather than a `when` per screen. Growth and Settings both run this check, and a second copy is
 * how the two rows start disagreeing about the same result — the same reasoning that keeps the next reminder
 * in one place. The identity line beside the row is not repeated here: it already names the version.
 */
fun GrowthDatasetUpdateResult.message(): String = when (this) {
    is GrowthDatasetUpdateResult.Updated -> "Updated to $version"
    is GrowthDatasetUpdateResult.Withdrawn -> "Dataset withdrawn — back to the bundled reference"
    GrowthDatasetUpdateResult.NoUpdate -> "Already up to date"
    GrowthDatasetUpdateResult.Failed -> "Could not check"
}

/**
 * Owns which growth dataset this app reads: the bundled one, or a newer one fetched and cached.
 *
 * The bundled dataset is always the fallback. A fetch that fails for any reason — network, rules,
 * a stale manifest, a wrong checksum, a parse error — leaves the current reference untouched and
 * returns [GrowthDatasetUpdateResult.Failed] without throwing, so an automatic 90-day check can be
 * silent.
 */
class GrowthDatasetRepository(
    private val context: android.content.Context,
    private val dataStore: DataStore<Preferences>,
    private val gateway: GrowthDatasetGateway?,
    private val bundledJson: String,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val cacheDir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
    private val cacheFile = File(cacheDir, CACHE_FILE)

    private var cachedReference: GrowthReference? = null

    private val _identityLine = MutableStateFlow<String?>(null)

    /**
     * The identity line of the reference currently in use, so the screen that shows it can observe an
     * accepted update instead of re-reading it on entry.
     *
     * The phone found this the hard way in Phase 13.5: Settings read the line inside a `produceState`
     * keyed on the destination, so a successful check printed `Updated to v2026.11` directly above a
     * line still saying `v2026.10` until the parent left the screen and came back.
     */
    val identityLine: StateFlow<String?> = _identityLine.asStateFlow()

    suspend fun current(): GrowthReference =
        (cachedReference ?: withContext(Dispatchers.IO) {
            val fromCache = readCached()
            (fromCache ?: GrowthReferenceParser.parse(bundledJson)).also { cachedReference = it }
        }).also { _identityLine.value = it.identityLine }

    suspend fun checkForUpdates(force: Boolean): GrowthDatasetUpdateResult {
        val gateway = gateway ?: return GrowthDatasetUpdateResult.Failed
        if (!force) {
            val last = runCatching { dataStore.data.first()[KEY_LAST_CHECKED] }.getOrNull() ?: 0L
            if (last > 0 && ChronoUnit.DAYS.between(
                    java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                    java.time.Instant.ofEpochMilli(clock.millis()).atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                ) < SILENT_INTERVAL_DAYS) {
                return GrowthDatasetUpdateResult.NoUpdate
            }
        }

        return try {
            val manifest = gateway.fetchManifest()
            if (manifest.withdrawn) {
                // A retraction is not a version comparison: `isNewer` would refuse it for naming the same
                // version this phone already holds, which is precisely the case it exists for.
                withdraw(manifest)
            } else {
                val current = current()
                if (!isNewer(manifest, current)) {
                    markChecked()
                    GrowthDatasetUpdateResult.NoUpdate
                } else {
                    val json = gateway.fetchDataset()
                    if (sha256(json) != manifest.sha256) {
                        GrowthDatasetUpdateResult.Failed
                    } else {
                        val parsed = GrowthReferenceParser.parse(json)
                        writeCache(json, manifest)
                        cachedReference = parsed
                        _identityLine.value = parsed.identityLine
                        markChecked()
                        GrowthDatasetUpdateResult.Updated(parsed.version)
                    }
                }
            }
        } catch (t: Throwable) {
            GrowthDatasetUpdateResult.Failed
        }
    }

    /**
     * The server has retracted [manifest]'s dataset. Drop the cached copy and go back to the bundled one.
     *
     * Three things have to go, not one: the cache file, the two DataStore keys that remember what was
     * downloaded, and [cachedReference] — the parsed reference is held in memory for the life of the process,
     * which is why restoring the shipped reference by hand used to need a force-stop (`PLAN.md`, 2026-10-26).
     *
     * Only the version the manifest NAMES is recalled. A notice for a dataset this phone never accepted, or
     * for an older one it has since superseded, is a no-op: there is nothing here to take back, and reverting a
     * newer reference because an older one was retracted would be the app inventing an instruction.
     *
     * `markChecked()` runs either way, so a retraction that does not apply cannot make the next manual check
     * re-fetch the manifest in a loop.
     */
    private suspend fun withdraw(manifest: GrowthDatasetManifest): GrowthDatasetUpdateResult {
        val cleared = withContext(Dispatchers.IO) {
            val prefs = runCatching { dataStore.data.first() }.getOrNull()
            if (prefs?.get(KEY_VERSION) != manifest.datasetVersion) return@withContext false
            deleteCache()
            runCatching {
                dataStore.edit {
                    it.remove(KEY_VERSION)
                    it.remove(KEY_SHA)
                }
            }
            true
        }
        markChecked()
        if (!cleared) return GrowthDatasetUpdateResult.NoUpdate
        val bundled = GrowthReferenceParser.parse(bundledJson)
        cachedReference = bundled
        _identityLine.value = bundled.identityLine
        return GrowthDatasetUpdateResult.Withdrawn(manifest.datasetVersion)
    }

    /** Convenience for the 90-day silent check on app start. Never throws. */
    suspend fun checkIfDue() {
        runCatching { checkForUpdates(force = false) }
    }

    private fun isNewer(manifest: GrowthDatasetManifest, current: GrowthReference): Boolean {
        if (manifest.datasetVersion == current.version) return false
        val manifestDate = runCatching { LocalDate.parse(manifest.retrievedAt) }.getOrNull()
        val currentDate = runCatching { LocalDate.parse(current.retrievedAt) }.getOrNull()
        if (manifestDate != null && currentDate != null) {
            return manifestDate.isAfter(currentDate)
        }
        return manifest.datasetVersion > current.version
    }

    private suspend fun readCached(): GrowthReference? = withContext(Dispatchers.IO) {
        val prefs = runCatching { dataStore.data.first() }.getOrNull() ?: return@withContext null
        val version = prefs[KEY_VERSION] ?: return@withContext null
        val sha = prefs[KEY_SHA] ?: return@withContext null
        if (!cacheFile.exists()) return@withContext null
        val json = runCatching { cacheFile.readText() }.getOrNull() ?: return@withContext null
        if (sha256(json) != sha) {
            deleteCache()
            return@withContext null
        }
        val parsed = runCatching { GrowthReferenceParser.parse(json) }.getOrNull() ?: run {
            deleteCache()
            null
        }
        if (parsed != null && parsed.version != version) {
            deleteCache()
            return@withContext null
        }
        parsed
    }

    private suspend fun writeCache(json: String, manifest: GrowthDatasetManifest) = withContext(Dispatchers.IO) {
        val tmp = File(cacheDir, "$CACHE_FILE.tmp")
        tmp.writeText(json)
        if (cacheFile.exists()) cacheFile.delete()
        tmp.renameTo(cacheFile)
        dataStore.edit {
            it[KEY_VERSION] = manifest.datasetVersion
            it[KEY_SHA] = manifest.sha256
        }
    }

    private suspend fun markChecked() {
        dataStore.edit { it[KEY_LAST_CHECKED] = clock.millis() }
    }

    private fun deleteCache() {
        cacheFile.delete()
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_DIR = "growth"
        private const val CACHE_FILE = "updated.json"
        private const val SILENT_INTERVAL_DAYS = 90L

        private val KEY_VERSION = stringPreferencesKey("growth.dataset.version")
        private val KEY_SHA = stringPreferencesKey("growth.dataset.sha256")
        private val KEY_LAST_CHECKED = longPreferencesKey("growth.dataset.lastChecked")
    }
}
