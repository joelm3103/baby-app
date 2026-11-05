package app.babylog.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wastage report, on the JVM.
 *
 * One implementation feeds two screens (Summary's Bottle totals and Trends' Left behind card), so these
 * assertions are the only place the two can be proved to agree. The cases that matter are the ones a "simple"
 * implementation gets wrong: a leftover that was never recorded is not a zero, a child with no bottles still
 * gets a line, and a share that is real but rounds away must never read 0%.
 */
class WastageTest {

    private val t0 = 1_760_000_000_000L
    private val firstTwin = Child(
        id = "first-twin", name = "First Twin", birthDate = LocalDate.of(2025, 12, 31), sex = Sex.BOY, sortOrder = 0,
    )
    private val secondTwin = Child(
        id = "second-twin", name = "Second Twin", birthDate = LocalDate.of(2025, 12, 31), sex = Sex.BOY, sortOrder = 1,
    )

    @Test
    fun `poured is drunk plus left, and the share is of the poured volume`() {
        val report = wastageReport(
            listOf(
                bottle("a", consumedMl = 150, leftMl = 30),
                bottle("b", consumedMl = 120),
            ),
            listOf(firstTwin),
        )

        assertEquals(270, report.consumedMl)
        assertEquals(30, report.wastedMl)
        assertEquals(300, report.pouredMl)
        assertEquals(0.1, report.wastedShare!!, 1e-9)
        assertEquals("10%", wastedPercentText(report.wastedShare))
        assertEquals(2, report.feeds)
        // Only the feed that RECORDED a leftover counts — that is the number behind the percentage.
        assertEquals(1, report.feedsWithLeftover)
    }

    @Test
    fun `a leftover nobody recorded is not a zero measurement`() {
        val report = wastageReport(listOf(bottle("a", consumedMl = 150)), listOf(firstTwin))

        assertEquals(0, report.wastedMl)
        assertEquals(0, report.feedsWithLeftover)
        // Nothing was wasted AND something was poured, so 0% is the honest answer here rather than a dash.
        assertEquals("0%", wastedPercentText(report.wastedShare))
    }

    @Test
    fun `a child with no bottle in the range keeps a line of zeroes`() {
        val report = wastageReport(
            listOf(bottle("a", consumedMl = 150, leftMl = 30)),
            listOf(firstTwin, secondTwin),
        )

        // Both mode must not silently drop the twin who happens to have had no bottle; a missing row reads
        // as "no data" rather than "none", and the two are different.
        assertEquals(2, report.lines.size)
        val second = report.lines.single { it.childId == "second-twin" }
        assertEquals(0, second.feeds)
        assertEquals(0, second.pouredMl)
        assertNull(second.wastedShare)
    }

    @Test
    fun `a bottle made and refused is all waste`() {
        val report = wastageReport(listOf(bottle("a", consumedMl = 0, leftMl = 180)), listOf(firstTwin))

        assertEquals(0, report.consumedMl)
        assertEquals(180, report.pouredMl)
        assertEquals(1.0, report.wastedShare!!, 1e-9)
        assertEquals("100%", wastedPercentText(report.wastedShare))
    }

    @Test
    fun `an empty range says nothing rather than zero percent`() {
        val report = wastageReport(emptyList(), listOf(firstTwin))

        assertFalse(report.hasData)
        assertEquals(0, report.pouredMl)
        // "0% wasted" would be a claim about bottles that do not exist.
        assertNull(report.wastedShare)
        assertEquals("\u2014", wastedPercentText(report.wastedShare))
    }

    @Test
    fun `a share that is real but rounds away reads less than one percent`() {
        val report = wastageReport(listOf(bottle("a", consumedMl = 299, leftMl = 1)), listOf(firstTwin))

        // The same rule Trends follows for an average that genuinely exists: never print 0 over a number
        // that is not zero.
        assertEquals("<1%", wastedPercentText(report.wastedShare))
    }

    @Test
    fun `an average is divided by every bucket in the window, including the empty ones`() {
        val report = wastageReport(
            listOf(bottle("a", consumedMl = 150, leftMl = 60)),
            listOf(firstTwin),
            days = 3,
        )

        assertEquals(20.0, report.averageWastedPerBucket, 1e-9)
    }

    @Test
    fun `only bottles count, and tombstones do not`() {
        val report = wastageReport(
            listOf(
                bottle("a", consumedMl = 150, leftMl = 30),
                bottle("b", consumedMl = 150, leftMl = 90, deleted = true),
                BabyEvent(
                    id = "nappy", childId = "first-twin", type = EventType.DIAPER, startedAt = t0,
                    amountMl = 500, leftBehindMl = 400, createdBy = "t", updatedAt = t0,
                ),
            ),
            listOf(firstTwin),
        )

        assertEquals(1, report.feeds)
        assertEquals(30, report.wastedMl)
        assertEquals(180, report.pouredMl)
        assertTrue(report.hasData)
    }

    private fun bottle(
        id: String,
        consumedMl: Int?,
        leftMl: Int? = null,
        deleted: Boolean = false,
    ) = BabyEvent(
        id = "bottle-$id",
        childId = "first-twin",
        type = EventType.BOTTLE,
        startedAt = t0,
        amountMl = consumedMl,
        leftBehindMl = leftMl,
        createdBy = "t",
        updatedAt = t0,
        deleted = deleted,
    )
}
