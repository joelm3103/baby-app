package app.babylog.data.backup

import app.babylog.data.local.ChildEntity
import app.babylog.data.local.EventEntity
import app.babylog.data.local.FoodEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The backup round trip is the whole contract: whatever the app wrote must read back byte-for-byte
 * equal, tombstones and custom foods included. Robolectric supplies a real `org.json`, which the plain
 * JVM test classpath stubs out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {

    private val t0 = 1_700_000_000_000L

    private val children = listOf(
        ChildEntity(
            id = "first-twin", name = "First Twin", birthDateEpochDay = LocalDate.of(2025, 12, 31).toEpochDay(),
            gestationalAgeDays = 210, sex = "BOY", useAdjustedAge = true, sortOrder = 0,
            deleted = false, updatedAt = t0,
        ),
        // A tombstoned child must survive the trip, or a restore would resurrect a removed child.
        ChildEntity(
            id = "second-twin", name = "Second Twin", birthDateEpochDay = LocalDate.of(2025, 12, 31).toEpochDay(),
            gestationalAgeDays = 210, sex = "BOY", useAdjustedAge = true, sortOrder = 1,
            deleted = true, updatedAt = t0,
        ),
    )

    private val events = listOf(
        EventEntity(
            id = "e1", childId = "first-twin", type = "BOTTLE", startedAt = t0, amountMl = 120,
            formulaBrand = "Aptamil", createdBy = "local", updatedAt = t0,
        ),
        // Every optional left null, so the round trip proves absent keys read back as null.
        EventEntity(id = "e2", childId = "first-twin", type = "DIAPER", startedAt = t0 + 1, createdBy = "local", updatedAt = t0 + 1),
        EventEntity(
            id = "e3", childId = "second-twin", type = "SOLIDS", startedAt = t0 + 2, mealSlot = "BREAKFAST",
            foodIdsCsv = "banana,mango-lassi", reaction = "LOVED_IT", createdBy = "local",
            updatedAt = t0 + 2, deleted = true,
        ),
    )

    private val foods = listOf(
        FoodEntity(id = "banana", name = "Banana", category = "Fruit", lastUsedAt = t0, custom = false),
        // The row that cannot be regenerated from the bundled asset, with a null lastUsedAt.
        FoodEntity(id = "mango-lassi", name = "Mango lassi", category = "Custom", lastUsedAt = null, custom = true),
    )

    private fun snapshot() = BackupSnapshot(exportedAt = t0, children = children, events = events, foods = foods)

    @Test
    fun `a snapshot round trips exactly`() {
        val read = Backup.read(Backup.write(snapshot()))

        assertEquals(BACKUP_SCHEMA, read.schema)
        assertEquals(t0, read.exportedAt)
        assertEquals(children, read.children)
        assertEquals(events, read.events)
        assertEquals(foods, read.foods)
    }

    @Test
    fun `the file is indented JSON tagged with the schema`() {
        val json = Backup.write(snapshot())

        assertTrue("expected pretty-printed JSON", json.contains("\n"))
        assertTrue(json.contains("\"app\": \"babylog\""))
        assertTrue(json.contains("\"schema\": $BACKUP_SCHEMA"))
    }

    @Test
    fun `a newer schema is refused rather than partially applied`() {
        val newer = Backup.write(snapshot()).replace("\"schema\": $BACKUP_SCHEMA", "\"schema\": ${BACKUP_SCHEMA + 1}")
        val failure = runCatching { Backup.read(newer) }.exceptionOrNull()

        assertTrue("expected UnsupportedBackupException, got $failure", failure is UnsupportedBackupException)
        assertEquals(BACKUP_SCHEMA + 1, (failure as UnsupportedBackupException).found)
    }

    @Test
    fun `a file that is not a backup is rejected`() {
        val failure = runCatching { Backup.read("{\"hello\":\"world\"}") }.exceptionOrNull()
        assertTrue("expected an IllegalArgumentException, got $failure", failure is IllegalArgumentException)
    }
}
