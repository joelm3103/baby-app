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
    /**
     * Meta text (relative times, recent rows) rendered on a TINTED card. Distinct from
     * `textSecondary`, which is tuned for the plain background: at a 0.26 tint the six card fills
     * carry enough colour that `textSecondary` measures 3.06:1, below AA. This clears 4.5:1 on all six.
     */
    val textOnCard: Color,
    /**
     * DESTRUCTIVE actions only — permanently deleting a child. A semantic role, deliberately NOT one of
     * the six activity accents: reusing an activity hue for "danger" would break the colour code, and a
     * seventh activity hue would be indistinguishable from the six that exist. Use it off tinted cards
     * (it is measured against `background` and `surface`), and [onAccent] for text on a danger fill.
     *
     * A fever reading is the one other thing that needs a red, and it does NOT use this: on a tinted
     * card it measures 3.76:1 (dark) / 4.28:1 (light), below AA. See [feverOnCard].
     */
    val danger: Color,
    /**
     * A FEVER reading rendered ON a tinted card — the word `fever` beside a temperature at or above
     * 38.0 °C. A reading is not a destructive action, so it is its own role rather than a second use of
     * [danger].
     *
     * Derived the way [labelFor] derives a label — toward white on dark, toward black on light — but at
     * 0.45 / 0.30 rather than 0.55: measured as the smallest fraction that clears 4.5:1 on ALL SIX card
     * tints (worst 4.80:1 dark, 6.71:1 light) while keeping more of the red than 0.55 would. On the
     * Health card specifically it measures 5.71:1 dark, 6.75:1 light.
     *
     * Needed on any TINTED surface: the dashboard card and its Both-mode columns, the entry-list rows,
     * and History's total cards, which use the same 26% tint. `danger` stays what it was — destructive
     * actions only.
     */
    val feverOnCard: Color,
    /** Whether this is the dark scheme. Drives [labelFor]'s direction. */
    val isDark: Boolean,
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
    textOnCard = Color(0xFFC2C6D6),
    // 6.85:1 on background, 6.02:1 on surface; #181A26 text on this fill is 6.85:1.
    danger = Color(0xFFFF7A7A),
    // danger toward white 0.45: 5.71:1 on the Health tint, 4.80:1 worst of the six, 10.39:1 on background.
    feverOnCard = Color(0xFFFFB6B6),
    isDark = true,
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
    textOnCard = Color(0xFF41506B),
    // 6.19:1 on background, 6.54:1 on surface; white text on this fill is 6.54:1.
    danger = Color(0xFFB3261E),
    // danger toward black 0.30: 6.75:1 on the Health tint, 6.71:1 worst of the six, 9.75:1 on background.
    feverOnCard = Color(0xFF7D1B15),
    isDark = false,
    accentBottle = Color(0xFF9A6B00),
    accentSolids = Color(0xFFB93E17),
    accentSleep = Color(0xFF1A6AAE),
    accentDiaper = Color(0xFF186B3D),
    accentGrowth = Color(0xFF5B3CC4),
    accentHealth = Color(0xFFB02A6E),
)

val LocalBabyLogColors = staticCompositionLocalOf { DarkColors }

/**
 * The card surface for an activity: the accent blended into the card surface at 26%.
 *
 * The fraction is measured, not taste. Full-section tinting was requested because it identifies a
 * section at a glance, and the numbers were tested rather than assumed:
 *
 *  - At 0.16 the fills are only dE 7.7 apart - barely separable - and `textSecondary` already fails
 *    at 3.98:1.
 *  - At 0.26 the fills are dE 12.5 apart, which is plainly distinguishable, and the lifted label
 *    clears 4.5:1.
 *  - Above 0.30 the tint starts washing out the label and the accent-filled button.
 *
 * The cost of a tint this strong: neither the raw accent (`#FF7A5C` measures 3.98:1 as a label on its
 * own tint) nor `textSecondary` is legible on it. So the label uses [labelFor] - the accent lifted
 * toward white - and meta text uses `textOnCard`. That is the trade: a tinted section means the label
 * is a lighter cousin of the accent, not the accent itself.
 */
fun BabyLogColors.cardFor(accent: Color): Color = blend(surface, accent, 0.26f)

/**
 * The activity label colour ON a tinted card. The raw accent is NOT legible there (3.4-4.3:1), so it
 * is lifted toward white on dark and deepened toward black on light, which keeps the hue cue in the
 * text while clearing AA.
 */
fun BabyLogColors.labelFor(accent: Color): Color =
    if (isDark) blend(accent, Color.White, 0.55f) else blend(accent, Color.Black, 0.55f)

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

    /**
     * A card's entry rows. Measured down from 8dp of vertical padding and an 18dp badge, which sat a
     * 20dp line of text in a 35dp row - over 40% air, on a card that is often read at arm's length.
     * The badge is also reserved for Both mode: six identical K badges down one card say nothing.
     */
    val rowVertical = 5.dp
    val rowBlockTop = 8.dp
    val rowBadge = 16.dp
}

object Radius {
    val card = 20.dp
    val pill = 999.dp
}
