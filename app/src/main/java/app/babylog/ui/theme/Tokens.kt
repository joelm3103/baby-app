package app.babylog.ui.theme

import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Semantic colour tokens, resolved per theme. Access them with `BabyLogTheme.colors`.
 *
 * PROVENANCE — do not "tidy" these values without evidence:
 *
 *  - **Dark values are SAMPLED from real the reference app screenshots** (15 images, 2026-10-12, in
 *    `research/screenshots/raw-captures/`). Derivation: `research/findings/palette.md`.
 *  - **Light values are DESIGNED, not sampled.** the owner's phone runs dark mode, so there are no
 *    light-mode the reference app screenshots. The light scheme is a deliberate mirror — same hue families,
 *    deepened to hold contrast on white. Re-derive if light-mode screenshots ever arrive.
 *  - The first palette (built from Google Play *marketing frames*) was wrong in two specific ways:
 *    it assumed white cards with dark navy text, and it read the diaper accent as yellow. Real
 *    screenshots show a dark app whose diaper accent is a muted sage — the "yellow" was an artefact
 *    of the rotated marketing composite.
 *
 * RULE: an accent colour means one activity type and nothing else. Never reuse one for a button,
 * link, badge or focus ring, or the colour code stops working.
 */
data class BabyLogColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val navInactive: Color,
    val navActive: Color,
    // One hue per activity type
    val accentBottle: Color,
    val accentSolids: Color,
    val accentSleep: Color,
    val accentDiaper: Color,
    val accentGrowth: Color,
    val accentHealth: Color,
)

/**
 * Sampled from the reference app in dark mode. The three largest clusters across all 15 screenshots were
 * `#181A26` (3.9% of pixels), `#222534` (21.0%) and `#2D313D` (35.5%) — background, card surface,
 * and raised/secondary surfaces.
 */
val DarkColors = BabyLogColors(
    background = Color(0xFF181A26),
    surface = Color(0xFF222534),
    surfaceRaised = Color(0xFF2D313D),
    divider = Color(0xFF343B4D),
    textPrimary = Color(0xFFEAE9EC),
    textSecondary = Color(0xFF9AA0B4),
    navInactive = Color(0xFF6C7285),
    navActive = Color(0xFFEAE9EC),
    // Feed's gold measured on the Activity screen (#01) at ~#C29D36-#EEC864.
    accentBottle = Color(0xFFEEC864),
    accentSolids = Color(0xFFE8C79A),
    // Sleep appeared on NO screen the owner captured (he rarely tracks it but wants it present), so this
    // hue is CARRIED from the marketing frames and is UNVERIFIED. Confirm before trusting it.
    accentSleep = Color(0xFF8EC5F0),
    // Diaper reads as a muted sage/beige in dark mode (the `#9EA797` cluster), not the yellow the
    // marketing frames suggested.
    accentDiaper = Color(0xFFA9B096),
    accentGrowth = Color(0xFFD7E8BF),
    accentHealth = Color(0xFFCDD0ED),
)

/** Designed mirror of [DarkColors]: same hue families, deepened for contrast against white. */
val LightColors = BabyLogColors(
    background = Color(0xFFF7F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF1F3F6),
    divider = Color(0xFFE3E7ED),
    textPrimary = Color(0xFF1F2333),
    textSecondary = Color(0xFF6B7280),
    navInactive = Color(0xFF9AA0B4),
    navActive = Color(0xFF1F2333),
    accentBottle = Color(0xFFB8860B),
    accentSolids = Color(0xFFA9762F),
    accentSleep = Color(0xFF3E8FD0),
    accentDiaper = Color(0xFF6F7A5A),
    accentGrowth = Color(0xFF5E8C2E),
    accentHealth = Color(0xFF6F76C4),
)

val LocalBabyLogColors = staticCompositionLocalOf { DarkColors }

/**
 * The card surface for an activity. The reference mockup tints the whole card by activity rather
 * than banding only the header, so this is a blend, not the accent itself. Keep the fraction low:
 * the accent must stay readable as a label colour on top of it.
 */
fun BabyLogColors.cardFor(accent: Color): Color = blend(surface, accent, 0.14f)

/** Slightly stronger tint, for a card's full-width action button. */
fun BabyLogColors.actionFor(accent: Color): Color = blend(surfaceRaised, accent, 0.30f)

private fun blend(base: Color, overlay: Color, fraction: Float) = Color(
    red = base.red + (overlay.red - base.red) * fraction,
    green = base.green + (overlay.green - base.green) * fraction,
    blue = base.blue + (overlay.blue - base.blue) * fraction,
    alpha = 1f,
)

object Spacing {
    val gutter = 16.dp
    val cardPadding = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val bottomBarHeight = 64.dp
}

object Radius {
    val card = 20.dp
    val pill = 999.dp
}
