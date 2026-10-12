package app.babylog.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.domain.Sex
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

/**
 * The DAO contract, verified on the JVM. This is why Robolectric is a dependency: the emulator was
 * deliberately skipped, so without this a schema mistake would only surface on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private val t0 = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    // ---- children ----------------------------------------------------------

    @Test
    fun `children are observed and soft delete hides them`() = runBlocking {
        db.childDao().upsertAll(listOf(child("first-twin", order = 0), child("second-twin", order = 1)))
        assertEquals(listOf("first-twin", "second-twin"), db.childDao().observeAll().first().map { it.id })

        db.childDao().softDelete("first-twin", t0 + 1)
        assertEquals(listOf("second-twin"), db.childDao().observeAll().first().map { it.id })
        assertEquals(1, db.childDao().count())
        // the row still exists, because sync needs the tombstone
        assertNotNull(db.childDao().byId("first-twin"))
    }

    // ---- events ------------------------------------------------------------

    @Test
    fun `events round trip through the DAO`() = runBlocking {
        val e = event("e1", "first-twin").copy(amountMl = 120, formulaBrand = "Aptamil")
        db.eventDao().upsert(e)
        val back = db.eventDao().byId("e1")
        assertEquals(e, back)
    }

    @Test
    fun `soft delete then restore returns the event to the timeline`() = runBlocking {
        db.eventDao().upsert(event("e1", "first-twin"))
        db.eventDao().softDelete("e1", t0 + 1)
        assertTrue(db.eventDao().observeAll().first().isEmpty())

        db.eventDao().restore("e1", t0 + 2) // the undo snackbar
        assertEquals(listOf("e1"), db.eventDao().observeAll().first().map { it.id })
    }

    @Test
    fun `reads filter by child`() = runBlocking {
        db.eventDao().upsertAll(listOf(event("k1", "first-twin"), event("l1", "second-twin"), event("k2", "first-twin")))
        assertEquals(
            listOf("k1", "k2"),
            db.eventDao().observeForChildren(listOf("first-twin")).first().map { it.id }.sorted(),
        )
        // Both mode is one query with two ids, not two queries
        assertEquals(3, db.eventDao().observeForChildren(listOf("first-twin", "second-twin")).first().size)
    }

    @Test
    fun `latest respects both type and child`() = runBlocking {
        db.eventDao().upsertAll(
            listOf(
                event("old", "first-twin", at = t0).copy(type = "BOTTLE"),
                event("new", "first-twin", at = t0 + 1000).copy(type = "BOTTLE"),
                event("other", "second-twin", at = t0 + 2000).copy(type = "BOTTLE"),
                event("diaper", "first-twin", at = t0 + 3000).copy(type = "DIAPER"),
            ),
        )
        assertEquals("new", db.eventDao().observeLatest("BOTTLE", listOf("first-twin")).first()?.id)
        assertEquals("other", db.eventDao().observeLatest("BOTTLE", listOf("second-twin")).first()?.id)
        assertEquals("diaper", db.eventDao().observeLatest("DIAPER", listOf("first-twin")).first()?.id)
        assertNull(db.eventDao().observeLatest("GROWTH", listOf("first-twin")).first())
    }

    @Test
    fun `between is inclusive of the start and exclusive of the end`() = runBlocking {
        db.eventDao().upsertAll(
            listOf(event("a", "first-twin", at = t0), event("b", "first-twin", at = t0 + 100), event("c", "first-twin", at = t0 + 200)),
        )
        val got = db.eventDao().between(listOf("first-twin"), t0 + 100, t0 + 200).map { it.id }
        assertEquals(listOf("b"), got)
    }

    @Test
    fun `a running sleep timer is the only thing observeRunning returns`() = runBlocking {
        db.eventDao().upsertAll(
            listOf(
                event("running", "first-twin").copy(type = "SLEEP", endedAt = null),
                event("done", "first-twin").copy(type = "SLEEP", endedAt = t0 + 60_000),
                event("bottle", "first-twin").copy(type = "BOTTLE", endedAt = null),
            ),
        )
        // note: the query intentionally does not filter by type - a bottle with no end is a bug we
        // would rather see than hide
        assertEquals(listOf("bottle", "running"), db.eventDao().observeRunning().first().map { it.id }.sorted())
    }

    @Test
    fun `recent by type powers the card rows and is capped`() = runBlocking {
        db.eventDao().upsertAll((0 until 8).map { event("e$it", "first-twin", at = t0 + it * 1000).copy(type = "BOTTLE") })
        assertEquals(3, db.eventDao().observeRecentByType("BOTTLE", listOf("first-twin"), 3).first().size)
    }

    // ---- food catalogue ----------------------------------------------------

    @Test
    fun `food search is case insensitive and recent first follows use`() = runBlocking {
        db.foodDao().upsertAll(
            listOf(FoodEntity("banana", "Banana", "FRUIT"), FoodEntity("oats", "Oats", "GRAIN")),
        )
        assertEquals(listOf("banana"), db.foodDao().search("ban").map { it.id })
        assertEquals(listOf("banana"), db.foodDao().search("BANANA").map { it.id })

        assertTrue(db.foodDao().recent(10).isEmpty()) // nothing used yet
        db.foodDao().touch("oats", t0)
        db.foodDao().touch("banana", t0 + 1000)
        assertEquals(listOf("banana", "oats"), db.foodDao().recent(10).map { it.id })

        assertEquals(listOf("banana", "oats"), db.foodDao().byIds(listOf("banana", "oats")).map { it.id }.sorted())
        assertEquals(2, db.foodDao().count())
    }

    // ---- outbox ------------------------------------------------------------

    @Test
    fun `writes queue in the outbox and drain cleanly`() = runBlocking {
        val id1 = db.outboxDao().enqueue(outbox("EVENT", "e1"))
        db.outboxDao().enqueue(outbox("EVENT", "e2", at = t0 + 5))
        assertEquals(listOf("e1", "e2"), db.outboxDao().pending().map { it.entityId })
        assertEquals(2, db.outboxDao().observeCount().first())

        db.outboxDao().delete(id1)
        db.outboxDao().deleteAll(db.outboxDao().pending().map { it.id })
        assertEquals(0, db.outboxDao().observeCount().first())
    }

    // ---- helpers -----------------------------------------------------------

    private fun child(id: String, order: Int) = ChildEntity(
        id = id, name = id.replaceFirstChar { it.uppercase() }, birthDateEpochDay = 20_457L,
        gestationalAgeDays = 210, sex = Sex.BOY.name, useAdjustedAge = true,
        sortOrder = order, deleted = false, updatedAt = t0,
    )

    private fun event(id: String, childId: String, at: Long = t0) = EventEntity(
        id = id, childId = childId, type = "BOTTLE", startedAt = at,
        createdBy = "u1", updatedAt = t0,
    )

    private fun outbox(type: String, entityId: String, at: Long = t0) = OutboxEntity(
        entityType = type, entityId = entityId, op = "UPSERT", queuedAt = at,
    )
}
