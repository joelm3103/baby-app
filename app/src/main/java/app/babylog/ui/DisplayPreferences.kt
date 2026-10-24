package app.babylog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.babylog.domain.TimeFormat
import app.babylog.domain.TemperatureUnit

val LocalTimeFormat = staticCompositionLocalOf { TimeFormat.TWELVE_HOUR }
val LocalTemperatureUnit = staticCompositionLocalOf { TemperatureUnit.CELSIUS }
