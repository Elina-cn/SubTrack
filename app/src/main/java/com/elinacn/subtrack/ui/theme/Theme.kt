package com.elinacn.subtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The `on*` roles are spelled out on purpose: the UI reads every colour through the scheme, so a
// missing role would silently fall back to a Material default that does not match the palette.
private val DarkColorScheme = darkColorScheme(
    primary = PastelBlue,
    onPrimary = DarkText,
    secondary = PastelMint,
    onSecondary = DarkText,
    tertiary = PastelGray,
    background = DarkBackground,
    onBackground = PastelGray,
    surface = DarkText,
    onSurface = PastelGray
)

private val LightColorScheme = lightColorScheme(
    primary = PastelBlue,
    onPrimary = DarkText,
    secondary = PastelMint,
    onSecondary = DarkText,
    tertiary = PastelGray,
    background = PastelGray,
    onBackground = DarkText,
    surface = Color.White,
    onSurface = DarkText
)

@Composable
fun SubTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
