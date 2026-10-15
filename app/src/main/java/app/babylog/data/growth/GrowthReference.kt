package app.babylog.data.growth

import app.babylog.domain.Sex
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Which growth standard a measurement is read against.
 *
 * The names are the WHO table names, because that is what the source files are called and a renaming
 * would hide the provenance. Only the first three are charted in v1 — see [GrowthReference.lms] and
 * `ui/growth`.
 */
enum class GrowthMeasure {
    WEIGHT_FOR_AGE,
    LENGTH_HEIGHT_FOR_AGE,
    HEAD_CIRCUMFERENCE_FOR_AGE,
    BMI_FOR_AGE,
    WEIGHT_FOR_LENGTH_HEIGHT,
}

/**
 * What a table's x-axis measures.
 *
 * This distinction is the single easiest way to build a confidently wrong chart. The four age-keyed
 * tables run 0–1856 **days** (daily, so day == row index), while weight-for-length is keyed by
 * **centimetres** 45.0–110.0 in 0.1 cm steps. Reading the age axis as months, or the length axis as
 * age, produces a plausible-looking percentile that is simply wrong — which is why the axis travels
 * with the table rather than being assumed by the caller.
 */
enum class GrowthAxis { AGE_DAYS, LENGTH_CM }

/** One `sex, axis, l, m, s` row of a WHO table. */
data class LmsPoint(val axis: Double, val l: Double, val m: Double, val s: Double)

/** A source file the bundled dataset was built from, with its checksum, so the asset is auditable. */
data class GrowthSourceFile(
    val name: String,
    val measure: GrowthMeasure,
    val axis: GrowthAxis,
    val rowsPerSex: Int,
    val sha256: String,
    val url: String,
)

/**
 * The WHO Child Growth Standards, bundled in the APK.
 *
 * WHO has not revised these tables since 2006, so the asset ships rather than being fetched on first
 * run; `retrievedAt` is therefore the honest date to show next to a percentile, not `publishedAt`.
 * See `research/growth-data-sources.md` for how the files were verified.
 */
data class GrowthReference(
    val dataset: String,
    val version: String,
    val publishedAt: String,
    val retrievedAt: String,
    val citation: String,
    val license: String,
    val sourceFiles: List<GrowthSourceFile>,
    private val tables: Map<GrowthMeasure, Map<Sex, List<LmsPoint>>>,
) {
    /** The rows for one measure and sex, as published. Never empty for a bundled measure. */
    fun table(measure: GrowthMeasure, sex: Sex): List<LmsPoint> =
        tables[measure]?.get(sex).orEmpty()

    fun axisOf(measure: GrowthMeasure): GrowthAxis =
        sourceFiles.firstOrNull { it.measure == measure }?.axis ?: GrowthAxis.AGE_DAYS

    /**
     * The LMS parameters to read a measurement against, or null when the axis is outside the table.
     *
     * A null is the honest answer, not a missed case: extrapolating WHO's tables past their published
     * range (day 1856, or 45 cm) would invent a percentile the data does not support.
     */
    fun lms(measure: GrowthMeasure, sex: Sex, axis: Double): LmsPoint? {
        val rows = table(measure, sex)
        if (rows.isEmpty()) return null
        return when (axisOf(measure)) {
            // Daily rows: the day IS the index, so this is a lookup and not an interpolation.
            GrowthAxis.AGE_DAYS -> rows.getOrNull(axis.toInt().takeIf { it >= 0 } ?: return null)
            GrowthAxis.LENGTH_CM -> interpolate(rows, axis)
        }
    }

    /**
     * A percentile curve across a whole table, for plotting.
     *
     * Kept here rather than in the UI so the chart and the callout cannot disagree about what the
     * reference says.
     */
    fun curve(measure: GrowthMeasure, sex: Sex, from: Double, to: Double, step: Double): List<LmsPoint> {
        val out = mutableListOf<LmsPoint>()
        var axis = from
        while (axis <= to) {
            lms(measure, sex, axis)?.let(out::add)
            axis += step
        }
        return out
    }

    /**
     * The required dataset line: `WHO 2006 · v2026.10 · updated 15 Oct 2026`.
     *
     * Derived from the version string rather than stored, so a future dataset cannot be displayed with
     * a stale label, and a percentile is never shown without saying which tables produced it.
     */
    val identityLine: String get() = buildIdentityLine(version, retrievedAt)

    private fun interpolate(rows: List<LmsPoint>, axis: Double): LmsPoint? {
        if (axis < rows.first().axis || axis > rows.last().axis) return null
        val upper = rows.indexOfFirst { it.axis >= axis }
        if (upper <= 0) return rows.first()
        val high = rows[upper]
        val low = rows[upper - 1]
        val span = high.axis - low.axis
        val t = if (span == 0.0) 0.0 else (axis - low.axis) / span
        fun mix(a: Double, b: Double) = a + (b - a) * t
        return LmsPoint(axis, mix(low.l, high.l), mix(low.m, high.m), mix(low.s, high.s))
    }
}

/** Thrown when the bundled asset is missing a measure or a sex a percentile would need. */
class GrowthDatasetException(message: String) : Exception(message)

/**
 * Reads the bundled growth asset. Mirrors `FoodCatalogueParser`: the asset is data, so a future
 * dataset release is a data change rather than a code change.
 */
object GrowthReferenceParser {
    /** Packaged under the app's assets, so it ships inside the APK and needs no network. */
    const val ASSET_PATH = "growth/who-igrowup.json"

    private val measureByKey = GrowthMeasure.entries.associateBy { it.name }

    fun parse(json: String): GrowthReference {
        val root = JSONObject(json)
        val sourceFiles = buildList {
            val files = root.getJSONArray("files")
            for (i in 0 until files.length()) {
                val row = files.getJSONObject(i)
                add(
                    GrowthSourceFile(
                        name = row.getString("name"),
                        measure = measure(row.getString("measure")),
                        axis = GrowthAxis.valueOf(row.getString("axis")),
                        rowsPerSex = row.getInt("rowsPerSex"),
                        sha256 = row.getString("sha256"),
                        url = row.getString("url"),
                    ),
                )
            }
        }

        val measures = root.getJSONObject("measures")
        val tables = GrowthMeasure.entries.associateWith { measure ->
            val forMeasure = measures.optJSONObject(measure.name)
            mapOf(
                Sex.BOY to rows(forMeasure, "1"),
                Sex.GIRL to rows(forMeasure, "2"),
            )
        }

        val reference = GrowthReference(
            dataset = root.getString("dataset"),
            version = root.getString("version"),
            publishedAt = root.getString("publishedAt"),
            retrievedAt = root.getString("retrievedAt"),
            citation = root.getString("citation"),
            license = root.getString("license"),
            sourceFiles = sourceFiles,
            tables = tables,
        )

        val missing = GrowthMeasure.entries.filter { reference.table(it, Sex.BOY).isEmpty() }
        if (missing.isNotEmpty()) {
            throw GrowthDatasetException("growth asset has no rows for $missing")
        }
        if (reference.table(GrowthMeasure.WEIGHT_FOR_AGE, Sex.GIRL).isEmpty()) {
            throw GrowthDatasetException("growth asset has no girls' rows")
        }
        return reference
    }

    private fun rows(forMeasure: JSONObject?, sexCode: String): List<LmsPoint> {
        val array = forMeasure?.optJSONArray(sexCode) ?: return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val row = array.getJSONArray(i)
                add(
                    LmsPoint(
                        axis = row.getDouble(0),
                        l = row.getDouble(1),
                        m = row.getDouble(2),
                        s = row.getDouble(3),
                    ),
                )
            }
        }
    }

    private fun measure(key: String): GrowthMeasure =
        measureByKey[key] ?: throw GrowthDatasetException("unknown growth measure '$key'")
}

/**
 * `WHO 2006 · v2026.10 · updated 15 Oct 2026`, or a plain fallback if the asset's version ever changes
 * shape. Pure, so the exact wording is pinned by a test rather than by the screen.
 */
internal fun buildIdentityLine(version: String, retrievedAt: String): String {
    val updated = runCatching {
        LocalDate.parse(retrievedAt).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    }.getOrNull()

    val dash = version.split('-', limit = 2)
    val brand = dash.getOrNull(0)?.takeIf { it.isNotBlank() && dash.size == 2 }?.let {
        val rest = dash[1]
        val year = rest.substringBefore('.')
        if (year.isBlank()) null else "${it.uppercase(Locale.ENGLISH)} $year"
    }
    val revision = dash.getOrNull(1)?.substringAfter('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }

    return listOfNotNull(
        brand ?: version,
        revision?.let { "v$it" },
        updated?.let { "updated $it" },
    ).joinToString(" · ")
}
