package app.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import app.babylog.domain.EventType

/** The single mapping from activity type to hue. One hue per type, never reused for anything else. */
fun BabyLogColors.accentFor(type: EventType): Color = when (type) {
    EventType.BOTTLE -> accentBottle
    EventType.SOLIDS -> accentSolids
    EventType.SLEEP -> accentSleep
    EventType.DIAPER -> accentDiaper
    EventType.GROWTH -> accentGrowth
    EventType.HEALTH -> accentHealth
}

fun EventType.displayName(): String = when (this) {
    EventType.BOTTLE -> "Bottle feed"
    EventType.SOLIDS -> "Solids"
    EventType.SLEEP -> "Sleep"
    EventType.DIAPER -> "Diaper"
    EventType.GROWTH -> "Growth"
    EventType.HEALTH -> "Health"
}
