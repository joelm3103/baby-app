package app.babylog.ui.reminders

import app.babylog.domain.ReminderConfig
import app.babylog.domain.ReminderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the reminder sheet saves.
 *
 * The sheet no longer asks whether to notify, so the rule that matters is that saving always leaves an ENABLED
 * rule behind. This is the defect the phone found in Phase 17 and Phase 20 fixes: the sheet opened with `Notify`
 * off, so `Set` wrote a rule that looked saved on the card, armed nothing, and said nothing about why. A test
 * rather than a comment, because the failing version compiled perfectly.
 */
class ReminderSaveTest {

    @Test
    fun savingAStoredDisabledRuleBringsItBackOn() {
        // The exact trap: a rule left switched off, opened to be changed, and saved. It used to be written back
        // still off — indistinguishable on the card from a rule that works.
        val stored = ReminderConfig(enabled = false, intervalMinutes = 240, skipNight = true)
        val saved = sheetSaveConfig(stored)
        assertTrue(saved.enabled)
        // Everything else the parent chose survives: this is a re-enable, not a reset.
        assertEquals(240, saved.intervalMinutes)
        assertTrue(saved.skipNight)
    }

    @Test
    fun aFreshRuleIsSavedOn() {
        assertTrue(sheetSaveConfig(ReminderConfig()).enabled)
    }

    @Test
    fun theIntervalIsNotResetToTheDefault() {
        val chosen = ReminderConfig(mode = ReminderMode.AFTER_LAST, intervalMinutes = 90)
        assertEquals(90, sheetSaveConfig(chosen).intervalMinutes)
        assertEquals(ReminderMode.AFTER_LAST, sheetSaveConfig(chosen).mode)
    }
}
