package app.babylog.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.domain.DoseUnit
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The v1 -> v2 upgrade, on a database that already holds a parent's log.
 *
 * This is the test the additive-only rule exists for. Phase 11 gave a dose its own column, which is the
 * first time this app has changed its schema at all, and the thing that must never happen is a phone
 * losing the log it has been writing to for months. So the old rows must still be there afterwards, with
 * the new columns EMPTY rather than zeroed — "no dose was recorded" and "a dose of nothing" are
 * different claims about a child's medicine.
 *
 * The v1 database is built from the committed `schemas/.../1.json` itself, not from a hand-copied
 * CREATE TABLE: a second copy of the old schema in a test is a copy that can drift, and a migration test
 * that drifts tests nothing. That file is committed as source for exactly this reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private val t0 = 1_700_000_000_000L

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `a v1 log survives the upgrade and the dose columns arrive empty`() = runBlocking {
        createV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // The row written by the previous version, read back through the new schema.
            val old = db.eventDao().allIncludingDeleted().single { it.id == "old" }
            assertEquals("first-twin", old.childId)
            assertEquals(38.5, old.temperatureC!!, 0.0)
            assertEquals("Calpol", old.medication)
            // Empty, not zero: this row never recorded a dose, and a 0 would read as one.
            assertNull(old.doseAmount)
            assertNull(old.doseUnit)

            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the upgraded database accepts a dose and reads it back`() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            db.eventDao().upsert(
                EventEntity(
                    id = "dosed", childId = "first-twin", type = "HEALTH", startedAt = t0 + 1,
                    medication = "Calpol", doseAmount = 2.5, doseUnit = DoseUnit.MILLILITRES.name,
                    createdBy = "local", updatedAt = t0 + 1,
                ),
            )

            val read = db.eventDao().byId("dosed")
            assertNotNull(read)
            assertEquals(2.5, read!!.doseAmount!!, 0.0)
            assertEquals("MILLILITRES", read.doseUnit)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the current schema is exported, so the next migration can be tested`() {
        // Not ceremony: an unexported schema means the NEXT version cannot be tested against this one,
        // and the failure would only appear months later as an untestable migration.
        assertTrue(
            "expected the v2 schema to be exported and committed",
            schemaFile(2).exists(),
        )
    }

    /** Builds the v1 database exactly as version 1 of the app would have left it. */
    private fun createV1Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val v1 = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val database = JSONObject(schemaFile(1).readText()).getJSONObject("database")
            val entities = database.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                v1.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    v1.execSQL(
                        indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table),
                    )
                }
            }
            v1.execSQL(
                "INSERT INTO events (id, childId, type, startedAt, temperatureC, medication, " +
                    "createdBy, updatedAt, deleted) " +
                    "VALUES ('old', 'first-twin', 'HEALTH', $t0, 38.5, 'Calpol', 'local', $t0, 0)",
            )
            v1.execSQL("PRAGMA user_version = 1")
        } finally {
            v1.close()
        }
    }

    /**
     * The exported schema file for a version. Unit tests run from the module directory, but the layout
     * differs between Gradle and an IDE, so both candidates are tried rather than assumed.
     */
    private fun schemaFile(version: Int): File {
        val relative = "schemas/app.babylog.data.local.AppDatabase/$version.json"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: File(relative)
    }
}
