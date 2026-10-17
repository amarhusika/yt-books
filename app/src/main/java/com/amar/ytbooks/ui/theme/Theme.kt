package com.amar.ytbooks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.amar.ytbooks.util.findActivity

private val DarkScheme = darkColorScheme(
    primary = WhiteSmoke,
    onPrimary = Black,
    primaryContainer = Graphite,
    onPrimaryContainer = White,
    secondary = Silver,
    onSecondary = Black,
    secondaryContainer = Slate,
    onSecondaryContainer = WhiteSmoke,
    tertiary = Gray,
    onTertiary = Black,
    background = Black,
    onBackground = WhiteSmoke,
    surface = Ink,
    onSurface = WhiteSmoke,
    surfaceVariant = Graphite,
    onSurfaceVariant = Gray,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Ink,
    surfaceContainer = Charcoal,
    surfaceContainerHigh = Graphite,
    surfaceContainerHighest = Slate,
    surfaceBright = Slate,
    surfaceDim = Black,
    outline = Steel,
    outlineVariant = Slate,
    inverseSurface = WhiteSmoke,
    inverseOnSurface = Black,
    inversePrimary = Black,
    scrim = Black,
)

private val LightScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = Cloud,
    onPrimaryContainer = Black,
    secondary = Ash,
    onSecondary = White,
    secondaryContainer = Mist,
    onSecondaryContainer = Black,
    tertiary = Gray,
    onTertiary = White,
    background = WhiteSmoke,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = Cloud,
    onSurfaceVariant = Ash,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = Cloud,
    surfaceContainerHighest = Mist,
    surfaceBright = White,
    surfaceDim = Mist,
    outline = Silver,
    outlineVariant = Mist,
    inverseSurface = Black,
    inverseOnSurface = WhiteSmoke,
    inversePrimary = WhiteSmoke,
    scrim = Black,
)

@Composable
fun YtBooksTheme(
    content: @Composable () -> Unit
) {
    // Always follows the system light/dark setting
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
