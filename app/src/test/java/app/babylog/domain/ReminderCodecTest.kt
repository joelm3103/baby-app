package app.babylog.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reminder codec.
 *
 * Two properties matter beyond the round trip: a config must survive being written and read back unchanged
 * (otherwise a rule silently resets itself between sessions), and a string from a NEWER build must degrade to
 * defaults field by field rather than throwing — the same asymmetry `Mappers.enumOrNull` documents.
 */
class ReminderCodecTest {

    @Test
    fun `a full config round trips`() {
        val config = ReminderConfig(
            enabled = true,
            mode = ReminderMode.FIXED_TIMES,
            intervalMinutes = 90,
            timesOfDay = listOf(13 * 60, 7 * 60),
            skipNight = true,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )

        val back = decodeReminder(encodeReminder(config))

        // Times come back sorted, which is how they are stored and displayed.
        assertEquals(listOf(7 * 60, 13 * 60), back.timesOfDay)
        assertEquals(config.copy(timesOfDay = listOf(7 * 60, 13 * 60)), back)
    }

    @Test
    fun `the default config round trips`() {
        val defaults = ReminderConfig()
        assertEquals(defaults, decodeReminder(encodeReminder(defaults)))
    }

    @Test
    fun `nothing stored means the default rule, which is off`() {
        assertEquals(ReminderConfig(), decodeReminder(null))
        assertEquals(ReminderConfig(), decodeReminder(""))
        assertEquals(ReminderConfig(), decodeReminder("   "))
    }

    @Test
    fun `an unknown mode falls back to the default rather than throwing`() {
        val decoded = decodeReminder("1|WHENEVER|180||0|MON,TUE,WED,THU,FRI,SAT,SUN")
        assertEquals(ReminderMode.AFTER_LAST, decoded.mode)
        assertEquals(true, decoded.enabled)
    }

    @Test
    fun `an unparseable interval falls back to three hours`() {
        assertEquals(180, decodeReminder("0|AFTER_LAST|soon||0|MON").intervalMinutes)
        assertEquals(180, decodeReminder("0|AFTER_LAST|0||0|MON").intervalMinutes)
    }

    @Test
    fun `a rule with no anchor decodes with none, and one with an anchor keeps it`() {
        // The anchor is what lets a reminder for an activity nobody has logged ring once. Absent is legitimate: a
        // rule written by the previous build has no anchor and still decodes.
        assertNull(decodeReminder("1|AFTER_LAST|180||0|MONDAY").anchorAt)
        assertEquals(
            1_761_390_000_000L,
            decodeReminder("1|AFTER_LAST|180||0|MONDAY|1761390000000").anchorAt,
        )
    }

    @Test
    fun `a short string fills in what is missing`() {
        // What an older build's value would look like if the format ever grew a field.
        val decoded = decodeReminder("1|AFTER_LAST")
        assertEquals(true, decoded.enabled)
        assertEquals(ReminderConfig().intervalMinutes, decoded.intervalMinutes)
        assertEquals(ReminderConfig().days, decoded.days)
    }

    @Test
    fun `an empty day list is not a rule with no days`() {
        // "No days" is unrepresentable in the UI, so a corrupted or empty value must fall back to every day
        // rather than produce a reminder that can never fire.
        assertEquals(DayOfWeek.entries.toSet(), decodeReminder("1|AFTER_LAST|180||0|").days)
        assertEquals(DayOfWeek.entries.toSet(), decodeReminder("1|AFTER_LAST|180||0|FRYDAY").days)
    }

    @Test
    fun `out-of-range times are dropped`() {
        val decoded = decodeReminder("1|FIXED_TIMES|180|1440 -30 420||0|MON")
        assertEquals(listOf(420), decoded.timesOfDay)
    }

    @Test
    fun `the encoding is stable, so an unchanged rule is not rewritten every save`() {
        val config = ReminderConfig(enabled = true, timesOfDay = listOf(420, 420, 780), anchorAt = 1_761_390_000_000L)
        // Full day names rather than three-letter codes: the preference file is read by humans when something
        // goes wrong, and MON vs MONDAY is a mapping nobody should have to remember. The anchor is the last field,
        // APPENDED rather than inserted, so a value written before it existed still decodes field for field.
        assertEquals(
            "1|AFTER_LAST|180|420 780|0|MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY|1761390000000",
            encodeReminder(config),
        )
    }
}
