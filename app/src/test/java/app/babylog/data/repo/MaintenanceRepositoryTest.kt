package app.babylog.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.backup.Backup
import app.babylog.data.backup.UnsupportedBackupException
import app.babylog.data.local.AppDatabase
import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.data.local.FoodEntity
import app.babylog.data.local.OutboxEntity
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backup, restore, archive and permanent delete, against a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaintenanceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MaintenanceRepository
    private val t0 = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = MaintenanceRepository(db, clock = Clock.fixed(java.time.Instant.ofEpochMilli(t0), java.time.ZoneOffset.UTC))
        runBlocking { seed() }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.childDao().upsertAll(
            listOf(
                child("first-twin", order = 0, deleted = false),
                child("second-twin", order = 1, deleted = false),
            ),
        )
        db.eventDao().upsertAll(
            listOf(
                event("e1", "first-twin", deleted = false),
                event("e2", "first-twin", deleted = true),
                event("e3", "second-twin", deleted = false),
            ),
        )
        db.foodDao().upsertAll(
            listOf(
                FoodEntity(id = "banana", name = "Banana", category = "Fruit"),
                FoodEntity(id = "mango-lassi", name = "Mango lassi", category = "Custom", custom = true),
            ),
        )
    }

    // ---- export ------------------------------------------------------------

    @Test
    fun `a snapshot carries tombstones and custom foods`() = runBlocking {
        // Insert a tombstone here, so the test proves deleted rows reach the backup.
        db.childDao().upsert(child("removed", order = 2, deleted = true))

        val snap = repo.snapshot()

        assertEquals(listOf("first-twin", "removed", "second-twin"), snap.children.map { it.id }.sorted())
        assertTrue("a tombstoned child must be in the backup", snap.children.single { it.id == "removed" }.deleted)
        assertEquals(listOf("e1", "e2", "e3"), snap.events.map { it.id }.sorted())
        assertTrue(snap.events.single { it.id == "e2" }.deleted)
        assertEquals(listOf("banana", "mango-lassi"), snap.foods.map { it.id }.sorted())
        assertTrue(snap.foods.single { it.id == "mango-lassi" }.custom)
    }

    // ---- restore -----------------------------------------------------------

    @Test
    fun `replaceAll rebuilds the database from the file`() = runBlocking {
        val file = repo.exportJson()

        // Wreck the local state the way a wrong import would, then restore.
        repo.deleteChildPermanently("second-twin")
        db.foodDao().deleteAll()
        assertNull(db.childDao().byId("second-twin"))

        repo.replaceAll(file)

        assertEquals(listOf("first-twin", "second-twin"), db.childDao().allIncludingDeleted().map { it.id }.sorted())
        assertEquals(listOf("e1", "e2", "e3"), db.eventDao().allIncludingDeleted().map { it.id }.sorted())
        assertEquals(setOf("banana", "mango-lassi"), db.foodDao().all().map { it.id }.toSet())
    }

    @Test
    fun `replaceAll wipes rows that are not in the file`() = runBlocking {
        val file = Backup.write(
            app.babylog.data.backup.BackupSnapshot(
                exportedAt = t0,
                children = listOf(child("new", order = 0, deleted = false)),
                events = emptyList(),
                foods = emptyList(),
            ),
        )

        repo.replaceAll(file)

        assertEquals(listOf("new"), db.childDao().allIncludingDeleted().map { it.id }.sorted())
        assertEquals(0, db.eventDao().allIncludingDeleted().size)
        assertEquals(0, db.foodDao().all().size)
    }

    @Test
    fun `replaceAll refuses a newer schema and leaves the database untouched`() = runBlocking {
        val newer = repo.exportJson().replace("\"schema\": 1", "\"schema\": 2")

        val failure = runCatching { repo.replaceAll(newer) }.exceptionOrNull()

        assertTrue("expected UnsupportedBackupException, got $failure", failure is UnsupportedBackupException)
        assertEquals(2, db.childDao().allIncludingDeleted().size)
        assertEquals(3, db.eventDao().allIncludingDeleted().size)
    }

    // ---- archive -----------------------------------------------------------

    @Test
    fun `archiving hides the child and their history but keeps every row`() = runBlocking {
        repo.archiveChild("first-twin")

        assertEquals(listOf("second-twin"), db.childDao().observeAll().first().map { it.id })
        assertEquals(0, db.eventDao().observeForChildren(listOf("first-twin")).first().size)
        // Still there for a backup or an undo.
        assertEquals(2, db.childDao().allIncludingDeleted().size)
        assertEquals(3, db.eventDao().allIncludingDeleted().size)
        assertTrue(db.eventDao().allIncludingDeleted().filter { it.childId == "first-twin" }.all { it.deleted })
    }

    // ---- permanent delete --------------------------------------------------

    @Test
    fun `permanent delete removes the child, their events and their outbox intent`() = runBlocking {
        db.outboxDao().enqueue(OutboxEntity(entityType = "EVENT", entityId = "e1", op = "UPSERT", queuedAt = t0))
        db.outboxDao().enqueue(OutboxEntity(entityType = "EVENT", entityId = "e3", op = "UPSERT", queuedAt = t0))

        repo.deleteChildPermanently("first-twin")

        assertNull(db.childDao().byId("first-twin"))
        assertEquals(listOf("second-twin"), db.childDao().allIncludingDeleted().map { it.id }.sorted())
        assertEquals(listOf("e3"), db.eventDao().allIncludingDeleted().map { it.id }.sorted())
        // firstTwin's queued write is gone; secondTwin's survives.
        assertEquals(listOf("e3"), db.outboxDao().pending().map { it.entityId })
    }

    private fun child(id: String, order: Int, deleted: Boolean) = ChildEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        birthDateEpochDay = LocalDate.of(2025, 12, 31).toEpochDay(),
        gestationalAgeDays = 210,
        sex = "BOY",
        sortOrder = order,
        deleted = deleted,
        updatedAt = t0,
    )

    private fun event(id: String, childId: String, deleted: Boolean) = EventEntity(
        id = id,
        childId = childId,
        type = "BOTTLE",
        startedAt = t0,
        amountMl = 120,
        createdBy = "local",
        updatedAt = t0,
        deleted = deleted,
    )
}
