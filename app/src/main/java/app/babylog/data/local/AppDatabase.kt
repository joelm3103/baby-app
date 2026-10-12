package app.babylog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [ChildEntity::class, EventEntity::class, FoodEntity::class, OutboxEntity::class],
    version = 1,
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
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
