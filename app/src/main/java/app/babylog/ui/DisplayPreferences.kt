package app.babylog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.babylog.domain.TimeFormat
import app.babylog.domain.TemperatureUnit
import app.babylog.domain.TimeInputMode

val LocalTimeFormat = staticCompositionLocalOf { TimeFormat.TWELVE_HOUR }
val LocalTemperatureUnit = staticCompositionLocalOf { TemperatureUnit.CELSIUS }

/**
 * Which time input the time dialog opens on. Defaults to the wheels, matching the stored preference's own
 * default — a local that disagreed with `SettingsRepository` would be a dialog and a Settings row saying
 * different things until the flow's first emission.
 */
val LocalTimeInputMode = staticCompositionLocalOf { TimeInputMode.WHEEL }

/**
 * The ONE writer of that preference.
 *
 * Provided by `AppRoot` and read by both the dialog's switch and the Settings row, so the two entry points
 * cannot drift into being two values. An empty body here is what a preview or a stray composition gets:
 * a switch that stays correct for the dialog's own session and remembers nothing.
 */
val LocalSetTimeInputMode = staticCompositionLocalOf<(TimeInputMode) -> Unit> { {} }
