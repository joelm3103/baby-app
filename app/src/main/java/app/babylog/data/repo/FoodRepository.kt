package app.babylog.data.repo

import app.babylog.data.local.FoodCatalogue
import app.babylog.data.local.FoodDao
import app.babylog.data.local.FoodEntity
import app.babylog.domain.uniqueFoodSlug
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The solids catalogue: a curated list seeded from a bundled asset, plus whatever the parent adds.
 *
 * One table, two origins. A seeded row has `custom = false` and a stable slug id; a parent-added row
 * has `custom = true`. The picker does not care which is which, and no schema change was needed —
 * `FoodEntity` already carried both `custom` and `lastUsedAt`.
 */
class FoodRepository(
    private val foodDao: FoodDao,
    private val loadCatalogue: suspend () -> FoodCatalogue,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** The version of the catalogue last loaded, for the honest "which list is this" display. */
    @Volatile
    var catalogueVersion: String? = null
        private set

    /**
     * Recently used first, then the rest alphabetically. Observed rather than fetched once, so logging
     * a food reorders the picker on the next open without any refresh plumbing.
     */
    fun observeCatalogue(): Flow<List<FoodEntity>> = foodDao.observeCatalogue()

    /**
     * Safe to run on every launch, with no version gate: `insertIgnoreAll` adds foods a newer asset
     * introduced and ignores every row that already exists, so a parent's custom food and a touched
     * `lastUsedAt` are never disturbed. At ~90 rows the operation is trivial, and re-running it means
     * an updated asset applies itself without any extra state to drift.
     */
    suspend fun seedIfNeeded() {
        val catalogue = withContext(Dispatchers.IO) { loadCatalogue() }
        catalogueVersion = catalogue.version
        if (catalogue.foods.isEmpty()) return
        foodDao.insertIgnoreAll(catalogue.foods)
    }

    suspend fun count(): Int = foodDao.count()

    suspend fun all(): List<FoodEntity> = foodDao.all()

    suspend fun byIds(ids: List<String>): List<FoodEntity> =
        if (ids.isEmpty()) emptyList() else foodDao.byIds(ids)

    /**
     * Adds a food the catalogue does not have, and returns the existing row when it does. Reuse is by
     * name (case-insensitive) and then by slug, so typing "Banana" for a food already in the list
     * returns the catalogue entry instead of creating a visible duplicate. A name that yields no slug
     * at all (an emoji, a non-Latin script) is identified by a generated id instead.
     */
    suspend fun addCustom(name: String): FoodEntity {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "a custom food needs a name" }

        val existing = foodDao.all()
        existing.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

        val slug = uniqueFoodSlug(trimmed, existing.map { it.id }.toSet())
        val id = slug.ifEmpty { "custom-$newId()" }
        existing.firstOrNull { it.id == id }?.let { return it }

        val food = FoodEntity(id = id, name = trimmed, category = CUSTOM_CATEGORY, custom = true)
        foodDao.upsert(food)
        return food
    }

    private companion object {
        const val CUSTOM_CATEGORY = "Custom"
    }
}
