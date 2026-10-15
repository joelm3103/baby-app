package app.babylog.data.growth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.babylog.domain.Child
import app.babylog.domain.Sex
import app.babylog.domain.ageDaysForCharts
import app.babylog.domain.percentile
import app.babylog.domain.valueAtZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The bundled dataset, read the way the app reads it: from the real asset, through the real parser.
 *
 * This is the test that would fail if the generator were ever run against a drifted upstream file, or
 * if the asset were replaced by something that parses but is not WHO's data. Robolectric is needed
 * only because `org.json` is an Android API — the same reason `FoodRepositoryTest` needs it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrowthReferenceTest {

    private fun reference(): GrowthReference {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = context.assets.open(GrowthReferenceParser.ASSET_PATH).bufferedReader().use { it.readText() }
        return GrowthReferenceParser.parse(json)
    }

    @Test
    fun `the asset carries WHO's identity, not just numbers`() {
        val reference = reference()
        assertEquals("WHO Child Growth Standards", reference.dataset)
        assertEquals("who-2006.2026.10", reference.version)
        assertEquals("2006-04-27", reference.publishedAt)
        assertTrue(
            "retrievedAt should be a plain ISO date, was ${reference.retrievedAt}",
            reference.retrievedAt.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
        )
        // A percentile is never shown without this line, so its shape is pinned here.
        assertTrue(
            "identity line reads '${reference.identityLine}'",
            reference.identityLine.matches(Regex("WHO 2006 · v2026\\.10 · updated \\d{1,2} [A-Z][a-z]{2} \\d{4}")),
        )
        assertEquals("WHO 2006 · v2026.10 · updated 15 Oct 2026", buildIdentityLine("who-2006.2026.10", "2026-10-15"))
        assertEquals("2026.10", buildIdentityLine("2026.10", "not-a-date"))
    }

    @Test
    fun `every measure and both sexes are present with the published row counts`() {
        val reference = reference()
        assertEquals(5, reference.sourceFiles.size)
        for (measure in GrowthMeasure.entries) {
            for (sex in listOf(Sex.BOY, Sex.GIRL)) {
                val rows = reference.table(measure, sex)
                val expected = if (reference.axisOf(measure) == GrowthAxis.AGE_DAYS) 1857 else 651
                assertEquals("$measure/$sex", expected, rows.size)
            }
        }
    }

    @Test
    fun `the rows are WHO's published parameters`() {
        val reference = reference()
        val day0 = reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 0.0)!!
        assertEquals(0.3487, day0.l, 1e-9)
        assertEquals(3.3464, day0.m, 1e-9)
        assertEquals(0.14602, day0.s, 1e-9)

        val day12 = reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 12.0)!!
        assertEquals(3.671, day12.m, 1e-9)

        // The age axis is DAYS and the rows are daily, so day == index. Reading it as months would
        // silently produce a chart of a different child.
        assertEquals(12.0, reference.table(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY)[12].axis, 1e-9)
        assertEquals(1856.0, reference.table(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY).last().axis, 1e-9)
    }

    @Test
    fun `the bundled parameters reproduce WHO's published SD columns`() {
        val reference = reference()
        // day 0 boys weight-for-age: published SD2neg 2.459, SD2 4.419.
        val day0 = reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 0.0)!!
        assertEquals(2.459, valueAtZ(-2.0, day0.l, day0.m, day0.s)!!, 0.005)
        assertEquals(4.419, valueAtZ(2.0, day0.l, day0.m, day0.s)!!, 0.005)
        // day 12: published SD2neg 2.73, SD2 4.831.
        val day12 = reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 12.0)!!
        assertEquals(2.73, valueAtZ(-2.0, day12.l, day12.m, day12.s)!!, 0.005)
        assertEquals(4.831, valueAtZ(2.0, day12.l, day12.m, day12.s)!!, 0.005)
        // ...and reading them back lands on the 2nd and 98th percentiles.
        assertEquals(2.3, percentile(2.459, day0.l, day0.m, day0.s)!!, 0.1)
        assertEquals(97.7, percentile(4.419, day0.l, day0.m, day0.s)!!, 0.1)
    }

    @Test
    fun `the median of every table is the 50th percentile`() {
        val reference = reference()
        for (measure in listOf(
            GrowthMeasure.WEIGHT_FOR_AGE,
            GrowthMeasure.LENGTH_HEIGHT_FOR_AGE,
            GrowthMeasure.HEAD_CIRCUMFERENCE_FOR_AGE,
        )) {
            for (sex in listOf(Sex.BOY, Sex.GIRL)) {
                for (day in listOf(0.0, 210.0, 400.0, 1856.0)) {
                    val lms = reference.lms(measure, sex, day)!!
                    val p = percentile(lms.m, lms.l, lms.m, lms.s)!!
                    assertEquals("$measure/$sex/day $day", 50.0, p, 1e-6)
                }
            }
        }
    }

    @Test
    fun `outside a table's range there is no percentile to give`() {
        val reference = reference()
        assertNull(reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 1857.0))
        assertNull(reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, -1.0))
        assertNotNull(reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 1856.0))
        // The length-keyed table runs 45.0-110.0 cm; extrapolating past it would invent a percentile.
        assertNull(reference.lms(GrowthMeasure.WEIGHT_FOR_LENGTH_HEIGHT, Sex.BOY, 44.9))
        assertNull(reference.lms(GrowthMeasure.WEIGHT_FOR_LENGTH_HEIGHT, Sex.BOY, 110.1))
        assertNotNull(reference.lms(GrowthMeasure.WEIGHT_FOR_LENGTH_HEIGHT, Sex.BOY, 110.0))
    }

    @Test
    fun `the length-keyed table interpolates between its rows`() {
        val reference = reference()
        val rows = reference.table(GrowthMeasure.WEIGHT_FOR_LENGTH_HEIGHT, Sex.BOY)
        val low = rows.first()
        val high = rows[1]
        val midway = reference.lms(GrowthMeasure.WEIGHT_FOR_LENGTH_HEIGHT, Sex.BOY, 45.05)!!
        assertEquals((low.m + high.m) / 2.0, midway.m, 1e-9)
        assertEquals((low.l + high.l) / 2.0, midway.l, 1e-9)
        assertEquals(GrowthAxis.LENGTH_CM, reference.axisOf(GrowthMeasure.WEIGHT_FOR_LENGTH_HEIGHT))
        assertEquals(GrowthAxis.AGE_DAYS, reference.axisOf(GrowthMeasure.WEIGHT_FOR_AGE))
    }

    @Test
    fun `a curve is drawn across the plotted range and stops at the data`() {
        val reference = reference()
        val curve = reference.curve(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 0.0, 730.0, 30.0)
        assertTrue("expected a month of points, got ${curve.size}", curve.size >= 24)
        assertEquals(0.0, curve.first().axis, 1e-9)
        assertTrue(curve.all { it.m > 0.0 })
        // Past the published range there is nothing to draw rather than a straight-line guess.
        assertTrue(reference.curve(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 1800.0, 1900.0, 30.0).size < 5)
    }

    @Test
    fun `adjusted age moves the percentile, which is the point of the setting`() {
        val reference = reference()
        // the children: 30 weeks at birth, so 70 days premature.
        val birth = LocalDate.of(2025, 12, 31)
        val preemie = Child(
            id = "first-twin",
            name = "First Twin",
            birthDate = birth,
            sex = Sex.BOY,
            gestationalAgeDays = 210,
            useAdjustedAge = true,
        )
        assertEquals(70, preemie.prematureDays)
        assertEquals(0L, ageDaysForCharts(preemie, birth.plusDays(70)))

        val measurement = 4.5 // kg
        val adjusted = reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 0.0)!!
        val chronological = reference.lms(GrowthMeasure.WEIGHT_FOR_AGE, Sex.BOY, 70.0)!!
        val adjustedPercentile = percentile(measurement, adjusted.l, adjusted.m, adjusted.s)!!
        val chronologicalPercentile = percentile(measurement, chronological.l, chronological.m, chronological.s)!!

        // 4.5 kg is unremarkable for a 10-week-old and large for a newborn, so the two must disagree.
        // Adjusted age is the YOUNGER age, so the same weight reads HIGHER against it - this is the
        // difference between ~9m1w and ~7m1w that PLAN.md warns about, in the direction that matters.
        assertNotEquals(adjustedPercentile, chronologicalPercentile, 0.5)
        assertTrue(
            "adjusted ($adjustedPercentile) should read higher than chronological ($chronologicalPercentile)",
            adjustedPercentile > chronologicalPercentile + 10.0,
        )
    }

    @Test
    fun `the source files are the ones the research doc verified`() {
        val reference = reference()
        val expected = mapOf(
            GrowthMeasure.WEIGHT_FOR_AGE to "2e2e5c5c1d447f50",
            GrowthMeasure.LENGTH_HEIGHT_FOR_AGE to "7cf462b2a4f8c539",
            GrowthMeasure.HEAD_CIRCUMFERENCE_FOR_AGE to "16ee70120c9c77de",
            GrowthMeasure.BMI_FOR_AGE to "e858a5b67d612b93",
            GrowthMeasure.WEIGHT_FOR_LENGTH_HEIGHT to "ad470cf41b147bd2",
        )
        for ((measure, prefix) in expected) {
            val file = reference.sourceFiles.single { it.measure == measure }
            assertEquals(measure.name, prefix, file.sha256.take(16))
            assertTrue(file.url.startsWith("https://"))
        }
    }

    @Test
    fun `a dataset missing rows is rejected instead of quietly half-working`() {
        val broken = """{"dataset":"x","version":"who-2006.2026.10","publishedAt":"2006-04-27",
            "retrievedAt":"2026-10-15","citation":"c","license":"l","files":[],"measures":{}}"""
        val failure = runCatching { GrowthReferenceParser.parse(broken) }.exceptionOrNull()
        assertTrue("expected GrowthDatasetException, got $failure", failure is GrowthDatasetException)
    }
}
