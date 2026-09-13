package com.elinacn.subtrack.ui.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.common.DelayedLoadingIndicator
import com.elinacn.subtrack.ui.common.EmptyStatistics
import com.elinacn.subtrack.ui.theme.SubTrackTheme

/**
 * The statistics screen: where the money goes, and what the dearest subscriptions are.
 *
 * Stateless with respect to data - it renders [uiState] and nothing else. [onNavigateBack] arrives
 * as a lambda rather than a NavController, so the screen has no opinion about where it sits in the
 * graph and previews without one (ARCHITECTURE §13).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            DelayedLoadingIndicator(isLoading = uiState.isLoading)
            if (!uiState.isLoading && !uiState.hasAnySubscriptions) {
                EmptyStatistics()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatisticsScreenEmptyPreview() {
    SubTrackTheme {
        StatisticsScreen(uiState = StatisticsUiState(isLoading = false), onNavigateBack = {})
    }
}
