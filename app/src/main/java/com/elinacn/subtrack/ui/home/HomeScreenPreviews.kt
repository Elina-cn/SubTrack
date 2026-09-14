package com.elinacn.subtrack.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.ui.common.UiText
import com.elinacn.subtrack.ui.theme.SubTrackTheme

private fun previewSubscription(
    id: Long,
    name: String,
    cents: Long,
    currency: Currency = Currency.TRY
) = Subscription(
    id = id,
    name = name,
    price = Money(cents),
    currency = currency,
    billingPeriod = BillingPeriod.MONTHLY,
    nextPaymentDate = null,
    category = SubscriptionCategory.OTHER,
    iconKey = null,
    createdAt = 0L
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenEmptyPreview() {
    SubTrackTheme {
        HomeScreen(
            uiState = HomeUiState(isLoading = false),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToStatistics = {},
            onEditSubscription = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenLoadingPreview() {
    SubTrackTheme {
        HomeScreen(
            uiState = HomeUiState(isLoading = true),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToStatistics = {},
            onEditSubscription = {}
        )
    }
}

/** The snackbar is driven by a LaunchedEffect, so this one only renders in interactive preview. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenErrorPreview() {
    SubTrackTheme {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                errorMessage = UiText.Raw("Abonelik kaydedilemedi")
            ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToStatistics = {},
            onEditSubscription = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPopulatedPreview() {
    val subscriptions = listOf(
        previewSubscription(1, "Netflix", 15999),
        previewSubscription(2, "Spotify", 5990)
    )
    SubTrackTheme {
        HomeScreen(
            uiState = HomeUiState(
                subscriptions = subscriptions,
                total = Money(21989),
                isLoading = false
            ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToStatistics = {},
            onEditSubscription = {}
        )
    }
}

/** A mixed list, where the total needs the line saying what it was converted from. */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenMixedCurrencyPreview() {
    val subscriptions = listOf(
        previewSubscription(1, "Netflix", 15999),
        previewSubscription(2, "Spotify", 1099, Currency.USD),
        previewSubscription(3, "Adobe", 2499, Currency.EUR)
    )
    SubTrackTheme {
        HomeScreen(
            uiState = HomeUiState(
                subscriptions = subscriptions,
                total = Money(178545),
                isTotalConverted = true,
                isLoading = false
            ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToStatistics = {},
            onEditSubscription = {}
        )
    }
}
