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

    @Test
    fun `display preferences persist independently of unit presets`() = runBlocking {
        assertEquals(app.babylog.domain.TimeFormat.TWENTY_FOUR_HOUR, settings.timeFormat.first())
        assertEquals(app.babylog.domain.TemperatureUnit.CELSIUS, settings.units.first().temperature)
        settings.setTimeFormat(app.babylog.domain.TimeFormat.TWELVE_HOUR)
        settings.setTemperatureUnit(app.babylog.domain.TemperatureUnit.FAHRENHEIT)
        settings.setUnitSystem(app.babylog.domain.UnitSystem.IMPERIAL)
        assertEquals(app.babylog.domain.TimeFormat.TWELVE_HOUR, settings.timeFormat.first())
        assertEquals(app.babylog.domain.TemperatureUnit.FAHRENHEIT, settings.units.first().temperature)
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

    // ---- medication presets ------------------------------------------------

    @Test
    fun `medication presets start empty rather than suggesting a medicine`() = runBlocking {
        assertEquals(emptyList<String>(), settings.medicationPresets.first())
    }

    @Test
    fun `medication presets keep insertion order and do not duplicate`() = runBlocking {
        settings.addMedicationPreset("Calpol 2.5 ml")
        settings.addMedicationPreset("Vitamin D")
        // Same name, different case: still one preset, because the chips would otherwise be twins.
        settings.addMedicationPreset("  calpol 2.5 ML ")

        assertEquals(listOf("Calpol 2.5 ml", "Vitamin D"), settings.medicationPresets.first())

        settings.removeMedicationPreset("Calpol 2.5 ml")
        assertEquals(listOf("Vitamin D"), settings.medicationPresets.first())
    }

    @Test
    fun `a comma in a medication name survives the round trip`() = runBlocking {
        // The reason the encoding uses a newline separator: "Calpol, 2.5 ml" is a dose a parent types.
        settings.addMedicationPreset("Calpol, 2.5 ml")
        settings.addMedicationPreset("Vitamin D")

        assertEquals(listOf("Calpol, 2.5 ml", "Vitamin D"), settings.medicationPresets.first())
    }

    @Test
    fun `a blank medication preset is refused, and a newline cannot forge a second one`() = runBlocking {
        settings.addMedicationPreset("   ")
        settings.addMedicationPreset("Calpol\nVitamin D")

        assertEquals(listOf("Calpol Vitamin D"), settings.medicationPresets.first())
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
