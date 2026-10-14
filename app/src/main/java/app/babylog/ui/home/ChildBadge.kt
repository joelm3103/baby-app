package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
 * A child's initial in a small circle — one implementation for the child switcher and the recent log
 * rows, so the two cannot drift apart.
 *
 * Deliberately NEUTRAL. A colour in this app means one activity type and nothing else, so giving each
 * child a hue would introduce a second colour code and blunt the first one. The letter is the
 * identifier; the two children differ by `K` and `L`.
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) colors.textPrimary else colors.divider),
    ) {
        Text(
            text = initials,
            style = TextStyle(
                fontSize = (size.value * 0.5f).sp,
                lineHeight = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            color = if (selected) colors.surface else colors.textSecondary,
        )
    }
}
