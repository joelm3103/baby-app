package app.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import app.babylog.domain.DiaperColor
import app.babylog.domain.DiaperConsistency
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


/**
 * The fill for a diaper colour (Phase 13.3). A literal colour, kept out of [BabyLogColors] on purpose:
 * those tokens are semantic roles, and "brown" is a fact about a nappy rather than a role in this app.
 * Every swatch is drawn WITH its hairline ring — see [DiaperSwatch] for why Black and White cannot
 * appear without one.
 */
fun DiaperColor.swatch(): Color = when (this) {
    DiaperColor.YELLOW -> DiaperSwatch.Yellow
    DiaperColor.BROWN -> DiaperSwatch.Brown
    DiaperColor.BLACK -> DiaperSwatch.Black
    DiaperColor.GREEN -> DiaperSwatch.Green
    DiaperColor.RED -> DiaperSwatch.Red
    DiaperColor.WHITE -> DiaperSwatch.White
}

/** The word: `Brown`, used for TalkBack and for the row's grouped description — never printed as text. */
fun DiaperColor.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/** The word for a consistency chip: `Mucousy`. */
fun DiaperConsistency.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
