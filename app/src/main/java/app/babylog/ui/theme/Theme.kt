package app.babylog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Only a light scheme: neither source app has a dark theme, and the palette is chosen
// for low-brightness night legibility on white. Keep every colour behind these tokens so
// adding a dark column later is one file, not a hunt.
private val BabyLogColors = lightColorScheme(
    primary = NavActive,
    onPrimary = Surface,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Bg,
    onSurfaceVariant = TextSecondary,
    outlineVariant = Divider,
)

@Composable
fun BabyLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BabyLogColors,
        typography = BabyLogTypography,
        content = content,
    )
}
