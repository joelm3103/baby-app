package app.babylog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChildEntity::class, EventEntity::class, FoodEntity::class, OutboxEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun eventDao(): EventDao
    abstract fun foodDao(): FoodDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val NAME = "babylog.db"

        /**
         * ADDITIVE-ONLY, and this is a hard rule rather than a preference (see PLAN.md).
         *
         * Once real data lives on two phones: new columns must be nullable or defaulted, an existing
         * column's meaning must never be repurposed, and `fallbackToDestructiveMigration` must never
         * appear here. Every migration gets a test that opens a v(N) database and asserts the v(N+1)
         * read succeeds with the old rows intact.
         *
         * Exported schemas under `app/schemas/` are what make those tests possible — they are
         * committed on purpose, not build output.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Phase 11 gave a dose its own home, so that History can answer "which medicines"
                // without carrying the amounts along with it. Both columns are nullable: every row
                // written before this migration has no dose, and "no dose recorded" is the truth for
                // them rather than a zero.
                db.execSQL("ALTER TABLE events ADD COLUMN doseAmount REAL")
                db.execSQL("ALTER TABLE events ADD COLUMN doseUnit TEXT")
            }
        }

        /**
         * v2 -> v3: a dirty nappy gained a colour and a consistency (Phase 13.3, `0.12.0`).
         *
         * Additive and nullable, with NO back-fill: every row written before this migration recorded
         * neither, and NULL is the truth about it. Writing a default colour would invent a clinical
         * observation nobody made.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN diaperColoursCsv TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN diaperConsistencyCsv TEXT")
            }
        }

        /**
         * v3 -> v4: the solids catalogue gained a published half (ADR-0002).
         *
         * Both columns are additive, and both defaults are the truth about every existing row: a food
         * written before this migration has no local rename, and was not hidden. Nothing here writes a
         * name — inventing one would rename a food on a parent's picker without them asking.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE foods ADD COLUMN localName TEXT")
                db.execSQL("ALTER TABLE foods ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4 -> v5: a bottle gained "left behind" (Phase 26, `1.7.0`).
         *
         * Additive and nullable, with NO back-fill: every row written before this migration recorded no
         * leftover, and NULL is the truth about it. Writing 0 would claim every past bottle was finished
         * to the last millilitre, which is a measurement nobody made — and it would put that claim on the
         * wastage report the phase adds.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN leftBehindMl INTEGER")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
