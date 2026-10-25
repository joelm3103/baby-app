package app.babylog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.babylog.data.repo.EventDraft
import app.babylog.di.AppContainer
import app.babylog.domain.DiaperKind
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import app.babylog.domain.EventType
import app.babylog.domain.LengthUnit
import app.babylog.domain.UnitSystem
import app.babylog.domain.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Everything that runs before the first frame, exercised for real: the DI container, Room opening and
 * migrating, the DataStore preferences file being created, the first-run seed, and live flows emitting.
 *
 * This exists because a Compose UI can compile, package, install and then crash on launch for reasons
 * no build ever reports — a missing resource, a DataStore opened twice, Room failing to open. A launch
 * crash is the single most likely runtime failure of a green build, so it gets a test.
 *
 * NOTE: one AppContainer per test class only. DataStore refuses a second instance for the same file in
 * the same process, so do not add a second test here that builds another container.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppStartupTest {

    @Test
    fun `the container, database, preferences and seed all work before the first frame`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        // Room opens and the schema is usable
        container.children.seedIfEmpty()
        val kids = container.children.observeAll().first()
        assertEquals(2, kids.size)
        assertEquals(listOf("First Twin", "Second Twin"), kids.map { it.name })
        // the premature range, stored in days so the adjusted-age maths stays exact
        assertTrue(kids.all { it.gestationalAgeDays == 210 })
        assertTrue(kids.all { it.prematureDays == 70 })
        assertTrue(kids.all { it.isPreterm })

        // the DataStore preferences file is created and defaults are what a fresh install should be
        val units = container.settings.units.first()
        assertEquals(LengthUnit.CENTIMETRES, units.length)
        assertEquals(UnitSystem.METRIC, units.system)

        // a preference write round-trips, which is the path that had a missing import earlier
        container.settings.setVolumeUnit(VolumeUnit.FLUID_OUNCES)
        assertEquals(VolumeUnit.FLUID_OUNCES, container.settings.units.first().volume)
        container.settings.setUnitSystem(UnitSystem.METRIC)
        assertEquals(VolumeUnit.MILLILITRES, container.settings.units.first().volume)

        // logging for both children works end to end
        val logged = container.events.log(
            listOf("first-twin", "second-twin"),
            EventDraft(type = EventType.DIAPER, startedAt = System.currentTimeMillis(), diaperKind = DiaperKind.WET),
            createdBy = "test",
        )
        assertEquals(2, logged.size)
        assertEquals(2, container.events.observeForChildren(listOf("first-twin", "second-twin")).first().size)

        // Reminders, end to end through the real wiring: a rule saved to DataStore, a real anchor read from
        // Room, and a real alarm handed to AlarmManager. The Robolectric scheduler tests use fakes for the store
        // and the anchors; this is the one place the container's own adapters are exercised.
        container.settings.setReminder(
            ReminderKey(EventType.BOTTLE, "first-twin"),
            ReminderConfig(enabled = true, intervalMinutes = 180),
        )
        container.events.log(
            listOf("first-twin"),
            EventDraft(type = EventType.BOTTLE, startedAt = System.currentTimeMillis(), amountMl = 120),
            createdBy = "test",
        )
        container.reminders.refresh()
        val alarms = shadowOf(
            context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager,
        ).scheduledAlarms
        assertEquals("one alarm for the one enabled rule", 1, alarms.size)

        // The bundled food catalogue seeds through the REAL asset path in the container, not a fixture.
        // A bad asset path or unparseable JSON would otherwise only surface on a phone.
        container.foods.seedIfNeeded()
        val catalogue = container.foods.observeCatalogue().first()
        assertTrue("expected the seeded catalogue, got ${catalogue.size}", catalogue.size >= 60)
        assertTrue(catalogue.any { it.id == "banana" })
    }
}
