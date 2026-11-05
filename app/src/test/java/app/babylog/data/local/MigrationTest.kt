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
import org.junit.Assert.assertFalse
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

            // The chain now runs to v5 (Phase 26), so this reads 5 rather than 4. The claim is the same
            // one it always made: a v1 log survives every migration this build carries.
            assertEquals(5, db.openHelper.readableDatabase.version)
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
    fun `a v2 log survives the upgrade and the nappy-detail columns arrive empty`() = runBlocking {
        createV2Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Phase 13.3 gave a dirty nappy a colour and a consistency. The row the previous build wrote
            // must read back unchanged: `MIXED` still means both answers were yes, and the two new columns
            // are NULL because that change recorded neither. A default would be an invented observation.
            val old = db.eventDao().allIncludingDeleted().single { it.id == "nappy" }
            assertEquals("first-twin", old.childId)
            assertEquals("DIAPER", old.type)
            assertEquals("MIXED", old.diaperKind)
            assertEquals(true, old.rash)
            assertNull(old.diaperColoursCsv)
            assertNull(old.diaperConsistencyCsv)

            assertEquals(5, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the upgraded database accepts a colour and a consistency and reads them back`() = runBlocking {
        createV2Database()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            db.eventDao().upsert(
                EventEntity(
                    id = "detailed", childId = "first-twin", type = "DIAPER", startedAt = t0 + 1,
                    diaperKind = "DIRTY", diaperColoursCsv = "BROWN,GREEN",
                    diaperConsistencyCsv = "MUSHY,PEBBLES",
                    createdBy = "local", updatedAt = t0 + 1,
                ),
            )

            val read = db.eventDao().byId("detailed")
            assertNotNull(read)
            assertEquals("BROWN,GREEN", read!!.diaperColoursCsv)
            assertEquals("MUSHY,PEBBLES", read.diaperConsistencyCsv)
            assertEquals(5, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the v3 log and catalogue survive the upgrade with only local v4 opinions added`() = runBlocking {
        createDatabaseAt(3) { v3 ->
            v3.execSQL(
                "INSERT INTO events (id, childId, type, startedAt, createdBy, updatedAt, deleted) " +
                    "VALUES ('old', 'first-twin', 'SOLIDS', $t0, 'local', $t0, 0)",
            )
            v3.execSQL(
                "INSERT INTO foods (id, name, category, lastUsedAt, custom) " +
                    "VALUES ('callaloo', 'Callaloo', 'Custom', NULL, 1)",
            )
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("first-twin", db.eventDao().allIncludingDeleted().single { it.id == "old" }.childId)
            val food = db.foodDao().byId("callaloo")
            assertNotNull(food)
            assertEquals("Callaloo", food!!.name)
            assertNull(food.localName)
            assertFalse(food.hidden)
            assertEquals(5, db.openHelper.readableDatabase.version)

            db.foodDao().upsert(food.copy(localName = "Kallaloo", hidden = true))
            val curated = db.foodDao().byId("callaloo")!!
            assertEquals("Kallaloo", curated.localName)
            assertTrue(curated.hidden)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a v4 log survives the upgrade and its bottles gain no invented leftover`() = runBlocking {
        createV4Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Phase 26 added "left behind" beside the amount. The row the previous build wrote must read
            // back unchanged, and the new column must be NULL: a back-filled 0 would claim every bottle
            // ever logged was finished to the last millilitre, which is a measurement nobody made.
            val old = db.eventDao().allIncludingDeleted().single { it.id == "feed" }
            assertEquals("BOTTLE", old.type)
            assertEquals(150, old.amountMl)
            assertNull(old.leftBehindMl)

            assertEquals(5, db.openHelper.readableDatabase.version)

            // And the upgraded table accepts a leftover, which is the whole point of the column.
            db.eventDao().upsert(
                EventEntity(
                    id = "left", childId = "first-twin", type = "BOTTLE", startedAt = t0 + 1,
                    amountMl = 120, leftBehindMl = 30, createdBy = "local", updatedAt = t0 + 1,
                ),
            )
            assertEquals(30, db.eventDao().byId("left")!!.leftBehindMl)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the current schema is exported, so the next migration can be tested`() {
        // Not ceremony: an unexported schema means the NEXT version cannot be tested against this one,
        // and the failure would only appear months later as an untestable migration. Both older versions
        // are asserted too - v2 is what the v2 -> v3 test above is built from.
        assertTrue("expected the v2 schema to be exported and committed", schemaFile(2).exists())
        assertTrue("expected the v3 schema to be exported and committed", schemaFile(3).exists())
        assertTrue("expected the v4 schema to be exported and committed", schemaFile(4).exists())
        assertTrue("expected the v5 schema to be exported and committed", schemaFile(5).exists())
    }

    /** Builds the v1 database exactly as version 1 of the app would have left it. */
    private fun createV1Database() {
        createDatabaseAt(1) { v1 ->
            v1.execSQL(
                "INSERT INTO events (id, childId, type, startedAt, temperatureC, medication, " +
                    "createdBy, updatedAt, deleted) " +
                    "VALUES ('old', 'first-twin', 'HEALTH', $t0, 38.5, 'Calpol', 'local', $t0, 0)",
            )
        }
    }

    /** Builds the v2 database - the build that shipped 0.11.0 - with a logged nappy in it. */
    private fun createV2Database() {
        createDatabaseAt(2) { v2 ->
            v2.execSQL(
                "INSERT INTO events (id, childId, type, startedAt, diaperKind, rash, " +
                    "createdBy, updatedAt, deleted) " +
                    "VALUES ('nappy', 'first-twin', 'DIAPER', $t0, 'MIXED', 1, 'local', $t0, 0)",
            )
        }
    }

    /** Builds the v4 database - the build that shipped `1.6.0` - with a logged feed in it. */
    private fun createV4Database() {
        createDatabaseAt(4) { v4 ->
            v4.execSQL(
                "INSERT INTO events (id, childId, type, startedAt, amountMl, " +
                    "createdBy, updatedAt, deleted) " +
                    "VALUES ('feed', 'first-twin', 'BOTTLE', $t0, 150, 'local', $t0, 0)",
            )
        }
    }

    /**
     * Builds the database for a version from the committed schema JSON, then runs [populate] against it.
     *
     * The schema file, never a hand-copied CREATE TABLE: a second copy of an old schema in a test is a copy
     * that can drift, and a migration test that drifts tests nothing.
     */
    private fun createDatabaseAt(version: Int, populate: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val database = JSONObject(schemaFile(version).readText()).getJSONObject("database")
            val entities = database.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table),
                    )
                }
            }
            populate(db)
            db.execSQL("PRAGMA user_version = $version")
        } finally {
            db.close()
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
