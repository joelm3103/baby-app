package app.babylog.ui.home

import app.babylog.domain.TimeInputMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wheel's arithmetic and the dialog's mode cycle.
 *
 * A twelve-hour wheel shows `12` twice — once at midnight and once at noon — so moving between what the column
 * says and what the app stores is where the off-by-twelve lives. Every conversion in both directions is pinned
 * here, at the two hours that are wrong if the arithmetic is inlined: `00:xx` and `12:xx`.
 *
 * The cycle is pinned for the same reason: `nextTimeInputMode` is the only route between the three inputs, so
 * a wrong answer strands a mode with no way to reach it.
 */
class WheelTimePickerTest {

    @Test
    fun theModeCycleVisitsAllThreeAndReturns() {
        // Dial and wheels are what the owner asked to move between; the keyboard is kept reachable.
        assertEquals(TimeInputMode.WHEEL, nextTimeInputMode(TimeInputMode.DIAL))
        assertEquals(TimeInputMode.TEXT, nextTimeInputMode(TimeInputMode.WHEEL))
        assertEquals(TimeInputMode.DIAL, nextTimeInputMode(TimeInputMode.TEXT))
    }

    @Test
    fun everyModeNamesItselfAndLeadsSomewhereElse() {
        // A blank or repeated name would make the switch button unreadable or a no-op.
        val names = TimeInputMode.entries.map { it.spoken }
        assertEquals(TimeInputMode.entries.size, names.toSet().size)
        assertEquals(0, names.count { it.isBlank() })
        TimeInputMode.entries.forEach { mode ->
            assertEquals(true, nextTimeInputMode(mode) != mode)
        }
    }

    @Test
    fun everyModeHasADistinctChipLabel() {
        // Phase 29 puts these three on Settings → Units → `Time input`. Two modes sharing a chip is a row
        // where tapping one of them selects the other, which is indistinguishable from a dead chip.
        val labels = TimeInputMode.entries.map { it.label }
        assertEquals(TimeInputMode.entries.size, labels.toSet().size)
        assertEquals(0, labels.count { it.isBlank() })
        assertEquals(listOf("Clock", "Wheels", "Keypad"), labels)
    }

    @Test
    fun theHourColumnFollowsTheClockFormat() {
        assertEquals((0..23).map { "%02d".format(it) }, wheelHourLabels(is24Hour = true))
        assertEquals((1..12).map { "%02d".format(it) }, wheelHourLabels(is24Hour = false))
    }

    @Test
    fun aTwentyFourHourHourIsItsOwnColumnPosition() {
        (0..23).forEach { hour ->
            assertEquals(hour, wheelHourIndex(hour, is24Hour = true))
            assertEquals(hour, unWheelHour(hour, is24Hour = true, isPm = false))
        }
    }

    @Test
    fun midnightAndNoonBothReadTwelveOnATwelveHourWheel() {
        // The two that break naive arithmetic: `hour % 12` maps both 00 and 12 to position 0, which is right,
        // and the label at that position is `12`, which is the part that is easy to get wrong.
        assertEquals(11, wheelHourIndex(0, is24Hour = false))
        assertEquals(11, wheelHourIndex(12, is24Hour = false))
        assertEquals("12", wheelHourLabels(is24Hour = false)[11])
    }

    @Test
    fun everyHourSurvivesARoundTripThroughTheTwelveHourWheel() {
        (0..23).forEach { hour ->
            val index = wheelHourIndex(hour, is24Hour = false)
            val pm = isPmHour(hour)
            assertEquals("hour $hour came back wrong", hour, unWheelHour(index, is24Hour = false, isPm = pm))
        }
    }

    @Test
    fun twelveAmIsMidnightAndTwelvePmIsNoon() {
        // The two conversions that are wrong if the AM/PM column is treated as a plain +12.
        assertEquals(0, unWheelHour(11, is24Hour = false, isPm = false))
        assertEquals(12, unWheelHour(11, is24Hour = false, isPm = true))
        // ...and the neighbours either side of them.
        assertEquals(11, unWheelHour(10, is24Hour = false, isPm = false))
        assertEquals(23, unWheelHour(10, is24Hour = false, isPm = true))
    }

    @Test
    fun theHalfOfTheDayIsReadFromTheStoredHourNotTheColumn() {
        assertEquals(false, isPmHour(0))
        assertEquals(false, isPmHour(11))
        assertEquals(true, isPmHour(12))
        assertEquals(true, isPmHour(23))
    }

    @Test
    fun flippingToAmOrPmKeepsTheHourOnTheClockFace() {
        // What the dialog does when the AM/PM column moves: `if (pm) (hour % 12) + 12 else hour % 12`.
        // This is why the column reports a half-day rather than an hour — no index round-trip, so the two
        // columns cannot each compute a value from the other's stale half.
        fun toPm(hour: Int) = (hour % 12) + 12
        fun toAm(hour: Int) = hour % 12

        // Midday and midnight are the two that must not move the hand.
        assertEquals(12, toPm(0))
        assertEquals(0, toAm(12))
        // ...and the rest keep the number the face shows.
        assertEquals(23, toPm(11))
        assertEquals(11, toAm(23))
        assertEquals(13, toPm(1))
        assertEquals(1, toAm(13))
        // The invariants that make the column safe to move, for every hour of the day.
        (0..23).forEach { h ->
            // Flipping the half must NOT move the hand: the column is a separate control, and if it moved
            // the number too, tapping AM/PM would change the time the parent set on the wheel.
            // (JUnit4 takes the message FIRST, and there is no three-argument int overload with it last.)
            assertEquals(
                "PM moved the hand at $h",
                wheelHourIndex(h, is24Hour = false),
                wheelHourIndex(toPm(h), is24Hour = false),
            )
            assertEquals(
                "AM moved the hand at $h",
                wheelHourIndex(h, is24Hour = false),
                wheelHourIndex(toAm(h), is24Hour = false),
            )
            // It changes the half of the day, which is the whole point of it.
            assertEquals(true, isPmHour(toPm(h)))
            assertEquals(false, isPmHour(toAm(h)))
            // Switching to the other half is whatever the face already showed, and doing it twice is once.
            assertEquals(h % 12, toAm(toPm(h)))
            assertEquals(toPm(h), toPm(toPm(h)))
            assertEquals(toAm(h), toAm(toAm(h)))
        }
    }

    @Test
    fun theMinuteColumnIsEveryMinute() {
        // One-minute granularity, because a feed's time matters to the minute and the app stores exact ones.
        assertEquals(60, WHEEL_MINUTE_LABELS.size)
        assertEquals("00", WHEEL_MINUTE_LABELS.first())
        assertEquals("59", WHEEL_MINUTE_LABELS.last())
    }
}
