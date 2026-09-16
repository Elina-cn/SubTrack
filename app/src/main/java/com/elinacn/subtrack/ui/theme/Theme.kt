package com.elinacn.subtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.elinacn.subtrack.domain.model.ThemeMode

// Every role is spelled out, including the ones this app's own code never names. That is the point
// of phase 14a: a role left undefined does not go unused, it goes to the Material baseline, and the
// baseline is purple. Two visible bugs came from exactly that - the Snackbar's "Undo" was drawing
// in the baseline `inversePrimary`, and unselected chips drew their border and label in the
// baseline `outline` and `onSurfaceVariant`. The components asking for those roles are Material's,
// not ours, so "we do not use it" was never the same as "nothing uses it".
//
// The two schemes swap the jobs of the two hues, and the reason is measured (see Color.kt):
// emerald is 8.02:1 on white and gold is 2.42:1, so in the light scheme emerald is the ink and
// gold only fills; in the dark scheme gold is the ink at 5.66:1 on the card and the emerald family
// becomes the surfaces.
//
// Both schemes are `by lazy` rather than plain vals so that nothing is constructed while this
// file's class is being loaded. A static initializer that throws poisons its class for the rest of
// the process: every later access reports "Could not initialize class ThemeKt" and the original
// cause is gone. Compose previews were failing exactly that way. Deferring the work keeps class
// loading trivial and lets any real error surface with its own message. Same objects, still built
// once.
private val DarkColorScheme by lazy {
    darkColorScheme(
        // Gold is the ink here, and the emerald family holds the surfaces.
        primary = GoldBright,
        onPrimary = EmeraldInk,
        primaryContainer = EmeraldFill,
        onPrimaryContainer = EmeraldWhisper,
        // The light scheme's accent, because an inverse surface in the dark scheme is light.
        inversePrimary = EmeraldDeep,

        secondary = EmeraldTint,
        onSecondary = EmeraldInk,
        secondaryContainer = EmeraldFillHigh,
        onSecondaryContainer = EmeraldMist,

        tertiary = GoldLight,
        onTertiary = EmeraldInk,
        tertiaryContainer = GoldDeep,
        onTertiaryContainer = GoldPale,

        background = EmeraldNight,
        onBackground = EmeraldSnow,
        surface = EmeraldCard,
        onSurface = EmeraldSnow,
        surfaceVariant = EmeraldRaised,
        onSurfaceVariant = EmeraldFrost,
        surfaceTint = GoldBright,

        inverseSurface = EmeraldSnow,
        inverseOnSurface = EmeraldNight,

        error = CrimsonSoft,
        onError = CrimsonNight,
        errorContainer = CrimsonDeep,
        onErrorContainer = CrimsonPale,

        outline = EmeraldEdgeLight,
        outlineVariant = EmeraldLine,
        scrim = Color.Black,

        surfaceDim = EmeraldNight,
        surfaceBright = EmeraldRaised,
        surfaceContainerLowest = EmeraldNightLow,
        surfaceContainerLow = EmeraldCardLow,
        surfaceContainer = EmeraldCardBase,
        surfaceContainerHigh = EmeraldCardHigh,
        surfaceContainerHighest = EmeraldRaised
    )
}

private val LightColorScheme by lazy {
    lightColorScheme(
        // Emerald is the ink here, and gold is only ever a fill - see `tertiary`.
        primary = EmeraldDeep,
        onPrimary = Color.White,
        primaryContainer = EmeraldPale,
        onPrimaryContainer = EmeraldInk,
        // The dark scheme's accent, because an inverse surface in the light scheme is dark: this
        // is the Snackbar's action, gold on deep emerald at 5.66:1.
        inversePrimary = GoldBright,

        secondary = EmeraldMid,
        onSecondary = Color.White,
        secondaryContainer = EmeraldMist,
        onSecondaryContainer = EmeraldInk,

        // Gold's one job in this scheme. 2.42:1 on white means it can fill a shape and never
        // carry one, so `onTertiary` is the ink that goes on top of it, not the other way round.
        tertiary = Gold,
        onTertiary = EmeraldInk,
        tertiaryContainer = GoldPale,
        onTertiaryContainer = GoldInk,

        background = EmeraldBackdrop,
        onBackground = EmeraldInk,
        surface = Color.White,
        onSurface = EmeraldInk,
        surfaceVariant = EmeraldHaze,
        onSurfaceVariant = EmeraldMuted,
        surfaceTint = EmeraldDeep,

        inverseSurface = EmeraldCard,
        inverseOnSurface = EmeraldSnow,

        error = Crimson,
        onError = Color.White,
        errorContainer = CrimsonPale,
        onErrorContainer = CrimsonInk,

        outline = EmeraldEdge,
        outlineVariant = EmeraldSoft,
        scrim = Color.Black,

        surfaceDim = EmeraldDim,
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = EmeraldContainerLow,
        surfaceContainer = EmeraldContainer,
        surfaceContainerHigh = EmeraldContainerHigh,
        surfaceContainerHighest = EmeraldContainerHighest
    )
}

/**
 * Whether [themeMode] means a dark scheme on this device right now.
 *
 * Public because the colour scheme is not the only thing that has to answer this question: since
 * phase 16a the activity also tells the system what colour to draw the status and navigation bar
 * icons in, and that answer has to be the same one. A forced dark theme has to darken the bars too,
 * whatever the device's own setting says - which is exactly what would drift if the `when` below
 * were written out twice.
 */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    // Read only in this branch, so a forced theme does not recompose when the system flips.
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Wraps [content] in the app's colours.
 *
 * [themeMode] decides light or dark and [dynamicColor] decides whose hues those are; the two are
 * independent, so forcing dark still works while the wallpaper is supplying the palette.
 *
 * The defaults are what a preview gets: the app's own palette, following the system. That is also
 * what an untouched install gets, which is the point - the emerald and gold identity and the 37
 * contrast ratios measured against it in phase 14a are the product, and wallpaper colours replace
 * every one of them. See ARCHITECTURE section 23.
 */
@Composable
fun SubTrackTheme(
    themeMode: ThemeMode = ThemeMode.Default,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = isDarkTheme(themeMode)
    val context = LocalContext.current
    val colorScheme = when {
        // Material You arrived in API 31. Below it there is nothing to read the wallpaper with,
        // so the preference simply has no effect and our own palette applies - the expected
        // behaviour on an older device, not a failure. The settings row says so rather than
        // leaving the user to wonder; see SettingsScreen.
        dynamicColor && DynamicColorSupport.isAvailableOnThisBuild() ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
