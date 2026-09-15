package com.elinacn.subtrack

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elinacn.subtrack.ui.navigation.SubTrackNavHost
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only activity. It sets up the theme and the navigation graph and nothing else; which screen
 * is showing, and which ViewModel belongs to it, is the graph's business.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            // Composed with the defaults while the read is still out. Those frames are real, and
            // they are the ones the gate above keeps off the screen.
            val theme = themeState ?: ThemeState()
            SubTrackTheme(themeMode = theme.mode, dynamicColor = theme.dynamicColor) {
                SubTrackNavHost()
            }
        }
        // After setContent, not before: the content view has no ViewTreeObserver of its own until
        // something is put in it, and a listener registered on the placeholder one never runs.
        holdFirstFrameUntilThemeIsRead()
    }

    /**
     * Keeps the window from being drawn until the stored theme is in hand.
     *
     * Measured in phase 14b before this existed: with dark stored and the system on light, the
     * home screen was drawn in full in the light scheme - background #D3E2D8, white app bar - and
     * only then turned dark. One complete frame in the wrong theme, which reads as the app
     * changing its mind in front of the user.
     *
     * Holding the first frame is the fix rather than guessing a theme to draw meanwhile: any guess
     * is wrong for somebody, and this way the window background stands in for the few frames the
     * read takes, exactly as it already does during a cold start.
     *
     * The deadline is what keeps a held frame from becoming a blank app. If the preferences never
     * arrive - a failure the repository's IOException fallback does not cover - the gate opens
     * anyway and the app comes up in the default theme, which is what it did before this method
     * existed. A wrong theme is recoverable; a window that never draws is not.
     */
    private fun holdFirstFrameUntilThemeIsRead() {
        val content = findViewById<View>(android.R.id.content)
        val deadline = SystemClock.uptimeMillis() + THEME_READ_TIMEOUT_MS
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = viewModel.themeState.value != null ||
                        SystemClock.uptimeMillis() >= deadline
                    if (!ready) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
        )
    }

    private companion object {
        /**
         * The longest the window is held for a preference read.
         *
         * Generous next to a read that finishes in a handful of frames, and short next to the
         * cold start the user is already waiting through.
         */
        const val THEME_READ_TIMEOUT_MS = 1_000L
    }
}
