package app.babylog.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.local.AppDatabase
import app.babylog.data.local.FoodCatalogue
import app.babylog.data.local.FoodCatalogueParser
import app.babylog.data.sync.OutboxDrainer
import app.babylog.data.sync.PublishedCatalogue
import app.babylog.domain.CatalogueClaim
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The catalogue is seeded from the REAL bundled asset, so this also proves the shipped JSON parses —
 * a malformed asset would otherwise only fail on a phone, which is exactly the class of bug the
 * JVM/Robolectric setup exists to prevent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FoodRepository
    private lateinit var catalogue: FoodCatalogue

    private val t0 = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        catalogue = FoodCatalogueParser.parse(
            context.assets.open(FoodCatalogueParser.ASSET_PATH).bufferedReader().use { it.readText() },
        )
        repo = FoodRepository(
            foodDao = db.foodDao(),
            outboxDao = db.outboxDao(),
            loadCatalogue = { catalogue },
            newId = { "generated" },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `the bundled asset parses and contains a usable catalogue`() {
        assertTrue("expected a real food list, got ${catalogue.foods.size}", catalogue.foods.size >= 60)
        assertTrue(catalogue.version.isNotBlank())
        val ids = catalogue.foods.map { it.id }
        assertEquals("food ids must be unique", ids.size, ids.toSet().size)
        assertTrue("banana" in ids)
        assertTrue("sweet-potato" in ids)
        assertTrue("peanut-butter" in ids)
    }

    @Test
    fun `seeding fills the empty table with every food in the asset`() = runBlocking {
        assertEquals(0, repo.count())
        repo.seedIfNeeded()
        assertEquals(catalogue.foods.size, repo.count())
        assertEquals(catalogue.version, repo.catalogueVersion)
    }

    @Test
    fun `re-seeding is idempotent and never resets lastUsedAt`() = runBlocking {
        repo.seedIfNeeded()
        db.foodDao().touch("banana", t0)

        repo.seedIfNeeded()

        assertEquals(catalogue.foods.size, repo.count())
        assertEquals(t0, repo.byIds(listOf("banana")).single().lastUsedAt)
    }

    @Test
    fun `re-seeding never clobbers a custom food`() = runBlocking {
        repo.seedIfNeeded()
        val custom = repo.addCustom("Mango lassi")

        repo.seedIfNeeded()

        val reloaded = repo.byIds(listOf(custom.id)).single()
        assertTrue(reloaded.custom)
        assertEquals("Mango lassi", reloaded.name)
    }

    @Test
    fun `adding a food already in the catalogue reuses it rather than duplicating`() = runBlocking {
        repo.seedIfNeeded()
        val before = repo.count()

        val banana = repo.addCustom("banana")

        assertEquals("banana", banana.id)
        assertFalse(banana.custom)
        assertEquals(before, repo.count())
    }

    @Test
    fun `adding a new food stores it as custom and lists it in the catalogue`() = runBlocking {
        repo.seedIfNeeded()

        val food = repo.addCustom("First Twin's special porridge")

        assertTrue(food.custom)
        assertEquals("Custom", food.category)
        assertEquals("first-twin-s-special-porridge", food.id)
        assertTrue(repo.observeCatalogue().first().any { it.id == food.id })
    }

    @Test
    fun `adding a food queues one pending upsert`() = runBlocking {
        val food = repo.addCustom("Callaloo")

        val queued = db.outboxDao().pending().single()
        assertEquals(OutboxDrainer.ENTITY_FOOD_PENDING, queued.entityType)
        assertEquals(food.id, queued.entityId)
        assertEquals(OutboxDrainer.OP_UPSERT, queued.op)
    }

    @Test
    fun `renaming a catalogue food stores a local name and queues a proposal`() = runBlocking {
        repo.seedIfNeeded()

        repo.rename("courgette", "Zucchini")

        assertEquals("Zucchini", repo.byId("courgette")!!.localName)
        val queued = db.outboxDao().pending().single()
        assertEquals(OutboxDrainer.ENTITY_FOOD_PROPOSAL, queued.entityType)
        assertEquals("courgette", queued.entityId)
    }

    @Test
    fun `hiding a food is local and queues nothing`() = runBlocking {
        repo.seedIfNeeded()

        repo.setHidden("banana", true)

        assertTrue(repo.byId("banana")!!.hidden)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `deleting a custom food hides it locally and queues a pending removal`() = runBlocking {
        val food = repo.addCustom("Callaloo")
        db.outboxDao().deleteEverything()

        repo.delete(food.id)

        assertTrue(repo.byId(food.id)!!.hidden)
        assertEquals(OutboxDrainer.ENTITY_FOOD_PENDING, db.outboxDao().pending().single().entityType)
        assertEquals(OutboxDrainer.OP_DELETE, db.outboxDao().pending().single().op)
    }

    @Test
    fun `a catalogue row cannot be deleted`() = runBlocking {
        repo.seedIfNeeded()

        repo.delete("banana")

        assertTrue(repo.byId("banana") != null)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `published catalogue renames a seeded row but preserves local curation and recency`() = runBlocking {
        repo.seedIfNeeded()
        db.foodDao().touch("banana", t0)
        db.foodDao().upsert(db.foodDao().byId("banana")!!.copy(localName = "My banana", hidden = true))

        repo.applyPublished(
            PublishedCatalogue(
                version = "2026-11.1",
                foods = listOf("banana" to CatalogueClaim("Plantain", "Fruit")),
            ),
        )

        val food = repo.byId("banana")!!
        assertEquals("Plantain", food.name)
        assertEquals("My banana", food.localName)
        assertTrue(food.hidden)
        assertEquals(t0, food.lastUsedAt)
        assertEquals("2026-11.1", repo.catalogueVersion)
    }

    @Test
    fun `pending snapshot cannot remove an offline custom food still queued`() = runBlocking {
        val food = repo.addCustom("Callaloo")

        repo.applyPending(emptyList())

        assertEquals(food, repo.byId(food.id))
        assertFalse(food.hidden)
    }

    @Test
    fun `a removed pending document hides its row but keeps its old-meal name`() = runBlocking {
        val food = repo.addCustom("Callaloo")
        db.outboxDao().deleteEverything()

        repo.applyPending(emptyList())

        assertEquals("Callaloo", repo.byId(food.id)!!.name)
        assertTrue(repo.byId(food.id)!!.hidden)
    }

    @Test
    fun `published promotion withdraws the pending document but keeps its food id`() = runBlocking {
        val food = repo.addCustom("Callaloo")
        db.outboxDao().deleteEverything()

        repo.applyPublished(
            PublishedCatalogue("v1", listOf(food.id to CatalogueClaim("Callaloo", "Vegetable"))),
        )

        assertFalse(repo.byId(food.id)!!.custom)
        assertEquals(OutboxDrainer.ENTITY_FOOD_PENDING, db.outboxDao().pending().single().entityType)
        assertEquals(OutboxDrainer.OP_DELETE, db.outboxDao().pending().single().op)
    }

    @Test
    fun `the observed catalogue puts recently used foods first and still lists the rest`() = runBlocking {
        repo.seedIfNeeded()
        db.foodDao().touch("banana", t0)
        db.foodDao().touch("apple", t0 + 1_000)

        val listed = repo.observeCatalogue().first()

        assertEquals(listOf("apple", "banana"), listed.take(2).map { it.id })
        assertEquals(catalogue.foods.size, listed.size)
    }

    @Test
    fun `byIds returns only the foods asked for`() = runBlocking {
        repo.seedIfNeeded()
        assertEquals(
            setOf("banana", "apple"),
            repo.byIds(listOf("banana", "apple", "not-a-food")).map { it.id }.toSet(),
        )
    }
}
