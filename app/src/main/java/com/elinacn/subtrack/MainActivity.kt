package com.elinacn.subtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.elinacn.subtrack.ui.navigation.SubTrackNavHost
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only activity. It sets up the theme and the navigation graph and nothing else; which screen
 * is showing, and which ViewModel belongs to it, is the graph's business.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubTrackTheme {
                SubTrackNavHost()
            }
        }
    }
}
