package app.babylog.data.repo

import app.babylog.data.local.FoodCatalogue
import app.babylog.data.local.FoodDao
import app.babylog.data.local.FoodEntity
import app.babylog.data.local.OutboxDao
import app.babylog.data.sync.OutboxDrainer
import app.babylog.data.sync.PendingFood
import app.babylog.data.sync.PublishedCatalogue
import app.babylog.data.sync.outboxIntent
import app.babylog.domain.FoodResolution
import app.babylog.domain.LocalFoodOpinion
import app.babylog.domain.displayFoodName
import app.babylog.domain.resolveFood
import app.babylog.domain.uniqueFoodSlug
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The solids catalogue: a curated list seeded from a bundled asset, a published half both phones read, and
 * whatever a parent adds.
 *
 * Three sources, one table, and [resolveFood] decides what an id means when they disagree (ADR-0002):
 *
 * - the bundled asset (read-only, ships in the APK) is the floor;
 * - `reference/food_catalogue` is the owner's published list, and it beats the asset — that is how a rename
 *   travels to both phones without an APK;
 * - `foods_pending` holds the foods a parent typed, so the other phone can read the name at once.
 *
 * Two things on a row NEVER travel: `localName` (this parent renamed a catalogue entry for their own
 * picker) and `hidden` (they took it out of it). Both are opinions about one picker, not facts about the
 * catalogue, and a rename proposal is how a disagreement becomes visible instead of silent.
 */
class FoodRepository(
    private val foodDao: FoodDao,
    private val outboxDao: OutboxDao? = null,
    private val settings: SettingsRepository? = null,
    private val loadCatalogue: suspend () -> FoodCatalogue,
    private val clock: Clock = Clock.systemUTC(),
    /** Called after anything is queued, so the drain runs without waiting for the next app start. */
    private val onQueued: suspend () -> Unit = {},
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** The version of the catalogue last loaded, for the honest "which list is this" display. */
    @Volatile
    var catalogueVersion: String? = null
        private set

    /**
     * Recently used first, then the rest alphabetically. Observed rather than fetched once, so logging
     * a food reorders the picker on the next open without any refresh plumbing.
     *
     * Includes hidden rows: the picker filters them, and the "N hidden" row needs to know they exist.
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

    suspend fun byId(id: String): FoodEntity? = foodDao.byId(id)

    /**
     * The owner's published catalogue, folded over whatever is already here.
     *
     * A row that does not exist yet is created; an asset row is renamed in place; and `localName`,
     * `hidden` and `lastUsedAt` are carried across untouched, because a publish is a statement about the
     * catalogue and not about one parent's picker.
     */
    suspend fun applyPublished(catalogue: PublishedCatalogue) {
        if (catalogue.version.isNotBlank()) catalogueVersion = catalogue.version
        catalogue.foods.forEach { (id, claim) ->
            val local = foodDao.byId(id)
            val resolved = resolveFood(id = id, published = claim, local = local?.opinion()) ?: return@forEach
            val row = resolved.toEntity()
            if (row != local) foodDao.upsert(row)
            if (local?.custom == true && (outboxDao?.countFor(id) ?: 0) == 0) {
                // The owner has promoted this id into the catalogue. Withdraw the now-redundant pending
                // document so it cannot sit in both lists forever; the row itself stays, because meals
                // already reference it.
                outboxDao?.enqueue(
                    outboxIntent(
                        OutboxDrainer.ENTITY_FOOD_PENDING,
                        id,
                        deleted = true,
                        queuedAt = clock.millis(),
                    ),
                )
                onQueued()
            }
        }
    }

    /**
     * The other phone's additions, and this phone's own.
     *
     * **Additive only.** A snapshot can never remove a row: an id missing from the collection is an entry
     * nobody published, and deleting a food a parent typed because their own queued write had not landed
     * yet would lose it for good. Removal is a local action ([delete]) or a catalogue publish, both of
     * which say so explicitly.
     */
    suspend fun applyPending(pending: List<PendingFood>) {
        val publishedIds = foodDao.all().filterNot { it.custom }.map { it.id }.toSet()
        val pendingById = pending.associateBy { it.id }
        val queuedIds = outboxDao?.pending()?.map { it.entityId }?.toSet().orEmpty()

        pending.forEach { entry ->
            val local = foodDao.byId(entry.id)
            // The catalogue already speaks for this id. A promoted food must not flip back to "custom"
            // because a stale pending document is still lying around.
            if (local != null && !local.custom) return@forEach
            val resolved = resolveFood(id = entry.id, pending = entry.claim, local = local?.opinion())
                ?: return@forEach
            val row = resolved.toEntity()
            if (row != local) foodDao.upsert(row)
        }

        // A pending document disappeared. That is a real removal, unlike an empty snapshot arriving before
        // an offline add has drained (which is protected by `queuedIds`). Keep the local row hidden rather
        // than hard-deleting it: an older meal still needs the name when its card is rendered.
        foodDao.all().filter { food ->
            food.custom && food.id !in pendingById && food.id !in publishedIds && food.id !in queuedIds
        }.forEach { food ->
            if (!food.hidden) foodDao.upsert(food.copy(hidden = true))
        }
    }

    /**
     * Announce the foods this phone had already typed, once.
     *
     * Before the published catalogue shipped, `addCustom` queued nothing, so a custom food existed on one
     * phone and nowhere else. Without this pass those rows would stay invisible to the other phone for
     * ever, because nothing else ever queues a food that already exists.
     */
    suspend fun seedPendingFoodsOnce() {
        if (settings?.foodSeedDone?.first() == true) return
        queueAllPendingFoods()
        settings?.markFoodSeedDone()
    }

    /**
     * Queue every food this phone added, unconditionally.
     *
     * Used by the first-attach seed and by a restore, where the file just written IS the truth and the
     * other phone has to be told about all of it.
     */
    suspend fun queueAllPendingFoods() {
        val at = clock.millis()
        val alreadyQueued = outboxDao?.pending().orEmpty().map { it.entityId }.toSet()
        val queued = foodDao.all().filter { it.custom && it.id !in alreadyQueued }
        queued.forEach { food ->
            outboxDao?.enqueue(
                outboxIntent(OutboxDrainer.ENTITY_FOOD_PENDING, food.id, deleted = false, queuedAt = at),
            )
        }
        if (queued.isNotEmpty()) onQueued()
    }

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
        existing.firstOrNull { displayFoodName(it.name, it.localName).equals(trimmed, ignoreCase = true) }
            ?.let { return it }

        val slug = uniqueFoodSlug(trimmed, existing.map { it.id }.toSet())
        val id = slug.ifEmpty { "custom-$newId()" }
        existing.firstOrNull { it.id == id }?.let { return it }

        val food = FoodEntity(id = id, name = trimmed, category = CUSTOM_CATEGORY, custom = true)
        foodDao.upsert(food)
        queuePending(food.id, deleted = false)
        return food
    }

    /**
     * Renames a food, and the two kinds are renamed by different means on purpose.
     *
     * A **pending** food is this parent's own addition, so its `name` changes and travels through
     * `foods_pending` — the other phone should read the same word. A **catalogue** entry is not theirs to
     * rename: the local `localName` changes what this picker shows, and a proposal document records the
     * disagreement for the owner to decide on. Renaming something back to the catalogue's own name clears
     * the local override and queues nothing.
     */
    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "a food needs a name" }
        val food = foodDao.byId(id) ?: return

        if (food.custom) {
            val renamed = food.copy(name = trimmed, localName = null)
            if (renamed != food) {
                foodDao.upsert(renamed)
                queuePending(id, deleted = false)
            }
            return
        }

        val override = trimmed.takeIf { !it.equals(food.name, ignoreCase = true) }
        if (override == food.localName) return
        foodDao.upsert(food.copy(localName = override))
        if (override != null) {
            outboxDao?.enqueue(
                outboxIntent(
                    OutboxDrainer.ENTITY_FOOD_PROPOSAL,
                    id,
                    deleted = false,
                    queuedAt = clock.millis(),
                ),
            )
            onQueued()
        }
    }

    /** Takes a food out of THIS phone's picker. Local only: it queues nothing and notifies nobody. */
    suspend fun setHidden(id: String, hidden: Boolean) {
        val food = foodDao.byId(id) ?: return
        if (food.hidden == hidden) return
        foodDao.upsert(food.copy(hidden = hidden))
    }

    /**
     * Removes a food a parent added. Refuses a catalogue entry, which no phone may delete — hide it
     * instead, and the catalogue keeps its own row on both devices.
     */
    suspend fun delete(id: String) {
        val food = foodDao.byId(id) ?: return
        if (!food.custom) return
        outboxDao?.deleteUpserts(entityIds = listOf(id))
        // A deleted custom food stops appearing in the picker on this phone, but the row stays so an older
        // meal continues to show its name. The other phone receives the pending-document removal and hides
        // its copy too.
        foodDao.upsert(food.copy(hidden = true))
        queuePending(id, deleted = true)
    }

    private suspend fun queuePending(id: String, deleted: Boolean) {
        outboxDao?.enqueue(
            outboxIntent(
                OutboxDrainer.ENTITY_FOOD_PENDING,
                id,
                deleted = deleted,
                queuedAt = clock.millis(),
            ),
        )
        onQueued()
    }

    private fun FoodEntity.opinion() = LocalFoodOpinion(localName = localName, hidden = hidden, lastUsedAt = lastUsedAt)

    private fun FoodResolution.toEntity() = FoodEntity(
        id = id,
        name = name,
        category = category,
        lastUsedAt = lastUsedAt,
        custom = custom,
        localName = localName,
        hidden = hidden,
    )

    private companion object {
        const val CUSTOM_CATEGORY = "Custom"
    }
}
