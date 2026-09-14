package com.elinacn.subtrack.ui.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.common.DelayedLoadingIndicator
import com.elinacn.subtrack.ui.common.EmptyStatistics
import com.elinacn.subtrack.ui.common.labelRes
import com.elinacn.subtrack.ui.common.rememberMoneyFormatter
import com.elinacn.subtrack.ui.statistics.components.CategoryBarRow
import com.elinacn.subtrack.ui.statistics.components.ExpensiveSubscriptionRow
import com.elinacn.subtrack.ui.statistics.components.MonthlyTrendChart
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
            if (!uiState.isLoading && uiState.hasNothingToShow) {
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

            if (uiState.mostExpensive.isNotEmpty()) {
                SectionTitle(text = stringResource(id = R.string.statistics_most_expensive))
                uiState.mostExpensive.forEach { cost ->
                    ExpensiveSubscriptionRow(
                        name = cost.subscription.name,
                        billingPeriod = stringResource(
                            id = cost.subscription.billingPeriod.labelRes()
                        ),
                        monthlyCost = moneyFormatter.format(cost.monthlyCost, uiState.currency)
                    )
                }
            }

            // The trend stands down only when the whole screen has nothing to say - otherwise it
            // is shown even with one month behind it, because "collecting" is the honest answer
            // and the usual one for a new user.
            if (!uiState.isLoading && !uiState.hasNothingToShow) {
                SectionTitle(text = stringResource(id = R.string.statistics_trend))
                if (uiState.canDrawTrend) {
                    MonthlyTrendChart(
                        points = uiState.trend,
                        peak = uiState.trendPeak,
                        currency = uiState.currency
                    )
                } else {
                    TrendNotice(text = stringResource(id = R.string.statistics_trend_collecting))
                }
                if (uiState.monthsInOtherCurrency > 0) {
                    TrendNotice(
                        text = pluralStringResource(
                            id = R.plurals.statistics_trend_other_currency,
                            count = uiState.monthsInOtherCurrency,
                            uiState.monthsInOtherCurrency
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SpacerXLarge))
        }
    }
}

/**
 * A paragraph under the trend: either why there is no chart yet, or what the chart is not showing.
 *
 * Plain text rather than an [EmptyStatistics]-style block with a glyph. This is a note inside a
 * section, not an empty screen, and a second illustrated empty state under the breakdown would
 * read as a second page.
 */
@Composable
private fun TrendNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(
            horizontal = Dimens.ScreenPadding,
            vertical = Dimens.RowSpacing
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
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
