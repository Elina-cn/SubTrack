package com.elinacn.subtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elinacn.subtrack.ui.home.HomeScreen
import com.elinacn.subtrack.ui.home.HomeViewModel
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only activity. It resolves the ViewModel and hands its state to a stateless screen; every
 * composable below this point is previewable without Hilt.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubTrackTheme {
                val viewModel: HomeViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                HomeScreen(uiState = uiState, onEvent = viewModel::onEvent)
            }
        }
    }
}
