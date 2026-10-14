package app.babylog.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.local.AppDatabase
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-run seed must run once and never again. The old guard was "is the children table empty",
 * which a parent makes true again by removing their children — and the app would resurrect the children
 * on the next launch. This is that exact scenario.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChildSeedOnceTest {

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepository
    private lateinit var children: ChildRepository
    private lateinit var maintenance: MaintenanceRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // A UNIQUE DataStore file per test: DataStore refuses two live instances for one file, and the
        // app's own preferences file is shared across this JVM.
        tempDir = File(context.cacheDir, "seed-once-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "settings.preferences_pb")
        }
        settings = SettingsRepository(store)
        children = ChildRepository(db.childDao(), Clock.systemUTC())
        maintenance = MaintenanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun `the seed runs once, even after every child is permanently removed`() = runBlocking {
        assertTrue("the first run should seed", settings.seedChildrenOnce { children.seedIfEmpty() })
        assertEquals(2, db.childDao().count())

        maintenance.deleteChildPermanently("first-twin")
        maintenance.deleteChildPermanently("second-twin")
        assertEquals(0, db.childDao().totalCount())

        assertFalse("the seed must never run twice", settings.seedChildrenOnce { children.seedIfEmpty() })
        assertEquals(0, db.childDao().count())
        assertTrue(settings.childSeedDone.first())
    }

    @Test
    fun `archiving every child does not re-seed either`() = runBlocking {
        settings.seedChildrenOnce { children.seedIfEmpty() }

        maintenance.archiveChild("first-twin")
        maintenance.archiveChild("second-twin")

        assertFalse(settings.seedChildrenOnce { children.seedIfEmpty() })
        // Only the two tombstones remain; no fresh the children were created.
        assertEquals(2, db.childDao().totalCount())
        assertEquals(0, db.childDao().count())
    }
}
