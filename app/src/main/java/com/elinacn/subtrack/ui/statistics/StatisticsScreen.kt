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
import com.elinacn.subtrack.ui.common.labelRes
import com.elinacn.subtrack.ui.common.rememberMoneyFormatter
import com.elinacn.subtrack.ui.statistics.components.CategoryBarRow
import com.elinacn.subtrack.ui.theme.Dimens
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
    val moneyFormatter = rememberMoneyFormatter()

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

            if (uiState.categoryShares.isNotEmpty()) {
                SectionTitle(text = stringResource(id = R.string.statistics_by_category))
                uiState.categoryShares.forEach { share ->
                    CategoryBarRow(
                        label = stringResource(id = share.category.labelRes()),
                        amount = moneyFormatter.format(share.total, uiState.currency),
                        percent = share.percent
                    )
                }
            }
        }
    }
}

/** The heading over one section, indented and spaced like the home screen's own. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(
            start = Dimens.SectionTitleStart,
            top = Dimens.SectionTitleTop,
            bottom = Dimens.SectionTitleBottom
        ),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Preview(showBackground = true)
@Composable
private fun StatisticsScreenEmptyPreview() {
    SubTrackTheme {
        StatisticsScreen(uiState = StatisticsUiState(isLoading = false), onNavigateBack = {})
    }
}
