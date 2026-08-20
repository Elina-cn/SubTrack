package com.elinacn.subtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The `on*` roles are spelled out on purpose: the UI reads every colour through the scheme, so a
// missing role would silently fall back to a Material default that does not match the palette.
// Two blues do different jobs. `primary` is the accent that has to be legible as text and icons on
// a card, so it is the dark one. `primaryContainer` is the pastel that fills the dashboard card,
// the FAB and the save button, with DarkText on top of it.
private val DarkColorScheme = darkColorScheme(
    primary = PastelBlue,
    onPrimary = DarkText,
    primaryContainer = PastelBlue,
    onPrimaryContainer = DarkText,
    secondary = PastelMint,
    onSecondary = DarkText,
    tertiary = PastelGray,
    background = DarkBackground,
    onBackground = PastelGray,
    surface = DarkText,
    onSurface = PastelGray
)

private val LightColorScheme = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = PastelBlue,
    onPrimaryContainer = DarkText,
    secondary = PastelMint,
    onSecondary = DarkText,
    tertiary = PastelGray,
    background = SoftBlueGray,
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
