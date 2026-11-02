package app.babylog.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one-line values the category menu shows, which are the only text on that screen a build cannot
 * check.
 *
 * Both functions here exist because the phone found them wrong in the Phase 19 pass: `2 childs` was on
 * the Children row, and the full growth identity line wrapped to two lines and made its row taller than
 * every other row in the menu. A green build proved neither, so both are pinned here.
 */
class SettingsSummaryTest {

    @Test
    fun countsUseTheRightPluralForChildren() {
        // The one that shipped wrong: appending an `s` to `child`.
        assertEquals("None", counted(0, singular = "child", plural = "children"))
        assertEquals("1 child", counted(1, singular = "child", plural = "children"))
        assertEquals("2 children", counted(2, singular = "child", plural = "children"))
        assertEquals("11 children", counted(11, singular = "child", plural = "children"))
    }

    @Test
    fun countsDefaultToAnAppendedS() {
        assertEquals("None", counted(0, "preset"))
        assertEquals("1 preset", counted(1, "preset"))
        assertEquals("3 presets", counted(3, "preset"))
    }

    @Test
    fun theDatasetSummaryKeepsTheVersionAndDropsTheUpdateDate() {
        assertEquals(
            "WHO 2006 · v2026.10",
            datasetSummary("WHO 2006 · v2026.10 · updated 15 Oct 2026"),
        )
    }

    @Test
    fun theDatasetSummarySurvivesAnIdentityLineItDoesNotRecognise() {
        // A dataset whose version has no `-year` suffix builds a shorter line; it must still render
        // rather than vanish, because this row is where a parent finds out which tables they are on.
        assertEquals("v2026.10", datasetSummary("v2026.10"))
        assertNull(datasetSummary(null))
    }
}
