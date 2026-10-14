package app.babylog.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.local.AppDatabase
import app.babylog.data.local.ChildEntity
import app.babylog.data.local.FoodEntity
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.domain.Sex
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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

/** The write path, against a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: EventRepository
    private lateinit var clock: MutableClock
    private var idCounter = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(T0)
        repo = EventRepository(
            eventDao = db.eventDao(),
            outboxDao = db.outboxDao(),
            foodDao = db.foodDao(),
            clock = clock,
            newId = { "id-${idCounter++}" },
        )
        runBlocking {
            db.childDao().upsertAll(
                listOf(child("first-twin", 0), child("second-twin", 1)),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    // ---- Both mode: the twin case ----------------------------------------

    @Test
    fun `logging for both children writes one row each with the same timestamp`() = runBlocking {
        val created = repo.log(
            childIds = listOf("first-twin", "second-twin"),
            draft = EventDraft(type = EventType.BOTTLE, startedAt = T0, amountMl = 120),
            createdBy = "owner",
        )
        assertEquals(2, created.size)
        assertEquals(setOf("first-twin", "second-twin"), created.map { it.childId }.toSet())
        assertEquals(2, created.map { it.id }.toSet().size)                 // distinct ids
        assertEquals(1, created.map { it.startedAt }.toSet().size)          // identical timestamps
        assertEquals(2, repo.observeForChildren(listOf("first-twin", "second-twin")).first().size)
        assertEquals(120, repo.observeLatest(EventType.BOTTLE, listOf("second-twin")).first()?.amountMl)
    }

    @Test
    fun `logging for one child writes exactly one row`() = runBlocking {
        val created = repo.log(
            listOf("first-twin"),
            EventDraft(type = EventType.DIAPER, startedAt = T0, diaperKind = DiaperKind.MIXED),
            createdBy = "owner",
        )
        assertEquals(1, created.size)
        assertEquals(1, repo.observeForChildren(listOf("first-twin")).first().size)
        assertTrue(repo.observeForChildren(listOf("second-twin")).first().isEmpty())
    }

    @Test
    fun `duplicate child ids in the request do not double write`() = runBlocking {
        val created = repo.log(
            listOf("first-twin", "first-twin"),
            EventDraft(type = EventType.BOTTLE, startedAt = T0),
            createdBy = "owner",
        )
        assertEquals(1, created.size)
    }

    @Test
    fun `every write queues exactly one outbox row`() = runBlocking {
        repo.log(listOf("first-twin", "second-twin"), EventDraft(type = EventType.BOTTLE, startedAt = T0), "owner")
        assertEquals(2, db.outboxDao().observeCount().first())
        assertTrue(db.outboxDao().pending().all { it.op == "UPSERT" && it.entityType == "EVENT" })
    }

    // ---- delete and undo --------------------------------------------------

    @Test
    fun `soft delete hides the event and undo brings it back`() = runBlocking {
        val event = repo.log(listOf("first-twin"), EventDraft(type = EventType.BOTTLE, startedAt = T0), "owner").single()

        val tombstone = repo.softDelete(event)
        assertTrue(tombstone.deleted)
        assertTrue(repo.observeForChildren(listOf("first-twin")).first().isEmpty())

        repo.undoDelete(tombstone)
        assertEquals(1, repo.observeForChildren(listOf("first-twin")).first().size)
        // the delete and the undo are both queued, so the other phone sees the end state
        assertEquals(3, db.outboxDao().observeCount().first())
        assertEquals(listOf("UPSERT", "DELETE", "UPSERT"), db.outboxDao().pending().map { it.op })
    }

    // ---- editing one row in place ----------------------------------------

    @Test
    fun `editing a row updates it in place and leaves its Both-mode twin alone`() = runBlocking {
        val created = repo.log(
            listOf("first-twin", "second-twin"),
            EventDraft(type = EventType.BOTTLE, startedAt = T0, amountMl = 120),
            createdBy = "owner",
        )
        val firstRow = created.first { it.childId == "first-twin" }

        clock.advanceMinutes(10)
        val edited = repo.update(
            EventDraft(type = EventType.BOTTLE, startedAt = T0, amountMl = 150)
                .applyTo(firstRow, updatedAt = clock.millis()),
        )

        assertEquals(firstRow.id, edited.id)
        val rows = repo.observeForChildren(listOf("first-twin", "second-twin")).first()
        assertEquals(2, rows.size)                                          // edited, never re-inserted
        assertEquals(150, rows.first { it.childId == "first-twin" }.amountMl)
        assertEquals(120, rows.first { it.childId == "second-twin" }.amountMl)     // the twin is untouched
        // One queue entry per write: two logs plus this edit. A re-insert would have shown as a 4th row.
        assertEquals(3, db.outboxDao().observeCount().first())
    }

    @Test
    fun `an edit cannot move a row to another child`() = runBlocking {
        val event = repo.log(
            listOf("second-twin"),
            EventDraft(type = EventType.BOTTLE, startedAt = T0, amountMl = 120),
            "owner",
        ).single()

        val edited = repo.update(
            EventDraft(type = EventType.BOTTLE, startedAt = T0, amountMl = 90).applyTo(event, 1L),
        )

        assertEquals("second-twin", edited.childId)
        assertTrue(repo.observeForChildren(listOf("first-twin")).first().isEmpty())
    }

    // ---- sleep timer ------------------------------------------------------

    @Test
    fun `a sleep timer starts for both and does not stack`() = runBlocking {
        val started = repo.startSleepTimer(listOf("first-twin", "second-twin"), "owner")
        assertEquals(2, started.size)
        assertTrue(started.all { it.isRunning })

        // second tap must not create a parallel timer
        assertTrue(repo.startSleepTimer(listOf("first-twin", "second-twin"), "owner").isEmpty())
        assertEquals(2, repo.observeRunning().first().size)
    }

    @Test
    fun `a timer starts for the free child only`() = runBlocking {
        repo.startSleepTimer(listOf("first-twin"), "owner")
        val started = repo.startSleepTimer(listOf("first-twin", "second-twin"), "owner")
        assertEquals(listOf("second-twin"), started.map { it.childId })
    }

    @Test
    fun `stopping a timer records a positive duration`() = runBlocking {
        repo.startSleepTimer(listOf("first-twin"), "owner")
        clock.advanceMinutes(95)

        val stopped = repo.stopSleepTimer("first-twin")
        assertNotNull(stopped)
        assertEquals(95 * 60_000L, stopped!!.durationMs)
        assertTrue(repo.observeRunning().first().isEmpty())
    }

    @Test
    fun `stopping a timer that is not running is a no-op`() = runBlocking {
        assertNull(repo.stopSleepTimer("first-twin"))
    }

    // ---- solids touch the catalogue --------------------------------------

    @Test
    fun `logging solids marks the foods as recently used`() = runBlocking {
        db.foodDao().upsertAll(
            listOf(FoodEntity("banana", "Banana", "FRUIT"), FoodEntity("oats", "Oats", "GRAIN")),
        )
        assertTrue(db.foodDao().recent(10).isEmpty())

        clock.advanceMinutes(5)
        repo.log(
            listOf("first-twin", "second-twin"),
            EventDraft(type = EventType.SOLIDS, startedAt = clock.millis(), foodIds = listOf("banana", "oats")),
            "owner",
        )
        assertEquals(listOf("banana", "oats"), db.foodDao().recent(10).map { it.id }.sorted())
    }

    // ---- helpers ----------------------------------------------------------

    private class MutableClock(private var millis: Long) : Clock() {
        fun advanceMinutes(minutes: Long) { millis += minutes * 60_000L }
        override fun millis(): Long = millis
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private companion object { const val T0 = 1_760_000_000_000L }

    private fun child(id: String, order: Int) = ChildEntity(
        id = id, name = id, birthDateEpochDay = LocalDate.of(2025, 12, 31).toEpochDay(),
        gestationalAgeDays = 210, sex = Sex.BOY.name, useAdjustedAge = true,
        sortOrder = order, deleted = false, updatedAt = T0,
    )
}
