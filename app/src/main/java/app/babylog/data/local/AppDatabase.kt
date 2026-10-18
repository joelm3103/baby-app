package app.babylog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChildEntity::class, EventEntity::class, FoodEntity::class, OutboxEntity::class],
    version = 2,
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

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
