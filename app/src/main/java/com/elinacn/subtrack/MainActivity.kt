package com.elinacn.subtrack

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elinacn.subtrack.ui.navigation.SubTrackNavHost
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import com.elinacn.subtrack.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only activity. It sets up the theme and the navigation graph and nothing else; which screen
 * is showing, and which ViewModel belongs to it, is the graph's business.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, and that order is the library's requirement rather than a
        // preference: the call swaps the activity's theme from Theme.SubTrack.Starting to the
        // `postSplashScreenTheme` it names, and it has to do that before the window is set up.
        // Called after, the starting theme would be the theme the app keeps.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Before setContent, because this is what stops the decor view fitting the system windows
        // and the window has to know before it is first laid out. Since phase 16 the app targets
        // SDK 36 and Android 15 forces edge-to-edge anyway; calling it makes the same arrangement
        // explicit on every release, and is what finally lets the insets reach Compose. See
        // ARCHITECTURE section 16.
        //
        // The status bar style here is provisional and deliberately not `auto`. It follows the
        // window that is actually on screen for these frames, and since phase 16h that window is
        // the splash, whose background is the icon's own ground (#0D1A14) in both schemes. `dark`
        // means "the background behind me is dark", so the icons are drawn light and stay
        // readable across the hold. It was `light` until 16h, which was right while the launch
        // window came from Theme.SubTrack and was white; against the splash it would draw dark
        // icons on a near-black ground. The effect inside the composition replaces this as soon
        // as the stored theme arrives.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            // Composed with the defaults while the read is still out. Those frames are real, and
            // they are the ones the splash below keeps off the screen.
            val theme = themeState ?: ThemeState()
            SubTrackTheme(themeMode = theme.mode, dynamicColor = theme.dynamicColor) {
                SystemBarsFollowTheTheme(
                    darkTheme = isDarkTheme(theme.mode),
                    isThemeKnown = themeState != null
                )
                SubTrackNavHost()
            }
        }
        // After setContent, not before: the content view has no ViewTreeObserver of its own until
        // something is put in it, and a listener registered on the placeholder one never runs.
        // The library registers its own OnPreDrawListener on exactly that view, so it inherits
        // the constraint phase 14b measured - hence here rather than next to installSplashScreen.
        keepSplashScreenUntilThemeIsRead(splashScreen)
    }

    /**
     * Keeps the splash on screen until the stored theme is in hand.
     *
     * Same job the phase 14b pre-draw gate did, and for the same measured reason: with dark stored
     * and the system on light, the home screen was drawn in full in the light scheme - background
     * #D3E2D8, white app bar - and only then turned dark. One complete frame in the wrong theme,
     * which reads as the app changing its mind in front of the user.
     *
     * What changed in 16h is only *what stands in* during the wait. The gate used to hold the
     * launch window, whose background came from Theme.SubTrack and was white; phase 16g measured
     * the result as a blank #FAFAFA frame, 173-212 ms of it on API 34, sitting between the system
     * splash and the app. Now the splash itself is what is held, so the wait shows the mark on the
     * icon's own ground instead of nothing. **There is exactly one gate** - the old listener is
     * gone, and two of them in series would each wait for the same read.
     *
     * The deadline is what keeps a held frame from becoming a blank app. If the preferences never
     * arrive - a failure the repository's IOException fallback does not cover - the splash gives
     * up anyway and the app comes up in the default theme. A wrong theme is recoverable; a window
     * that never draws is not: the system reports it as "does not have a focused window" and kills
     * the app for not responding. Phase 16g measured the real wait at 173-212 ms, so a second is
     * far out of reach in normal use - this is the failure path, not a budget.
     *
     * The deadline lives here rather than in the ViewModel on purpose. "How long is this window
     * allowed to stay blank" is a fact about this window, not about the preference; MainViewModel
     * says only whether the value has arrived, and a repository that reported its own timeout
     * would be answering a question about the UI.
     *
     * The wake-up is **posted** rather than only tested inside the condition, and that part is
     * load-bearing. Cancelling a draw does not schedule another traversal, and the condition is
     * only read when one happens - so a deadline tested inside it would only be read if something
     * else asked to draw. On the ordinary path that is fine, because the emission being waited for
     * is itself what triggers the next traversal. On the path where nothing ever arrives there is
     * no next traversal, and a deadline that never gets read is not a deadline. A delayed message
     * on the main looper runs either way.
     */
    private fun keepSplashScreenUntilThemeIsRead(splashScreen: SplashScreen) {
        val deadline = SystemClock.uptimeMillis() + THEME_READ_TIMEOUT_MS
        splashScreen.setKeepOnScreenCondition {
            viewModel.themeState.value == null && SystemClock.uptimeMillis() < deadline
        }
        val content = findViewById<View>(android.R.id.content)
        // Asks for the traversal that the cancelled draws never scheduled.
        content.postDelayed({ content.invalidate() }, THEME_READ_TIMEOUT_MS)
    }

    private companion object {
        /**
         * The longest the splash is held for a preference read.
         *
         * Generous next to a read phase 16g measured at 173-212 ms, and short next to the cold
         * start the user is already waiting through.
         */
        const val THEME_READ_TIMEOUT_MS = 1_000L
    }
}

/**
 * Keeps the status and navigation bar icons readable against whatever the app is drawing.
 *
 * Edge-to-edge puts the app's own background behind both bars, so the system no longer knows what
 * is under its icons - it has to be told. [darkTheme] is the same answer the colour scheme was
 * built from, which is the point: when the user forces dark, the bars follow the *preference*, not
 * the device's night setting. Phase 16-0 measured what happens without this - in the light theme
 * the top 1080x128 band held zero non-white pixels, because the icons were still being drawn white
 * over a white surface.
 *
 * Nothing is applied until [isThemeKnown], and that matters: the first composition runs against the
 * defaults while the preference is still being read, and applying those defaults would set the bars
 * from a value the app is about to discard. The launch window's own style, set in `onCreate`,
 * covers that gap - the same gap the first-frame gate holds the app's own drawing across, so the
 * two are answering for the same frames rather than fighting over them.
 *
 * The two bars get different styles, and that is measured rather than tidy. Below API 29 androidx
 * fills a bar with the scrim it was handed instead of leaving it to the system, so a scrim is not
 * free - on API 24 an opaque status bar in `background` drew a visible seam across the top of the
 * app bar. The status bar does not need one: its icons have been able to go dark since API 23, so
 * transparent is safe on every release this app supports. The navigation bar does need one, because
 * below API 26 its icons are always white and would vanish over a light background; `scrim` is
 * black in both schemes, which is exactly the bar those releases shipped with. Between API 26 and
 * 28 the icons can follow the theme, so `background` lets the bar continue the app. From API 29
 * both are transparent and the system enforces its own contrast.
 */
@Composable
private fun SystemBarsFollowTheTheme(darkTheme: Boolean, isThemeKnown: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val lightScrim = MaterialTheme.colorScheme.background.toArgb()
    val darkScrim = MaterialTheme.colorScheme.scrim.toArgb()

    DisposableEffect(activity, darkTheme, isThemeKnown, lightScrim, darkScrim) {
        if (isThemeKnown) {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                ) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(lightScrim, darkScrim) { darkTheme }
            )
        }
        onDispose { }
    }
}
