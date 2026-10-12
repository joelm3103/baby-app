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
 *  - **Surfaces and text are SAMPLED from real the reference app screenshots** (15 images, 2026-10-12, in
 *    `research/screenshots/raw-captures/`). Derivation: `research/findings/palette.md`.
 *  - **Accents are BRIGHTENED, deliberately, on request** (2026-10-12). The sampled the reference app accents were
 *    muted *and* two pairs were nearly the same hue — Solids 35 deg vs Bottle 43 deg, and Growth 85 deg
 *    vs Diaper 76 deg, the latter two also washed out (chroma 0.10-0.16). the reference app's own palette is not
 *    legible enough to read at a glance, which is the entire point of a colour-coded dashboard. The
 *    sampled hues were kept as the *families* and the chroma and lightness raised, then each accent was
 *    checked to clear 4.5:1 as a label on its own card. Light values are the designed mirror.
 *  - The first palette (built from Google Play *marketing frames*) was wrong in two specific ways:
 *    it assumed white cards with dark navy text, and it read the diaper accent as yellow. Real
 *    screenshots show a dark app whose diaper accent is a muted sage.
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
    /** Text/icon colour to place ON an accent-filled shape. Never use textPrimary there. */
    val onAccent: Color,
    // One hue per activity type. Hues are deliberately well separated: 40 / 11 / 143 / 255 / 329 / 201.
    val accentBottle: Color,
    val accentSolids: Color,
    val accentSleep: Color,
    val accentDiaper: Color,
    val accentGrowth: Color,
    val accentHealth: Color,
)

/**
 * Sampled surfaces, brightened accents. The three largest sampled clusters were `#181A26` (3.9% of
 * pixels), `#222534` (21.0%) and `#2D313D` (35.5%) — background, card surface, raised surface.
 */
val DarkColors = BabyLogColors(
    background = Color(0xFF181A26),
    surface = Color(0xFF222534),
    surfaceRaised = Color(0xFF2D313D),
    divider = Color(0xFF343B4D),
    textPrimary = Color(0xFFEAE9EC),
    textSecondary = Color(0xFF9AA0B4),
    navInactive = Color(0xFF7E8598),
    navActive = Color(0xFFEAE9EC),
    onAccent = Color(0xFF181A26),
    // Feed keeps the reference app's gold family (measured ~#C29D36-#EEC864), raised to full chroma.
    accentBottle = Color(0xFFFFC857),
    // Solids moves to coral. At the reference app's tan it was 8 degrees of hue from Bottle's gold, which is not
    // a difference anyone can read across a room.
    accentSolids = Color(0xFFFF7A5C),
    // Sleep: sky blue. Still the one hue the reference app never showed, so it remains a designed choice.
    accentSleep = Color(0xFF63C8FF),
    // Diaper keeps the sage family (the `#9EA797` cluster) but at 5x the chroma, so it reads green.
    accentDiaper = Color(0xFF5AD98A),
    // Growth: violet. Deliberately away from every other hue - it is about charts, not feeding.
    accentGrowth = Color(0xFFB49CFF),
    // Health: magenta. Was lavender, which sat too close to Sleep's blue.
    accentHealth = Color(0xFFFF7BC0),
)

/** Designed mirror of [DarkColors]: same hue families, deepened to hold contrast on white. */
val LightColors = BabyLogColors(
    background = Color(0xFFF7F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF1F3F6),
    divider = Color(0xFFE3E7ED),
    textPrimary = Color(0xFF1F2333),
    textSecondary = Color(0xFF6B7280),
    navInactive = Color(0xFF9AA0B4),
    navActive = Color(0xFF1F2333),
    onAccent = Color(0xFFFFFFFF),
    accentBottle = Color(0xFF9A6B00),
    accentSolids = Color(0xFFB93E17),
    accentSleep = Color(0xFF1A6AAE),
    accentDiaper = Color(0xFF186B3D),
    accentGrowth = Color(0xFF5B3CC4),
    accentHealth = Color(0xFFB02A6E),
)

val LocalBabyLogColors = staticCompositionLocalOf { DarkColors }

/**
 * Cards are NOT tinted. Measured: a 0.16 tint over `surface` collapses the accents to chroma 0.09
 * (olive-brown, maroon), and raising the fraction to 0.22+ restores hue but drops the accent label to
 * 3.6:1 — below AA. A strongly tinted dark card cannot also be a readable background for its own
 * accent label, so the tint was the wrong device. Identity comes from a full-chroma accent bar at the
 * card's leading edge plus the accent-filled action button, both of which carry the hue at full
 * strength. `accentOnSurface = 8.2:1` (Bottle) worst case, versus 4.58:1 under a tint.
 */
private fun blend(base: Color, over: Color, fraction: Float) = Color(
    red = base.red + (over.red - base.red) * fraction,
    green = base.green + (over.green - base.green) * fraction,
    blue = base.blue + (over.blue - base.blue) * fraction,
    alpha = 1f,
)

object Spacing {
    val gutter = 16.dp
    val cardPadding = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val bottomBarHeight = 64.dp

    /** Width of a card's leading accent bar. Full chroma, drawn behind the content. */
    val accentBar = 4.dp
}

object Radius {
    val card = 20.dp
    val pill = 999.dp
}
