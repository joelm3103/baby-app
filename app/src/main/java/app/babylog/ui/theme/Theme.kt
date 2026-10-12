package app.babylog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Follows the phone's system theme (the owner's explicit choice, 2026-10-12) rather than being dark- or
 * light-only. Accent hues are identical in both schemes; only surfaces and text flip.
 *
 * This object/function pairing mirrors Material's own `MaterialTheme`, so `BabyLogTheme.colors`
 * reads naturally at call sites.
 */
object BabyLogTheme {
    val colors: BabyLogColors
        @Composable @ReadOnlyComposable get() = LocalBabyLogColors.current
}

@Composable
fun BabyLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalBabyLogColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(darkTheme),
            typography = BabyLogTypography,
            content = content,
        )
    }
}

private fun BabyLogColors.toMaterialScheme(dark: Boolean) =
    if (dark) darkColorScheme(
        primary = navActive,
        onPrimary = background,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        outline = divider,
        outlineVariant = divider,
    ) else lightColorScheme(
        primary = navActive,
        onPrimary = surface,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        outline = divider,
        outlineVariant = divider,
    )
