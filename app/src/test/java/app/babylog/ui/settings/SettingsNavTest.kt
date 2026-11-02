package app.babylog.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings' two levels, and the one rule about going back.
 *
 * Settings is a category menu with a submenu per category, and system back has to mean "up one level"
 * before it means "leave Settings". That is the only logic on this screen that a build cannot prove, so
 * it is a value with tests rather than an `if` inside a back handler: a menu that swallowed back would
 * trap the parent in Settings, and a submenu that passed back through would dump them on Activity from
 * a screen they had just opened.
 */
class SettingsNavTest {

    @Test
    fun theMenuIsTheRootSoBackLeavesSettings() {
        // Null is the shell's instruction to leave Settings for Activity, not a bug in the answer.
        assertNull(SettingsNav().back())
    }

    @Test
    fun backFromASubmenuLandsOnTheMenu() {
        SettingsSection.entries.forEach { section ->
            assertEquals(SettingsNav(), SettingsNav(section).back())
        }
    }

    @Test
    fun openingFromASubmenuReplacesItRatherThanNesting() {
        // Settings is two levels deep by design. A stack would invite a third, and a parent who tapped
        // two categories in a row should end up in the second one, not two backs away from the menu.
        val nested = SettingsNav(SettingsSection.APPEARANCE).open(SettingsSection.CHILDREN)
        assertEquals(SettingsNav(SettingsSection.CHILDREN), nested)
        assertEquals(SettingsNav(), nested.back())
    }

    @Test
    fun everySettingGroupHasItsOwnMenuEntry() {
        // Eleven groups is the whole of Settings, in the order the menu lists them. A group that lost its
        // entry would be unreachable — it would not appear in a build failure, only as a missing row.
        assertEquals(
            listOf(
                "Appearance",
                "Units",
                "Day & night",
                "Bottle presets",
                "Medication presets",
                "Children",
                "Activities",
                "Data",
                "Sync",
                "Growth reference",
                "About",
            ),
            SettingsSection.entries.map { it.title },
        )
    }

    @Test
    fun menuEntryNamesAreUnique() {
        // The back arrow's content description is built from the title, and two groups with one name
        // would give a screen reader the same label for two different destinations.
        val titles = SettingsSection.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(titles.none { it.isBlank() })
    }
}
