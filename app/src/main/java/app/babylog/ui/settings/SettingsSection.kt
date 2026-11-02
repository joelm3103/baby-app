package app.babylog.ui.settings

/**
 * One Settings submenu.
 *
 * Settings used to be a single list of eleven section cards, so reaching "bottle presets" meant
 * scrolling past six unrelated groups. It is now an index — every group listed by name with a
 * one-line summary of its current value — and each name opens its own submenu.
 *
 * The declaration order IS the menu order, and [title] is the only name a group has: it is the menu
 * row, the submenu's heading and what the back arrow returns to. A second label beside it is how the
 * two come to disagree.
 */
enum class SettingsSection(val title: String) {
    APPEARANCE("Appearance"),
    UNITS("Units"),
    DAY_NIGHT("Day & night"),
    BOTTLE_PRESETS("Bottle presets"),
    MEDICATION_PRESETS("Medication presets"),
    CHILDREN("Children"),
    ACTIVITIES("Activities"),
    DATA("Data"),
    SYNC("Sync"),
    GROWTH_REFERENCE("Growth reference"),
    ABOUT("About"),
}

/**
 * Where Settings is, and the one rule about going back.
 *
 * Settings has exactly two levels, so this is a value rather than a stack: opening a submenu from a
 * submenu REPLACES it instead of nesting. Null section is the category menu, which is Settings' root
 * — so there is nothing for Settings to consume there and [back] answers null, which the shell reads
 * as "leave Settings". Keeping that decision here, rather than in the back handler, is what makes it
 * testable without a device.
 */
data class SettingsNav(val section: SettingsSection? = null) {
    fun open(section: SettingsSection): SettingsNav = SettingsNav(section)

    /** The state a back press lands on, or null when Settings has nothing left to consume. */
    fun back(): SettingsNav? = section?.let { SettingsNav(null) }
}
