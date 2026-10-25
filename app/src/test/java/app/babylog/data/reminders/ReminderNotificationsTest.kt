package app.babylog.data.reminders

import android.app.NotificationManager
import android.content.Context
import app.babylog.data.medications.MedicationCatalogueParser
import app.babylog.domain.Child
import app.babylog.domain.EventType
import app.babylog.domain.NightWindow
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import app.babylog.domain.ReminderMode
import app.babylog.domain.Sex
import app.babylog.domain.TimeFormat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What actually reaches the notification shade when a reminder fires.
 *
 * The claim under test is the one the plan made: a fired reminder posts ONE notification, named with the child
 * and the activity, at the computed time — and a Health reminder never names a medicine or a dose. That last one
 * is not cosmetic: `AGENTS.md` forbids the app proposing a dose, and a notification saying "Calpol" at 3am would
 * be exactly that, whatever the intent behind it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderNotificationsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val zone: ZoneId = ZoneId.of("America/Port_of_Spain")

    private val first = Child(
        id = "first-twin", name = "First Twin", birthDate = LocalDate.of(2026, 1, 1),
        sex = Sex.GIRL, gestationalAgeDays = 210, useAdjustedAge = true, sortOrder = 0,
    )
    private val second = first.copy(id = "second-twin", name = "Second Twin", sortOrder = 1)

    private val anchor = at("2026-10-24", "09:00")
    private val now = at("2026-10-24", "12:05")

    private fun at(date: String, time: String): Long =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun engine(
        configs: Map<ReminderKey, ReminderConfig>,
        anchors: Map<Pair<EventType, String>, Long> = emptyMap(),
    ): Reminders {
        val deps = ReminderDeps(
            store = object : ReminderStore {
                override suspend fun configs() = configs
                override suspend fun stampAnchor(key: ReminderKey, at: Long) = Unit
                override suspend fun toInterval(key: ReminderKey) = Unit
            },
            anchors = object : ReminderAnchors {
                override suspend fun latestAnchor(type: EventType, childId: String) =
                    anchors[type to childId]
            },
            children = object : ReminderChildren {
                override suspend fun all() = listOf(first, second)
            },
            scheduler = ReminderScheduler(context),
            night = { NightWindow(18, 6) },
            clock = Clock.fixed(Instant.ofEpochMilli(now), zone),
            zone = zone,
        )
        return Reminders(deps, ReminderNotifications(context), { TimeFormat.TWELVE_HOUR })
    }

    /** A number with a dose unit after it: `2.5 ml`, `1 tablet`, `3 drops`. */
    private val DOSE_PATTERN = Regex("""\d+(\.\d+)?\s*(ml|mg|mcg|g|drops?|tablets?|capsules?|sachets?)\b""", RegexOption.IGNORE_CASE)

    private fun manager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun posted() = shadowOf(manager()).allNotifications

    @Test
    fun `a due feed reminder posts one notification naming the child and the activity`() =
        kotlinx.coroutines.runBlocking {
            engine(
                configs = mapOf(
                    ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = true),
                ),
                anchors = mapOf((EventType.BOTTLE to first.id) to anchor),
            ).fire(EventType.BOTTLE, first.id, zone, now)

            val all = posted()
            assertEquals(1, all.size)
            val notification = all.single()
            assertEquals("First Twin \u00b7 Bottle feed", notification.extras.getCharSequence("android.title").toString())
            assertEquals("3h 5m since the last feed \u00b7 9:00 AM", notification.extras.getCharSequence("android.text").toString())
        }

    @Test
    fun `a disabled reminder posts nothing`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = false)),
        ).fire(EventType.BOTTLE, first.id, zone, now)

        assertTrue(posted().isEmpty())
    }

    @Test
    fun `two children due at once are two notifications, not one replacing the other`() =
        kotlinx.coroutines.runBlocking {
            val engine = engine(
                configs = mapOf(
                    ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = true),
                    ReminderKey(EventType.BOTTLE, second.id) to ReminderConfig(enabled = true),
                ),
                anchors = mapOf(
                    (EventType.BOTTLE to first.id) to anchor,
                    (EventType.BOTTLE to second.id) to anchor,
                ),
            )
            engine.fire(EventType.BOTTLE, first.id, zone, now)
            engine.fire(EventType.BOTTLE, second.id, zone, now)

            assertEquals(2, posted().size)
            assertTrue(
                "the two notifications must have different ids",
                ReminderNotifications.notificationId(EventType.BOTTLE, first.id) !=
                    ReminderNotifications.notificationId(EventType.BOTTLE, second.id),
            )
        }

    @Test
    fun `a fixed-time reminder says which time it is for`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(
                ReminderKey(EventType.DIAPER, second.id) to ReminderConfig(
                    enabled = true,
                    mode = ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf(12 * 60),
                ),
            ),
        ).fire(EventType.DIAPER, second.id, zone, now)

        val text = posted().single().extras.getCharSequence("android.text").toString()
        assertEquals("Reminder for 12:05 PM", text)
    }

    /**
     * The rule `AGENTS.md` sets: the app must never appear to suggest a dose. A reminder that named a medicine
     * would do exactly that, so the Health reminder says the activity and stops.
     */
    @Test
    fun `a health reminder never names a medicine or a dose`() = kotlinx.coroutines.runBlocking {
        val catalogue = context.assets.open(MedicationCatalogueParser.ASSET_PATH)
            .bufferedReader().use { it.readText() }
            .let { MedicationCatalogueParser.parse(it) }
        assertTrue("the catalogue must not be empty for this to test anything", catalogue.isNotEmpty())

        ReminderMode.entries.forEachIndexed { index, mode ->
            // A fresh child per mode, so the two notifications cannot collide on an id and hide one another.
            val child = first.copy(id = "health-${mode.name}", name = "First Twin")
            val config = ReminderConfig(
                enabled = true,
                mode = mode,
                timesOfDay = if (mode == ReminderMode.FIXED_TIMES) listOf(12 * 60) else emptyList(),
            )
            engine(
                configs = mapOf(ReminderKey(EventType.HEALTH, child.id) to config),
                anchors = mapOf((EventType.HEALTH to child.id) to anchor),
            ).fire(EventType.HEALTH, child.id, zone, now)

            // `.last()` rather than `.single()`: each mode posts its own notification, and this loop is
            // checking the words rather than the count (which the two-children test already covers).
            val notification = posted().last()
            val title = notification.extras.getCharSequence("android.title").toString()
            val text = notification.extras.getCharSequence("android.text").toString()

            catalogue.forEach { medicine ->
                assertFalse("the health reminder must not name $medicine", text.contains(medicine, ignoreCase = true))
                assertFalse("the health reminder must not name $medicine", title.contains(medicine, ignoreCase = true))
            }
            // A dose is a NUMBER WITH A UNIT. Elapsed time legitimately has digits, so the guard is the dose
            // shape rather than "any digit" - which would fail on "3h 5m since the last health entry".
            assertFalse(
                "the health reminder must not show a dose ($index, $mode): $text",
                DOSE_PATTERN.containsMatchIn(text),
            )
            assertTrue("it should still say what it is about", text.contains("health entry"))
        }
    }

    @Test
    fun `the notification carries the activity's accent and the app's own channel`() = kotlinx.coroutines.runBlocking {
            engine(
                configs = mapOf(ReminderKey(EventType.SOLIDS, first.id) to ReminderConfig(enabled = true)),
                anchors = mapOf((EventType.SOLIDS to first.id) to anchor),
            ).fire(EventType.SOLIDS, first.id, zone, now)

            val notification = posted().single()
            assertEquals(ReminderNotifications.CHANNEL_ID, notification.channelId)
            assertNotNull(notification.color)
        }
}
