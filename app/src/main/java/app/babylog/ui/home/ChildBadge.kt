package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.babylog.ui.theme.BabyLogTheme

/**
 * A child's badge — one implementation for the switcher, the cards, the recent rows, the event list, the
 * Growth chart and the fever table, so they cannot drift apart.
 *
 * Deliberately NEUTRAL. A colour in this app means one activity type and nothing else, so giving each
 * child a hue would introduce a second colour code and blunt the first one. The letter is the identifier,
 * and a letter shared by two children is numbered by `badgesFor()` (`K1`, `K2`).
 *
 * One glyph is a circle; two glyphs are a pill of the same height, because the type must not shrink to fit
 * and the shape difference marks the numbered child. The height never changes, so a row that shows a badge
 * keeps its rows where they are.
 *
 * The glyph is centred by pinning `lineHeight` to the font size. A `Text`'s default line box is taller
 * than its glyph, so centring the box alone leaves the letter visibly low inside the circle — which is
 * exactly how the switcher's avatar looked before this component existed.
 */
@Composable
fun ChildBadge(
    initials: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 20.dp,
) {
    val colors = BabyLogTheme.colors
    val oneGlyph = initials.length <= 1
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(size)
            .widthIn(min = size)
            .clip(if (oneGlyph) CircleShape else RoundedCornerShape(percent = 50))
            .background(if (selected) colors.textPrimary else colors.divider)
            .padding(horizontal = if (oneGlyph) 0.dp else 4.dp),
    ) {
        Text(
            text = initials,
            style = TextStyle(
                fontSize = (size.value * 0.5f).sp,
                lineHeight = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            color = if (selected) colors.surface else colors.textSecondary,
        )
    }
}
