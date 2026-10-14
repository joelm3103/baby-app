package app.babylog.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import app.babylog.domain.NightWindow
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stored preferences whose DEFAULTS drive behaviour: the night window behind the History diaper
 * split, and the bottle presets behind the logging sheet's chips. A typo'd default would silently move
 * every overnight diaper into the wrong bucket, or offer chips nobody uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A unique file per test: DataStore refuses two live instances for one file.
        tempDir = File(context.cacheDir, "settings-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "settings.preferences_pb")
        }
        settings = SettingsRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
    }

    // ---- night window ------------------------------------------------------

    @Test
    fun `night defaults to 18-06`() = runBlocking {
        assertEquals(NightWindow(18, 6), settings.nightWindow.first())
    }

    @Test
    fun `a changed night window round-trips, and hours are clamped to 0-23`() = runBlocking {
        settings.setNightStartHour(20)
        settings.setNightEndHour(8)
        assertEquals(NightWindow(20, 8), settings.nightWindow.first())

        settings.setNightStartHour(99)
        settings.setNightEndHour(-1)
        assertEquals(NightWindow(23, 0), settings.nightWindow.first())
    }

    @Test
    fun `equal night hours mean no night at all`() = runBlocking {
        settings.setNightStartHour(6)
        settings.setNightEndHour(6)

        assertTrue(settings.nightWindow.first().isEmpty)
    }

    // ---- bottle presets ----------------------------------------------------

    @Test
    fun `bottle presets default to common sizes`() = runBlocking {
        assertEquals(listOf(60, 90, 120, 150), settings.bottlePresets.first())
    }

    @Test
    fun `adding and removing a preset keeps the order`() = runBlocking {
        settings.addBottlePreset(135)
        assertEquals(listOf(60, 90, 120, 135, 150), settings.bottlePresets.first())

        settings.removeBottlePreset(90)
        assertEquals(listOf(60, 120, 135, 150), settings.bottlePresets.first())
    }

    @Test
    fun `a duplicate or non-positive preset is ignored`() = runBlocking {
        settings.addBottlePreset(120) // already there
        settings.addBottlePreset(0)
        settings.addBottlePreset(-30)

        assertEquals(listOf(60, 90, 120, 150), settings.bottlePresets.first())
    }

    @Test
    fun `removing every preset leaves an empty list rather than restoring the defaults`() = runBlocking {
        listOf(60, 90, 120, 150).forEach { settings.removeBottlePreset(it) }

        assertEquals(emptyList<Int>(), settings.bottlePresets.first())
    }
}
