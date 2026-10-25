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
 *  - **Surfaces and text are SAMPLED from real screenshots of an existing tracker** (15 images,
 *    2026-10-12), by pixel clustering. The palette notes behind them live beside this repo.
 *  - **Accents are BRIGHTENED, deliberately, on request** (2026-10-12). The sampled accents were
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
     *
     * LIFTED when the card got its glyph pattern: the pattern is drawn BEHIND this text, so the text
     * must clear 4.5:1 against the pattern as well as against the plain tint, and the pattern is the
     * lighter of the two. Dark `#C2C6D6` measured 3.2:1 on a pattern tile — a fail — so it moved to
     * `#DCDEE7`, which is 4.5:1 on the worst tile and 5.9:1 on the plain tint. Light moved barely at
     * all, `#41506B` to `#404E69` (4.5:1 on the worst tile, 5.5:1 on the plain tint), because a
     * deepened accent tile does not lift a near-white card much. Pinned by `PatternPaletteTest`.
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
    // Lifted from #C2C6D6 for the card glyph pattern (see textOnCard): 4.5:1 on the worst pattern
    // tile, 5.9:1 on the plain tint.
    textOnCard = Color(0xFFDCDEE7),
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
    // 4.5:1 on the worst pattern tile, 5.5:1 on the plain tint.
    textOnCard = Color(0xFF404E69),
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
 * How far the card's glyph pattern sits from the card fill: the fill moved this fraction further toward
 * its own accent. Tone-on-tone, never white.
 *
 * Why not white, which is what the reference infographic uses: measured across the six accents and both
 * themes, a WHITE pattern over our 26% tint fails in both directions. On dark it costs legibility the
 * moment it is visible — at alpha 0.10 it measures dE 7.8 but drops `textOnCard` to 3.6:1, and at the
 * alpha needed to match the reference's strength (0.16, dE 12.5) it is 3.1:1. On light it is barely
 * there at all: white over a near-white tint is dE 1-4 at every one of those alphas.
 *
 * 0.14 is the measured compromise. Across the six accents it is dE 7-11 on dark and 7-10 on light, so
 * the texture is actually visible in both themes, and the one cost is [textOnCard], which was lifted to
 * keep meta text at 4.5:1 on a tile.
 */
const val PATTERN_ALPHA = 0.14f

/** The colour a card's glyph pattern is drawn in: its own hue, one step stronger than the fill. */
fun BabyLogColors.patternFor(accent: Color): Color = blend(cardFor(accent), accent, PATTERN_ALPHA)

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

/**
 * A dirty nappy's colour and consistency marks (Phase 13.3).
 *
 * The six fills are LITERAL colours - what the parent saw - and are deliberately not taken from the
 * activity accents, from [BabyLogColors.danger] or from [BabyLogColors.background]. An accent means one
 * activity type and nothing else, so a brown stool is not "BabyLog's anything" and a red one is not a
 * destructive action.
 *
 * THE RING IS THE CONTRACT. Black against the dark `surface #222534` measures 1.10:1 and White against the
 * light `surface #FFFFFF` 1.13:1 - without a hairline one of the two vanishes in one of the two themes. So
 * every swatch, selected or not, and the same mark when it is replayed at 8dp on a card row, carries a 1dp
 * ring.
 *
 * The ring is a DEDICATED colour per theme, not `textSecondary`, and the reason is measurable: a fill set
 * that runs from black to white has no single grey that clears 3:1 against all six (the mid-greys measured
 * 1.0-2.9:1 against the ends of the range). So each theme gets the pole it needs - near-white on dark,
 * near-black on light - and the rule the test enforces is the one that actually matters: at every circle's
 * EDGE either the fill contrasts with the sheet (>= 3:1) or the ring contrasts with the fill (>= 3:1), and
 * the ring always reads against the sheet itself (10.67:1 dark, 9.95:1 light). Measured results, per
 * theme: the only fills that rely on the ring are Brown (2.60:1 on dark, ring 4.10:1) and Black
 * (1.10:1, ring 11.74:1) on dark, and Yellow (1.69:1 on light, ring 5.88:1) and White (1.13:1, ring
 * 8.81:1) on light. `DiaperPaletteTest` asserts all twelve cases, so retuning a fill or dropping the ring
 * fails the build rather than shipping a blank dot.
 *
 * Selection adds a ring OUTSIDE the hairline and never changes the circle's size, because a row that
 * shifts as you tick boxes is the same defect the three temperature bands were given one geometry to
 * avoid.
 */
object DiaperSwatch {
    val Yellow = Color(0xFFF2C11E)
    val Brown = Color(0xFF8B5A2B)
    val Black = Color(0xFF241C1A)
    val Green = Color(0xFF5C8A21)
    val Red = Color(0xFFD93A2B)
    val White = Color(0xFFF3F1EC)

    /**
     * The selector's circle. The MARKS are 6 x 36dp + 5 x 10dp = 266dp inside the 328dp a 360dp phone
     * leaves; the TAP TARGETS are 44dp boxes around them (6 x 44dp + 5 x 10dp = 314dp, still inside that
     * 328dp) so the visible circles stay close together while every one of them meets the touch minimum.
     * A narrower phone wraps the row rather than clipping it.
     */
    val size = 36.dp
    val gap = 10.dp

    /** Both rings: a permanent hairline, then the selection ring outside it. */
    val hairline = 1.dp
    val selectionRing = 2.dp

    /**
     * The permanent hairline's colour, per theme. Near-white on the dark scheme and near-black on the light
     * one, because those are the poles a fill spanning black-to-white always has something to say against -
     * see the measured derivation above. Keyed on [BabyLogColors.isDark] rather than a `when` on a theme
     * name so a third theme cannot silently fall through to no ring.
     */
    fun ringFor(colors: BabyLogColors): Color = if (colors.isDark) Color(0xFFD5D8E2) else Color(0xFF3C4350)

    /** The same mark replayed on a card row, where it is a bullet rather than a control. */
    val dot = 8.dp
}
