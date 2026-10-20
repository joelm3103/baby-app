package app.babylog.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.local.AppDatabase
import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.data.local.OutboxEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The drain, against a real Room database and a fake server.
 *
 * The three properties that make a queue safe to retry are all here: the row is re-read at send time,
 * the intent leaves the queue only on a successful write, and a delete needs nothing but an id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxDrainerTest {

    private lateinit var db: AppDatabase
    private lateinit var gateway: FakeSyncGateway
    private lateinit var drainer: OutboxDrainer
    private val t0 = 1_760_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gateway = FakeSyncGateway()
        drainer = OutboxDrainer(
            outboxDao = db.outboxDao(),
            childDao = db.childDao(),
            eventDao = db.eventDao(),
            gateway = gateway,
            clock = Clock.fixed(Instant.ofEpochMilli(t0 + 500), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a queued write sends the row as it is now, not as it was when queued`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0))
        queue(OutboxDrainer.ENTITY_EVENT, "e1", OP_UPSERT)

        // The parent corrected the amount before the drain ran.
        db.eventDao().upsert(event("e1", amountMl = 150, updatedAt = t0 + 60_000))

        drainer.drain()

        assertEquals(150, gateway.eventWrites.single().amountMl)
    }

    @Test
    fun `a successful write empties the queue`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0))
        queue(OutboxDrainer.ENTITY_EVENT, "e1", OP_UPSERT)

        val result = drainer.drain()

        assertTrue(result.ok)
        assertEquals(1, result.written)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `a failed write keeps the intent, and the next drain sends it`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0))
        queue(OutboxDrainer.ENTITY_EVENT, "e1", OP_UPSERT)
        gateway.failAfter = 0

        val failure = drainer.drain()

        assertFalse(failure.ok)
        assertNotNull(failure.error)
        assertEquals(1, db.outboxDao().pending().size)

        gateway.failAfter = Int.MAX_VALUE
        val retry = drainer.drain()

        assertTrue(retry.ok)
        assertEquals(1, gateway.eventWrites.size)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `a failure part way through leaves only the unacked intents`() = runBlocking {
        db.eventDao().upsertAll(
            listOf(
                event("e1", amountMl = 90, updatedAt = t0),
                event("e2", amountMl = 120, updatedAt = t0),
                event("e3", amountMl = 150, updatedAt = t0),
            ),
        )
        queue(OutboxDrainer.ENTITY_EVENT, "e1", OP_UPSERT, queuedAt = t0)
        queue(OutboxDrainer.ENTITY_EVENT, "e2", OP_UPSERT, queuedAt = t0 + 1)
        queue(OutboxDrainer.ENTITY_EVENT, "e3", OP_UPSERT, queuedAt = t0 + 2)
        gateway.failAfter = 1

        val result = drainer.drain()

        assertEquals(1, result.written)
        assertEquals(listOf("e2", "e3"), db.outboxDao().pending().map { it.entityId })
    }

    @Test
    fun `a delete whose row still exists sends the row with its tombstone flag`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0, deleted = true))
        queue(OutboxDrainer.ENTITY_EVENT, "e1", OP_DELETE)

        drainer.drain()

        assertTrue(gateway.eventWrites.single().deleted)
        assertTrue(gateway.eventTombstones.isEmpty())
    }

    @Test
    fun `a delete whose row is already gone sends a tombstone, because a delete needs only an id`() =
        runBlocking {
            // The permanent-delete case: the row no longer exists to copy fields from.
            queue(OutboxDrainer.ENTITY_CHILD, "removed-child", OP_DELETE)

            drainer.drain()

            assertEquals(listOf("removed-child" to t0 + 500), gateway.childTombstones)
            assertTrue(gateway.childWrites.isEmpty())
        }

    @Test
    fun `an upsert pointing at a row that no longer exists is dropped, not sent`() = runBlocking {
        queue(OutboxDrainer.ENTITY_EVENT, "vanished", OP_UPSERT)

        val result = drainer.drain()

        assertEquals(1, result.skipped)
        assertEquals(0, gateway.writeCount)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `draining twice writes nothing the second time`() = runBlocking {
        db.eventDao().upsert(event("e1", amountMl = 90, updatedAt = t0))
        queue(OutboxDrainer.ENTITY_EVENT, "e1", OP_UPSERT)
        drainer.drain()

        val second = drainer.drain()

        assertEquals(0, second.written)
        assertEquals(1, gateway.eventWrites.size)
    }

    @Test
    fun `an entity type this phase does not sync is skipped and dropped`() = runBlocking {
        queue("FOOD", "banana", OP_UPSERT)

        val result = drainer.drain()

        assertEquals(1, result.skipped)
        assertTrue(db.outboxDao().pending().isEmpty())
    }

    @Test
    fun `a child is written from the child table`() = runBlocking {
        db.childDao().upsert(child("first-twin", updatedAt = t0))
        queue(OutboxDrainer.ENTITY_CHILD, "first-twin", OP_UPSERT)

        drainer.drain()

        assertEquals("First Twin", gateway.childWrites.single().name)
    }

    @Test
    fun `the drain never touches a row it was not asked about`() = runBlocking {
        db.eventDao().upsertAll(
            listOf(event("e1", amountMl = 90, updatedAt = t0), event("e2", amountMl = 120, updatedAt = t0)),
        )
        queue(OutboxDrainer.ENTITY_EVENT, "e2", OP_UPSERT)

        drainer.drain()

        assertEquals(listOf("e2"), gateway.eventWrites.map { it.id })
    }

    private suspend fun queue(
        entityType: String,
        entityId: String,
        op: String,
        queuedAt: Long = t0,
    ) {
        db.outboxDao().enqueue(OutboxEntity(entityType = entityType, entityId = entityId, op = op, queuedAt = queuedAt))
    }

    private fun child(id: String, updatedAt: Long) = ChildEntity(
        id = id,
        name = "First Twin",
        birthDateEpochDay = 20_454,
        sex = "BOY",
        updatedAt = updatedAt,
    )

    private fun event(
        id: String,
        amountMl: Int,
        updatedAt: Long,
        deleted: Boolean = false,
    ) = EventEntity(
        id = id,
        childId = "first-twin",
        type = "BOTTLE",
        startedAt = t0,
        amountMl = amountMl,
        createdBy = "parent-one",
        updatedAt = updatedAt,
        deleted = deleted,
    )

    private companion object {
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
    }
}
