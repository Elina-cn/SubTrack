package com.elinacn.subtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PastelBlue,
    secondary = PastelMint,
    tertiary = PastelGray,
    background = DarkText,
    surface = DarkText
)
private val LightColorScheme = lightColorScheme(
    primary = PastelBlue,
    secondary = PastelMint,
    tertiary = PastelGray,
    background = PastelGray,
    surface = Color.White
)

@Composable
fun SubTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}