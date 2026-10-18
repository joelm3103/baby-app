package app.babylog.data.backup

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.data.local.FoodEntity
import app.babylog.domain.TERM_GESTATION_DAYS
import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file's schema. Bump only for a breaking change; a reader refuses anything NEWER than this
 * rather than applying part of it.
 */
const val BACKUP_SCHEMA = 1

/** One whole-database snapshot. */
data class BackupSnapshot(
    val schema: Int = BACKUP_SCHEMA,
    val exportedAt: Long,
    val children: List<ChildEntity>,
    val events: List<EventEntity>,
    val foods: List<FoodEntity>,
)

/** A file written by a newer build. Refused whole — a half-applied restore is worse than a refusal. */
class UnsupportedBackupException(val found: Int, val supported: Int) :
    IllegalArgumentException("backup schema v$found is newer than the supported v$supported")

/**
 * The backup format: a versioned, human-readable JSON envelope of the whole database.
 *
 * Tombstones and custom foods are included deliberately. Dropping `deleted` rows would resurrect every
 * deleted event on restore; dropping the foods table would lose the only rows that cannot be
 * regenerated from the bundled asset.
 *
 * Pure (JSON in, JSON out) so it is tested without a database — the round trip is the contract.
 */
object Backup {
    private const val APP_TAG = "babylog"

    fun write(snapshot: BackupSnapshot): String {
        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("schema", snapshot.schema)
        root.put("exportedAt", snapshot.exportedAt)
        root.put("children", JSONArray().also { arr -> snapshot.children.forEach { arr.put(childJson(it)) } })
        root.put("events", JSONArray().also { arr -> snapshot.events.forEach { arr.put(eventJson(it)) } })
        root.put("foods", JSONArray().also { arr -> snapshot.foods.forEach { arr.put(foodJson(it)) } })
        // Indented on purpose: this file may have to be read by hand on a bad day.
        return root.toString(2)
    }

    fun read(json: String): BackupSnapshot {
        val root = JSONObject(json)
        val schema = if (root.has("schema")) root.optInt("schema") else 0
        require(schema >= 1) { "not a BabyLog backup: no schema version" }
        if (schema > BACKUP_SCHEMA) throw UnsupportedBackupException(schema, BACKUP_SCHEMA)
        return BackupSnapshot(
            schema = schema,
            exportedAt = root.optLong("exportedAt"),
            children = root.optJSONArray("children").objects { child(it) },
            events = root.optJSONArray("events").objects { event(it) },
            foods = root.optJSONArray("foods").objects { food(it) },
        )
    }

    // ---- children ---------------------------------------------------------

    private fun childJson(c: ChildEntity) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("birthDateEpochDay", c.birthDateEpochDay)
        put("gestationalAgeDays", c.gestationalAgeDays)
        put("sex", c.sex)
        put("useAdjustedAge", c.useAdjustedAge)
        put("sortOrder", c.sortOrder)
        put("deleted", c.deleted)
        put("updatedAt", c.updatedAt)
    }

    private fun child(o: JSONObject) = ChildEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        birthDateEpochDay = o.getLong("birthDateEpochDay"),
        gestationalAgeDays = o.optInt("gestationalAgeDays", TERM_GESTATION_DAYS),
        sex = o.getString("sex"),
        useAdjustedAge = o.optBoolean("useAdjustedAge", true),
        sortOrder = o.optInt("sortOrder", 0),
        deleted = o.optBoolean("deleted", false),
        updatedAt = o.optLong("updatedAt"),
    )

    // ---- events -----------------------------------------------------------

    private fun eventJson(e: EventEntity) = JSONObject().apply {
        put("id", e.id)
        put("childId", e.childId)
        put("type", e.type)
        put("startedAt", e.startedAt)
        putNullable("endedAt", e.endedAt)
        putNullable("amountMl", e.amountMl)
        putNullable("formulaBrand", e.formulaBrand)
        putNullable("mealSlot", e.mealSlot)
        putNullable("foodIdsCsv", e.foodIdsCsv)
        putNullable("reaction", e.reaction)
        putNullable("diaperKind", e.diaperKind)
        putNullable("rash", e.rash)
        putNullable("weightGrams", e.weightGrams)
        putNullable("lengthMm", e.lengthMm)
        putNullable("headMm", e.headMm)
        putNullable("temperatureC", e.temperatureC)
        putNullable("medication", e.medication)
        putNullable("doseAmount", e.doseAmount)
        putNullable("doseUnit", e.doseUnit)
        putNullable("vaccineName", e.vaccineName)
        putNullable("note", e.note)
        put("createdBy", e.createdBy)
        put("updatedAt", e.updatedAt)
        put("deleted", e.deleted)
    }

    private fun event(o: JSONObject) = EventEntity(
        id = o.getString("id"),
        childId = o.getString("childId"),
        type = o.getString("type"),
        startedAt = o.getLong("startedAt"),
        endedAt = o.longOrNull("endedAt"),
        amountMl = o.intOrNull("amountMl"),
        formulaBrand = o.stringOrNull("formulaBrand"),
        mealSlot = o.stringOrNull("mealSlot"),
        foodIdsCsv = o.stringOrNull("foodIdsCsv"),
        reaction = o.stringOrNull("reaction"),
        diaperKind = o.stringOrNull("diaperKind"),
        rash = o.booleanOrNull("rash"),
        weightGrams = o.intOrNull("weightGrams"),
        lengthMm = o.intOrNull("lengthMm"),
        headMm = o.intOrNull("headMm"),
        temperatureC = o.doubleOrNull("temperatureC"),
        medication = o.stringOrNull("medication"),
        doseAmount = o.doubleOrNull("doseAmount"),
        doseUnit = o.stringOrNull("doseUnit"),
        vaccineName = o.stringOrNull("vaccineName"),
        note = o.stringOrNull("note"),
        createdBy = o.getString("createdBy"),
        updatedAt = o.getLong("updatedAt"),
        deleted = o.optBoolean("deleted", false),
    )

    // ---- foods ------------------------------------------------------------

    private fun foodJson(f: FoodEntity) = JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
        put("category", f.category)
        putNullable("lastUsedAt", f.lastUsedAt)
        put("custom", f.custom)
    }

    private fun food(o: JSONObject) = FoodEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        category = o.optString("category", "Other"),
        lastUsedAt = o.longOrNull("lastUsedAt"),
        custom = o.optBoolean("custom", false),
    )

    // ---- helpers ----------------------------------------------------------

    /** Omit nulls entirely: an absent key and a JSON null both mean "not set" here. */
    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value != null) put(key, value)
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
    private fun JSONObject.booleanOrNull(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)

    /** `isNull` is true for a missing key too, which is exactly the tolerant read we want. */
    private fun <T> JSONArray?.objects(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { map(getJSONObject(it)) }
    }
}
