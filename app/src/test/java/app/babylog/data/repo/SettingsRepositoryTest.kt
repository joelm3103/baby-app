package app.babylog.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import app.babylog.domain.ChildSelection
import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import app.babylog.domain.ThemeMode
import app.babylog.domain.TimeInputMode
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private lateinit var store: DataStore<Preferences>
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A unique file per test: DataStore refuses two live instances for one file.
        tempDir = File(context.cacheDir, "settings-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) {
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
        assertEquals(app.babylog.domain.TimeFormat.TWELVE_HOUR, settings.timeFormat.first())
        assertEquals(app.babylog.domain.TemperatureUnit.CELSIUS, settings.units.first().temperature)
        settings.setTimeFormat(app.babylog.domain.TimeFormat.TWELVE_HOUR)
        settings.setTemperatureUnit(app.babylog.domain.TemperatureUnit.FAHRENHEIT)
        settings.setUnitSystem(app.babylog.domain.UnitSystem.IMPERIAL)
        assertEquals(app.babylog.domain.TimeFormat.TWELVE_HOUR, settings.timeFormat.first())
        assertEquals(app.babylog.domain.TemperatureUnit.FAHRENHEIT, settings.units.first().temperature)
    }

    // ---- Phase 29: the time dialog's input mode ----------------------------

    @Test
    fun `the time dialog opens on the wheels, not the dial`() = runBlocking {
        // The owner's ask: the scrollable wheels are the default, and a stored `DIAL` here would put the
        // clock back in front of a parent on every logging sheet.
        assertEquals(TimeInputMode.WHEEL, settings.timeInputMode.first())
    }

    @Test
    fun `a chosen time input round-trips, so the dialog opens where it was left`() = runBlocking {
        settings.setTimeInputMode(TimeInputMode.TEXT)
        assertEquals(TimeInputMode.TEXT, settings.timeInputMode.first())

        settings.setTimeInputMode(TimeInputMode.DIAL)
        assertEquals(TimeInputMode.DIAL, settings.timeInputMode.first())
    }

    @Test
    fun `a reset puts the time dialog back on the wheels`() = runBlocking {
        settings.setTimeInputMode(TimeInputMode.TEXT)

        settings.clearForReset()

        // A reset returns the app to first-run state, and first run opens on the wheels.
        assertEquals(TimeInputMode.WHEEL, settings.timeInputMode.first())
    }

    // ---- hidden activities -------------------------------------------------

    @Test
    fun `every activity shows until one is hidden`() = runBlocking {
        assertEquals(emptySet<EventType>(), settings.hiddenActivities.first())
    }

    @Test
    fun `hiding and showing round-trip as a set, not a list of toggles`() = runBlocking {
        settings.setActivityHidden(EventType.SOLIDS, hidden = true)
        settings.setActivityHidden(EventType.GROWTH, hidden = true)
        assertEquals(setOf(EventType.SOLIDS, EventType.GROWTH), settings.hiddenActivities.first())

        settings.setActivityHidden(EventType.SOLIDS, hidden = false)
        assertEquals(setOf(EventType.GROWTH), settings.hiddenActivities.first())
    }

    @Test
    fun `hiding the same activity twice is not a duplicate`() = runBlocking {
        settings.setActivityHidden(EventType.DIAPER, hidden = true)
        settings.setActivityHidden(EventType.DIAPER, hidden = true)
        assertEquals(setOf(EventType.DIAPER), settings.hiddenActivities.first())
    }

    @Test
    fun `an unknown activity name is dropped rather than thrown on`() = runBlocking {
        // A preference written by a newer build that knows a seventh activity must not break this one.
        store.edit { it[stringPreferencesKey("activities.hidden")] = "BOTTLE,NASAL_ASPIRATE" }
        assertEquals(setOf(EventType.BOTTLE), settings.hiddenActivities.first())
    }

    @Test
    fun `a reset shows every activity again`() = runBlocking {
        settings.setActivityHidden(EventType.SLEEP, hidden = true)
        assertEquals(setOf(EventType.SLEEP), settings.hiddenActivities.first())

        settings.clearForReset()

        assertEquals(emptySet<EventType>(), settings.hiddenActivities.first())
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


    // ---- Phase 15: clearForReset -------------------------------------------

    @Test
    fun `a reset drops this phone's choices and every reminder rule`() = runBlocking {
        settings.setTheme(ThemeMode.DARK)
        settings.setSelectedChild(ChildSelection.One("first-twin"))
        settings.setNightStartHour(20)
        settings.addBottlePreset(135)
        settings.addMedicationPreset("Calpol")
        settings.setReminder(
            ReminderKey(EventType.BOTTLE, "first-twin"),
            ReminderConfig(enabled = true, intervalMinutes = 180),
        )

        settings.clearForReset()

        assertEquals(ThemeMode.SYSTEM, settings.theme.first())
        assertEquals(ChildSelection.default, settings.selectedChild.first())
        assertEquals(NightWindow(), settings.nightWindow.first())
        assertEquals(listOf(60, 90, 120, 150), settings.bottlePresets.first())
        assertEquals(emptyList<String>(), settings.medicationPresets.first())
        // The whole point: a rule left behind would ring about a log the reset deleted.
        assertTrue(settings.reminders.first().isEmpty())
    }

    @Test
    fun `a reset keeps the first-run children flag, so the twins are not seeded back`() = runBlocking {
        settings.seedChildrenOnce { }   // sets children.seeded = true

        settings.clearForReset()

        assertTrue(settings.childSeedDone.first())
    }

    @Test
    fun `a reset keeps the sync seed and anything outside this app's own keys`() = runBlocking {
        settings.markSyncSeedDone()
        // The growth dataset keys live in the same store but were never this repository's to drop: a
        // reset must not silently downgrade a reference that was checksum-verified and accepted.
        store.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("growth.dataset.version")] = "who-2006.2026.11" }

        settings.clearForReset()

        assertTrue(settings.syncSeedDone.first())
        assertEquals("who-2006.2026.11", store.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("growth.dataset.version")])
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
