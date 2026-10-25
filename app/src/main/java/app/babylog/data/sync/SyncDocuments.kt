package app.babylog.data.sync

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.domain.TERM_GESTATION_DAYS

/**
 * A row as it travels to and from Firestore.
 *
 * A plain map rather than the entity itself, so the conversion is testable on the JVM: Firestore's
 * `DocumentSnapshot` cannot be constructed in a unit test, but a `Map<String, Any?>` can, and that is
 * exactly what `DocumentSnapshot.data` returns. The gateway is then a thin, untested-by-unit-test shell
 * around `set(map, merge)` and `snapshot.data`.
 *
 * The document id duplicates the `id` field on purpose. It makes an exported document self-describing
 * and lets a restore-from-Firestore path exist later without guessing ids from paths.
 */
typealias SyncMap = Map<String, Any?>

/** Firestore stores whole numbers as `Long` and reals as `Double`, so read through `Number`. */
private fun SyncMap.long(key: String): Long? = (this[key] as? Number)?.toLong()

private fun SyncMap.int(key: String): Int? = (this[key] as? Number)?.toInt()

private fun SyncMap.double(key: String): Double? = (this[key] as? Number)?.toDouble()

private fun SyncMap.string(key: String): String? = this[key] as? String

private fun SyncMap.bool(key: String): Boolean? = this[key] as? Boolean

/**
 * One incoming document, reduced to what the merge decides on.
 *
 * Separate from the entity because a **tombstone document need not be a whole row**: a permanently
 * deleted child has no fields left to send, so its deletion travels as `id`, `deleted` and `updatedAt`
 * only. Parsing the entity first would make every such tombstone unreadable, and the deletion would be
 * dropped on the other phone.
 */
data class RemoteRow(
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val payload: SyncMap,
)

/** Null when even the three fields a merge needs are missing, i.e. the document is not ours. */
fun remoteRowOf(documentId: String, map: SyncMap): RemoteRow? {
    val id = map.string("id") ?: documentId
    val updatedAt = map.long("updatedAt") ?: return null
    return RemoteRow(
        id = id,
        updatedAt = updatedAt,
        deleted = map.bool("deleted") ?: false,
        payload = map,
    )
}

fun ChildEntity.toSyncMap(): SyncMap = mapOf(
    "id" to id,
    "name" to name,
    "birthDateEpochDay" to birthDateEpochDay,
    "gestationalAgeDays" to gestationalAgeDays,
    "sex" to sex,
    "useAdjustedAge" to useAdjustedAge,
    "sortOrder" to sortOrder,
    "deleted" to deleted,
    "updatedAt" to updatedAt,
)

/**
 * A child from its document, or null when the document cannot be one.
 *
 * Null rather than an invented default: a child with no name or no sex is not something to write into
 * the database, and a row silently defaulted to "Boy, 280 days" would corrupt the percentile maths it
 * feeds. The caller skips the document and says so.
 */
fun childFromSyncMap(map: SyncMap): ChildEntity? {
    val id = map.string("id") ?: return null
    val name = map.string("name") ?: return null
    val sex = map.string("sex") ?: return null
    return ChildEntity(
        id = id,
        name = name,
        birthDateEpochDay = map.long("birthDateEpochDay") ?: return null,
        gestationalAgeDays = map.int("gestationalAgeDays") ?: TERM_GESTATION_DAYS,
        sex = sex,
        useAdjustedAge = map.bool("useAdjustedAge") ?: true,
        sortOrder = map.int("sortOrder") ?: 0,
        deleted = map.bool("deleted") ?: false,
        updatedAt = map.long("updatedAt") ?: return null,
    )
}

fun EventEntity.toSyncMap(): SyncMap = mapOf(
    "id" to id,
    "childId" to childId,
    "type" to type,
    "startedAt" to startedAt,
    "endedAt" to endedAt,
    "amountMl" to amountMl,
    "formulaBrand" to formulaBrand,
    "mealSlot" to mealSlot,
    "foodIdsCsv" to foodIdsCsv,
    "reaction" to reaction,
    "diaperKind" to diaperKind,
    "rash" to rash,
    // Phase 13.3. A document written before this build simply lacks both keys, and `eventFromSyncMap`
    // reads them as empty - "not recorded" - rather than as a parse failure.
    "diaperColoursCsv" to diaperColoursCsv,
    "diaperConsistencyCsv" to diaperConsistencyCsv,
    "weightGrams" to weightGrams,
    "lengthMm" to lengthMm,
    "headMm" to headMm,
    "temperatureC" to temperatureC,
    "medication" to medication,
    "doseAmount" to doseAmount,
    "doseUnit" to doseUnit,
    "vaccineName" to vaccineName,
    "note" to note,
    "createdBy" to createdBy,
    "updatedAt" to updatedAt,
    "deleted" to deleted,
)

fun eventFromSyncMap(map: SyncMap): EventEntity? {
    val id = map.string("id") ?: return null
    return EventEntity(
        id = id,
        childId = map.string("childId") ?: return null,
        type = map.string("type") ?: return null,
        startedAt = map.long("startedAt") ?: return null,
        endedAt = map.long("endedAt"),
        amountMl = map.int("amountMl"),
        formulaBrand = map.string("formulaBrand"),
        mealSlot = map.string("mealSlot"),
        foodIdsCsv = map.string("foodIdsCsv"),
        reaction = map.string("reaction"),
        diaperKind = map.string("diaperKind"),
        rash = map.bool("rash"),
        diaperColoursCsv = map.string("diaperColoursCsv"),
        diaperConsistencyCsv = map.string("diaperConsistencyCsv"),
        weightGrams = map.int("weightGrams"),
        lengthMm = map.int("lengthMm"),
        headMm = map.int("headMm"),
        temperatureC = map.double("temperatureC"),
        medication = map.string("medication"),
        doseAmount = map.double("doseAmount"),
        doseUnit = map.string("doseUnit"),
        vaccineName = map.string("vaccineName"),
        note = map.string("note"),
        createdBy = map.string("createdBy") ?: "unknown",
        updatedAt = map.long("updatedAt") ?: return null,
        deleted = map.bool("deleted") ?: false,
    )
}

/**
 * A tombstone document for a permanently deleted row.
 *
 * The deleted row no longer exists locally to copy fields from — that is the whole point of a permanent
 * delete — so the tombstone carries only what the other phone needs to act on it.
 */
fun tombstone(id: String, updatedAt: Long): SyncMap = mapOf(
    "id" to id,
    "deleted" to true,
    "updatedAt" to updatedAt,
)
