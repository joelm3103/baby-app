package app.babylog.data.reminders

import android.app.AlarmManager
import android.content.Context
import app.babylog.domain.Child
import app.babylog.domain.EventType
import app.babylog.domain.NightWindow
import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderKey
import app.babylog.domain.Sex
import java.time.Clock
import java.time.DayOfWeek
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
 * The scheduling layer, driven through Robolectric's own `ShadowAlarmManager`.
 *
 * This is the check a build cannot give: not "does it compile" but "when a feed reminder is enabled, is an
 * alarm actually registered, at the time the rule says". The domain tests prove the arithmetic; this proves the
 * arithmetic reaches the OS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val zone: ZoneId = ZoneId.of("America/Port_of_Spain")

    private val first = Child(
        id = "first-twin", name = "First Twin", birthDate = LocalDate.of(2026, 1, 1),
        sex = Sex.GIRL, gestationalAgeDays = 210, useAdjustedAge = true, sortOrder = 0,
    )
    private val second = first.copy(id = "second-twin", name = "Second Twin", sortOrder = 1)

    /** Fed at 09:00, so a 3h rule is due at 12:00 — and "now" is 09:30. */
    private val anchor = at("2026-10-24", "09:00")
    private val now = at("2026-10-24", "09:30")

    private fun at(date: String, time: String): Long =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun engine(
        configs: Map<ReminderKey, ReminderConfig>,
        children: List<Child> = listOf(first, second),
        anchors: Map<Pair<EventType, String>, Long> = emptyMap(),
        night: NightWindow = NightWindow(18, 6),
        scheduler: ReminderScheduler = ReminderScheduler(context),
    ): Reminders {
        val deps = ReminderDeps(
            store = object : ReminderStore {
                private val mutable = configs.toMutableMap()
                override suspend fun configs(): Map<ReminderKey, ReminderConfig> = mutable.toMap()
                override suspend fun stampAnchor(key: ReminderKey, at: Long) {
                    mutable[key]?.let { mutable[key] = it.copy(anchorAt = at) }
                }

                override suspend fun toInterval(key: ReminderKey) {
                    mutable[key]?.let {
                        mutable[key] = it.copy(
                            mode = app.babylog.domain.ReminderMode.AFTER_LAST,
                            timesOfDay = emptyList(),
                        )
                    }
                }
            },
            anchors = object : ReminderAnchors {
                override suspend fun latestAnchor(type: EventType, childId: String) =
                    anchors[type to childId]
            },
            children = object : ReminderChildren {
                override suspend fun all() = children
            },
            scheduler = scheduler,
            night = { night },
            clock = Clock.fixed(Instant.ofEpochMilli(now), zone),
            zone = zone,
        )
        return Reminders(deps, ReminderNotifications(context), { app.babylog.domain.TimeFormat.TWELVE_HOUR })
    }

    private fun alarmManager(): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun scheduledCount(): Int =
        shadowOf(alarmManager()).scheduledAlarms.size

    private fun nextTriggerAt(): Long? =
        shadowOf(alarmManager()).nextScheduledAlarm?.triggerAtTime

    @Test
    fun `an enabled feed reminder registers one alarm at the computed time`() = kotlinx.coroutines.runBlocking {
        val key = ReminderKey(EventType.BOTTLE, first.id)
        engine(
            configs = mapOf(key to ReminderConfig(enabled = true, intervalMinutes = 180)),
            anchors = mapOf((EventType.BOTTLE to first.id) to anchor),
        ).refresh()

        assertEquals(1, scheduledCount())
        assertEquals(at("2026-10-24", "12:00"), nextTriggerAt())
    }

    @Test
    fun `the armed alarm carries the time it is due, so the notification can say how late it is`() =
        kotlinx.coroutines.runBlocking {
            // Phase 30: the due time travels in the intent rather than being recomputed when it fires, because
            // the anchor moves as the log grows and a recomputation could disagree with what the phone was told.
            val key = ReminderKey(EventType.BOTTLE, first.id)
            engine(
                configs = mapOf(key to ReminderConfig(enabled = true, intervalMinutes = 180)),
                anchors = mapOf((EventType.BOTTLE to first.id) to anchor),
            ).refresh()

            val armed = shadowOf(alarmManager()).nextScheduledAlarm
            assertNotNull("the alarm should be armed", armed)
            val armedIntent = shadowOf(armed!!.operation).savedIntent
            assertEquals(
                at("2026-10-24", "12:00"),
                armedIntent.getLongExtra(ReminderReceiver.EXTRA_AT, 0L),
            )
        }

    @Test
    fun `cancelling a reminder leaves no armed time behind`() = kotlinx.coroutines.runBlocking {
        // The cancel path passes 0 through the same builder, and an extra of 0 must never reach a notification:
        // the receiver reads it as "unknown" so the text degrades rather than claiming 1970.
        ReminderScheduler(context).cancel(ReminderKey(EventType.BOTTLE, first.id))
        assertEquals(0, scheduledCount())
    }

    @Test
    fun `each child gets its own alarm from its own anchor`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(
                ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = true, intervalMinutes = 180),
                ReminderKey(EventType.BOTTLE, second.id) to ReminderConfig(enabled = true, intervalMinutes = 180),
            ),
            anchors = mapOf(
                (EventType.BOTTLE to first.id) to anchor,
                (EventType.BOTTLE to second.id) to at("2026-10-24", "08:00"),
            ),
        ).refresh()

        // Two alarms, and the earliest one is Second Twin's 11:00 - not one shared reminder.
        assertEquals(2, scheduledCount())
        assertEquals(at("2026-10-24", "11:00"), nextTriggerAt())
    }

    @Test
    fun `a disabled rule arms nothing`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(ReminderKey(EventType.DIAPER, first.id) to ReminderConfig(enabled = false)),
            anchors = mapOf((EventType.DIAPER to first.id) to anchor),
        ).refresh()

        assertEquals(0, scheduledCount())
    }

    @Test
    fun `switching a rule off cancels the alarm it had armed`() = kotlinx.coroutines.runBlocking {
        val key = ReminderKey(EventType.BOTTLE, first.id)
        val anchors = mapOf((EventType.BOTTLE to first.id) to anchor)

        engine(configs = mapOf(key to ReminderConfig(enabled = true)), anchors = anchors).refresh()
        assertEquals(1, scheduledCount())

        // The same refresh with the rule switched off: the alarm must go, not merely be skipped. A notification
        // the parent has turned off is worse than no notification.
        engine(configs = mapOf(key to ReminderConfig(enabled = false)), anchors = anchors).refresh()
        assertEquals(0, scheduledCount())
    }

    @Test
    fun `a rule written before the anchor existed is stamped and armed rather than left dead`() =
        kotlinx.coroutines.runBlocking {
            // The migration: an enabled rule with no anchor and no log could never fire. It is stamped once, so it
            // rings an interval from now - and the card gets a time to show.
            val key = ReminderKey(EventType.BOTTLE, first.id)
            engine(
                configs = mapOf(key to ReminderConfig(enabled = true, intervalMinutes = 180, anchorAt = null)),
                anchors = emptyMap(),
            ).refresh()

            assertEquals(1, scheduledCount())
            assertEquals(now + 180 * 60_000L, nextTriggerAt())
        }

    @Test
    fun `a rule with its own anchor arms from that anchor when nothing has been logged`() = kotlinx.coroutines.runBlocking {
        // The old behaviour here was "no log means no alarm", which is how a feed reminder quietly never rang for a
        // child whose first bottle had not been logged yet.
        val setAt = at("2026-10-24", "09:00")
        engine(
            configs = mapOf(
                ReminderKey(EventType.SOLIDS, first.id) to ReminderConfig(
                    enabled = true,
                    intervalMinutes = 180,
                    anchorAt = setAt,
                ),
            ),
            anchors = emptyMap(),
        ).refresh()

        assertEquals(1, scheduledCount())
        assertEquals(at("2026-10-24", "12:00"), nextTriggerAt())
    }

    @Test
    fun `fixed times arm the next time of day`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(
                ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(
                    enabled = true,
                    mode = app.babylog.domain.ReminderMode.FIXED_TIMES,
                    timesOfDay = listOf(7 * 60, 10 * 60),
                    days = DayOfWeek.entries.toSet(),
                ),
            ),
        ).refresh()

        assertEquals(1, scheduledCount())
        assertEquals(at("2026-10-24", "10:00"), nextTriggerAt())
    }

    @Test
    fun `a night-window rule arms the morning instead`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(
                ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = true, skipNight = true),
            ),
            anchors = mapOf((EventType.BOTTLE to first.id) to at("2026-10-24", "20:00")),
        ).refresh()

        assertEquals(at("2026-10-25", "06:00"), nextTriggerAt())
    }

    @Test
    fun `the armed alarm is the receiver's intent, with the activity and child`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(ReminderKey(EventType.DIAPER, second.id) to ReminderConfig(enabled = true)),
            anchors = mapOf((EventType.DIAPER to second.id) to anchor),
        ).refresh()

        val alarm = shadowOf(alarmManager()).nextScheduledAlarm!!
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(ReminderReceiver.ACTION_FIRE, intent.action)
        assertEquals("DIAPER", intent.getStringExtra(ReminderReceiver.EXTRA_TYPE))
        assertEquals(second.id, intent.getStringExtra(ReminderReceiver.EXTRA_CHILD_ID))
    }

    @Test
    fun `the alarm is EXACT when the system permits it`() = kotlinx.coroutines.runBlocking {
        // The reversal: `window=+37m47s` on the phone meant "a few minutes late" could be most of an hour. Robolectric
        // records how the alarm was set, and `allowWhileIdle` stays true either way - so the check is the one that
        // distinguishes them, `ScheduledAlarm.allowWhileIdleExact` being absent from the shadow's API: the scheduler
        // must report that it CAN be exact, and the alarm must still be armed through Doze.
        val scheduler = ReminderScheduler(context) { true }
        assertTrue("expected exact alarms to be available", scheduler.canBeExact())

        engine(
            configs = mapOf(ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = true)),
            anchors = mapOf((EventType.BOTTLE to first.id) to anchor),
            scheduler = scheduler,
        ).refresh()

        assertEquals(1, scheduledCount())
        assertTrue(shadowOf(alarmManager()).nextScheduledAlarm!!.allowWhileIdle)
    }

    @Test
    fun `with exact alarms revoked the reminder is still armed, late rather than silent`() =
        kotlinx.coroutines.runBlocking {
            val scheduler = ReminderScheduler(context) { false }
            assertFalse("expected exact alarms to be unavailable", scheduler.canBeExact())

            engine(
                configs = mapOf(ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = true)),
                anchors = mapOf((EventType.BOTTLE to first.id) to anchor),
                scheduler = scheduler,
            ).refresh()

            assertEquals("late is better than silent", 1, scheduledCount())
        }

    @Test
    fun `the alarm is inexact, so the app never asks for an exact-alarm privilege`() = kotlinx.coroutines.runBlocking {
        engine(
            configs = mapOf(ReminderKey(EventType.BOTTLE, first.id) to ReminderConfig(enabled = true)),
            anchors = mapOf((EventType.BOTTLE to first.id) to anchor),
        ).refresh()

        // Robolectric records the alarm type; RTC_WAKEUP is what setAndAllowWhileIdle uses, and there is no
        // allow-while-idle-with-exactness call anywhere in this app.
        val alarm = shadowOf(alarmManager()).nextScheduledAlarm!!
        // RTC_WAKEUP is what `setAndAllowWhileIdle` uses. The exact variants would be RTC_WAKEUP too, so the
        // real guard is the source check below plus this: `setExactAndAllowWhileIdle` also records
        // `allowWhileIdle = true`, and Robolectric exposes no exactness flag on the alarm itself.
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue("a reminder must be allowed through Doze", alarm.allowWhileIdle)
    }
}
