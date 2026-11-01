package app.babylog.data.sync

import app.babylog.data.local.FoodEntity
import app.babylog.domain.CatalogueClaim

/**
 * The owner's published catalogue: `reference/food_catalogue`, one document both phones read.
 *
 * `version` is free text the owner writes by hand ("2026-11.1"), and it is the only thing that says which
 * list this is — there is no checksum here on purpose (ADR-0002). The growth dataset carries a manifest
 * with a sha256 because a wrong percentile is a clinical claim; a wrong food name is a typo, and the fix
 * for it is a console edit rather than an app release.
 */
data class PublishedCatalogue(
    val version: String,
    /** Ids are the document's own keys, exactly as in the seeded asset: a rename must never orphan a meal. */
    val foods: List<Pair<String, CatalogueClaim>>,
) {
    val isEmpty: Boolean get() = foods.isEmpty()
}

/** One parent's addition: `foods_pending/{id}`, which either phone may write and both may read. */
data class PendingFood(val id: String, val claim: CatalogueClaim)

/**
 * A catalogue document, as a map.
 *
 * **A malformed entry is skipped, never fatal.** One bad row typed into the console must not empty a
 * parent's picker, which is the opposite of the growth dataset's all-or-nothing checksum rule and is
 * deliberate: a partial food list is still a usable food list.
 */
fun catalogueFromMap(map: SyncMap): PublishedCatalogue {
    val version = (map["version"] as? String)?.trim().orEmpty()
    val raw = map["foods"] as? List<*> ?: emptyList<Any?>()
    val entries = raw.mapNotNull { item ->
        val entry = item as? Map<*, *> ?: return@mapNotNull null
        val id = (entry["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val name = (entry["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val category = (entry["category"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "Other"
        id to CatalogueClaim(name = name, category = category)
    }
    // First entry wins a repeated id: the document is hand-written, and a duplicate is an editing slip
    // rather than an instruction about which of the two names is newer.
    return PublishedCatalogue(version = version, foods = entries.distinctBy { it.first })
}

/** Null when the document cannot name a food at all: an id with no name is not something to show. */
fun pendingFromMap(documentId: String, map: SyncMap): PendingFood? {
    val id = (map["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: documentId
    if (id.isBlank()) return null
    val name = (map["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val category = (map["category"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "Custom"
    return PendingFood(id = id, claim = CatalogueClaim(name = name, category = category))
}

/**
 * A pending addition on its way to the server.
 *
 * `lastUsedAt`, `hidden` and `localName` are absent because they are this phone's business: recent-first
 * ordering, a hidden row and a rename are opinions about one picker, and sending them would make the two
 * phones fight over the same fields.
 */
fun pendingMap(food: FoodEntity, at: Long, by: String? = null): SyncMap = mapOf(
    "id" to food.id,
    "name" to food.name,
    "category" to food.category,
    "addedAt" to at,
    "addedBy" to by,
).filterValues { it != null }

/**
 * A rename the owner has not decided on yet.
 *
 * `from` is the catalogue's own name and `to` is what this parent calls it, so the document reads as a
 * sentence: "Courgette → Zucchini". It changes nothing on either phone; only a publish does that.
 */
fun proposalMap(id: String, from: String, to: String, at: Long, by: String? = null): SyncMap = mapOf(
    "id" to id,
    "from" to from,
    "to" to to,
    "proposedAt" to at,
    "proposedBy" to by,
).filterValues { it != null }
