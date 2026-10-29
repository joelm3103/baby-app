package app.babylog.domain

/**
 * The one line the About card prints about the build it is.
 *
 * A pure function so the wording is testable, and because the version a phone reports has to come from
 * `BuildConfig` rather than from anything typed by hand: a Settings row that says "1.0.0" over a `1.1.0`
 * build is worse than no row at all, since it is the row a parent would quote in a bug report.
 */
fun aboutVersionLine(versionName: String, versionCode: Int, debug: Boolean): String {
    val flavour = if (debug) "debug build" else "release build"
    return "Version $versionName (build $versionCode) \u00b7 $flavour"
}

/** Where the source lives. Opened from the About card, and named so the label and the link cannot drift. */
const val SOURCE_REPOSITORY_URL: String = "https://github.com/joelm3103/baby-app"

/**
 * The credits, as the About card shows them.
 *
 * Kept out of the composable so the naming is one decision: the app is for two specific children, and a
 * generic "your baby" would be a stranger's if copied.
 */
const val ABOUT_CREDITS: String = "Built by joelm3103, for Kai and Levi."
