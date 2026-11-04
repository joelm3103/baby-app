package app.babylog.data.update

import app.babylog.domain.SOURCE_REPOSITORY_URL
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * How long a check is trusted for.
 *
 * A day, not "every launch": the app is opened a dozen times before lunch, and a request per open would be
 * a small denial of service against GitHub from a phone nobody is watching. It is also the number that makes
 * the About line honest — "Up to date" means "as of the last day", and the row says when it last asked.
 */
const val UPDATE_CHECK_INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

/** One published release, as far as this app cares: which version it is, and where a parent gets it. */
data class ReleaseInfo(val version: String, val url: String)

/**
 * The three answers a check can give, kept apart because the screen says something different for each.
 *
 * A repository with no releases yet is NOT a failure — `NoRelease` is the truth on the day this ships, since
 * the first APK will be uploaded after it. Reporting that as "could not reach GitHub" would blame the wrong
 * thing on the one day the row is most likely to be looked at.
 */
sealed interface UpdateLookup {
    data class Found(val release: ReleaseInfo) : UpdateLookup
    data object NoRelease : UpdateLookup
    data object Unreachable : UpdateLookup
}

/** Injected so the check can be driven by a fake in a test with no network and no device. */
interface UpdateSource {
    suspend fun latestRelease(): UpdateLookup
}

/** What the last check concluded, stored by name so the value survives a preference round trip. */
enum class UpdateOutcome { NEVER_CHECKED, UP_TO_DATE, AVAILABLE, UNREACHABLE, NO_RELEASE }

/**
 * The last check, as the About row reads it.
 *
 * `version` is only set for [UpdateOutcome.AVAILABLE], because a version that is not being offered is not a
 * fact worth showing — "up to date to 1.5.0" invites a parent to think 1.5.0 is special.
 */
data class UpdateStatus(
    val enabled: Boolean = true,
    val lastCheckAtMillis: Long = 0L,
    val outcome: UpdateOutcome = UpdateOutcome.NEVER_CHECKED,
    val version: String = "",
)

/**
 * `1.5.0`, `v1.5.0` and `1.5.0-beta.2` all reduce to their numeric run: [1, 5, 0].
 *
 * Trailing parts are dropped rather than guessed at, because a suffix is a pre-release and comparing it
 * properly needs rules this app has no use for: the tags it reads are its own, and it only ever asks "is the
 * published one newer than me". Null when there is no number at all — the caller then says nothing, which is
 * better than nagging about a version it could not read.
 */
fun parseVersion(raw: String): List<Int>? {
    val trimmed = raw.trim().let { if (it.startsWith("v", ignoreCase = true)) it.substring(1) else it }
    val parts = mutableListOf<Int>()
    for (chunk in trimmed.split('.')) {
        val digits = chunk.takeWhile { it.isDigit() }
        if (digits.isEmpty()) break
        parts += digits.toIntOrNull() ?: break
        if (digits.length != chunk.length) break
    }
    return parts.ifEmpty { null }
}

/**
 * Positive when [candidate] is NEWER than [current], negative when it is older, 0 when they match, null when
 * either cannot be read.
 *
 * Missing parts count as zero, so `1.5` and `1.5.0` are the same version rather than a spurious update, and
 * the comparison is by number and not by text — `1.10.0` is newer than `1.4.1`, which a string compare would
 * have got backwards.
 */
fun compareVersions(current: String, candidate: String): Int? {
    val mine = parseVersion(current) ?: return null
    val theirs = parseVersion(candidate) ?: return null
    for (index in 0 until maxOf(mine.size, theirs.size)) {
        val a = mine.getOrElse(index) { 0 }
        val b = theirs.getOrElse(index) { 0 }
        if (a != b) return if (b > a) 1 else -1
    }
    return 0
}

/** True only when the published version is strictly newer. An unreadable version is never "newer". */
fun isNewerRelease(current: String, candidate: String): Boolean =
    (compareVersions(current, candidate) ?: 0) > 0

/**
 * Whether this launch should ask GitHub anything.
 *
 * Off means off: a parent who switched the row off gets no request at all, not a hidden one. A clock that has
 * gone backwards yields a negative age, which fails the interval — the alternative is a phone that asks every
 * time it opens until the clock catches up.
 */
fun shouldCheckForUpdates(
    enabled: Boolean,
    lastCheckAtMillis: Long,
    nowMillis: Long,
    intervalMs: Long = UPDATE_CHECK_INTERVAL_MS,
): Boolean = enabled && (lastCheckAtMillis <= 0L || nowMillis - lastCheckAtMillis >= intervalMs)

/**
 * The API endpoint for a `https://github.com/owner/repo` link.
 *
 * Derived rather than stored as a second constant, so the About button and the check can never point at two
 * different repositories. Null for a link without two path segments, which is a configuration mistake the
 * check reports as unreachable rather than pretending to have looked.
 */
fun releasesApiFor(repositoryUrl: String): String? {
    val path = repositoryUrl.trim().trimEnd('/').substringAfter("github.com/", "").trim('/')
    val parts = path.split('/').filter { it.isNotBlank() }
    if (parts.size < 2) return null
    return "https://api.github.com/repos/${parts[0]}/${parts[1]}/releases/latest"
}

/** Where a parent is sent to fetch it. The human page, never the API link. */
fun releasesPageFor(repositoryUrl: String): String = repositoryUrl.trim().trimEnd('/') + "/releases/latest"

/**
 * The verdict for one lookup, as a pair so the caller stores one row rather than deciding twice.
 *
 * A release whose version cannot be parsed is treated as up to date: a tag this app cannot read is not
 * evidence of a newer build, and claiming one exists would send a parent to a page they cannot use.
 */
fun outcomeFor(currentVersion: String, lookup: UpdateLookup): Pair<UpdateOutcome, String> = when (lookup) {
    is UpdateLookup.Found ->
        if (isNewerRelease(currentVersion, lookup.release.version)) {
            UpdateOutcome.AVAILABLE to lookup.release.version
        } else {
            UpdateOutcome.UP_TO_DATE to ""
        }
    UpdateLookup.NoRelease -> UpdateOutcome.NO_RELEASE to ""
    UpdateLookup.Unreachable -> UpdateOutcome.UNREACHABLE to ""
}

/** `{"tag_name":"v1.5.0","html_url":"..."}` → a release, or null when either field is missing. */
fun parseRelease(json: String): ReleaseInfo? = runCatching {
    val root = JSONObject(json)
    val version = root.optString("tag_name").trim()
    val url = root.optString("html_url").trim()
    if (version.isEmpty() || url.isEmpty()) null else ReleaseInfo(version, url)
}.getOrNull()

/**
 * GitHub's `releases/latest`, read with the platform's own HTTP client.
 *
 * No OkHttp and no WorkManager: this is one GET a day with two timeouts, and a dependency that exists to
 * make a request comfortable is not worth the download. It sends nothing that identifies the phone — no
 * account, no id, no version header carrying the install — which is what keeps the About screen's promise
 * that there are no trackers.
 */
class GitHubUpdateSource(
    private val repositoryUrl: String = SOURCE_REPOSITORY_URL,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
) : UpdateSource {

    override suspend fun latestRelease(): UpdateLookup = withContext(Dispatchers.IO) {
        val endpoint = releasesApiFor(repositoryUrl) ?: return@withContext UpdateLookup.Unreachable
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub refuses a request with no User-Agent; naming the app is the one fact sent, and it
                // is the same for every phone that runs it.
                setRequestProperty("User-Agent", "BabyLog")
            }
            when (connection.responseCode) {
                // 404 is the honest first-run state: the repository exists, no release has been published.
                404 -> UpdateLookup.NoRelease
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                    .let { parseRelease(it) }
                    ?.let { UpdateLookup.Found(it) }
                    ?: UpdateLookup.Unreachable
                else -> UpdateLookup.Unreachable
            }
        } catch (_: IOException) {
            UpdateLookup.Unreachable
        } catch (_: SecurityException) {
            UpdateLookup.Unreachable
        } finally {
            connection?.disconnect()
        }
    }
}
