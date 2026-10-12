package app.babylog.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Design tokens. Source of truth for the values and the reasoning:
//   .hermes/skills/babylog-ui/references/design-tokens.md
// Rule: an accent colour means an event type. Never reuse one for non-event UI
// (buttons, links, badges) or the colour code stops working.

// Surfaces and text
val Bg = Color(0xFFF7F9FA)
val Surface = Color(0xFFFFFFFF)
val Divider = Color(0xFFE6EAEF)
val TextPrimary = Color(0xFF1F3A5F)
val TextSecondary = Color(0xFF6B7C8E)

// Navigation
val NavInactive = Color(0xFF9AA6B2)
val NavActive = Color(0xFF2E5B8A)

// One hue per event type (hue families verified in both the comparison app and the reference app)
val AccentSleep = Color(0xFF5BA8D9)
val AccentFeed = Color(0xFFF5A623)
val AccentNursing = Color(0xFFEF8A5E)
val AccentDiaper = Color(0xFFF2C438)
val AccentPump = Color(0xFFB08BE0)

// Reserved for V2 so a solids hue is never improvised later.
val AccentSolids = Color(0xFFD46AA8)

object Spacing {
    val gutter = 16.dp
    val cardPadding = 16.dp
    val cardGap = 12.dp
    val sectionGap = 28.dp
    val bandHeight = 44.dp
}

object Radius {
    val card = 20.dp
    val pill = 999.dp
}
