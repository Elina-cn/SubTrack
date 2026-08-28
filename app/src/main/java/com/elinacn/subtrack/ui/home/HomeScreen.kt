package com.elinacn.subtrack.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.ui.home.components.AddSubscriptionSheet
import com.elinacn.subtrack.ui.home.components.DashboardCard
import com.elinacn.subtrack.ui.home.components.SubscriptionCard
import com.elinacn.subtrack.ui.home.components.SwipeToDeleteRow
import com.elinacn.subtrack.ui.theme.Dimens
import com.elinacn.subtrack.ui.theme.SubTrackTheme
import java.math.BigDecimal
import java.util.Locale

/**
 * The home screen. Stateless with respect to data: it renders [uiState] and reports back through
 * [onEvent], holding nothing but whether the add sheet is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val priceFormat = stringResource(id = R.string.price_format)
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    // Resolved here rather than inside the effects below, which are not composable scopes.
    val deletedMessage = stringResource(id = R.string.subscription_deleted)
    val undoLabel = stringResource(id = R.string.undo)
    val errorText = uiState.errorMessage?.asString()

    // Offer the undo for as long as the snackbar is up; whichever way it ends, tell the ViewModel.
    LaunchedEffect(uiState.pendingUndo) {
        if (uiState.pendingUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel
        )
        onEvent(
            if (result == SnackbarResult.ActionPerformed) HomeEvent.UndoDelete
            else HomeEvent.DismissUndo
        )
    }

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(errorText)
        onEvent(HomeEvent.DismissError)
    }

    // Keep the sheet composed while it animates away, otherwise closing it is an instant cut.
    LaunchedEffect(uiState.isAddSheetOpen) {
        if (!uiState.isAddSheetOpen && sheetState.isVisible) sheetState.hide()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(HomeEvent.OpenAddSheet) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(Dimens.FabCorner)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_subscription)
                )
            }
        }
    ) { paddingValues ->
        // LazyColumn rather than Column so long lists only compose what is on screen.
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = Dimens.ListBottomSpacing) // room for the FAB
        ) {
            item {
                DashboardCard(totalAmount = uiState.monthlyTotal.format(priceFormat))

                Text(
                    text = stringResource(id = R.string.my_subscriptions),
                    modifier = Modifier.padding(
                        start = Dimens.SectionTitleStart,
                        top = Dimens.SectionTitleTop,
                        bottom = Dimens.SectionTitleBottom
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(
                items = uiState.subscriptions,
                key = { it.id } // Room's AUTOINCREMENT never reuses an id, so this stays unique
            ) { subscription ->
                SwipeToDeleteRow(onDelete = { onEvent(HomeEvent.Delete(subscription.id)) }) {
                    SubscriptionCard(
                        name = subscription.name,
                        price = subscription.price.format(priceFormat)
                    )
                }
            }
        }
    }

    if (uiState.isAddSheetOpen || sheetState.isVisible) {
        AddSubscriptionSheet(
            sheetState = sheetState,
            nameError = uiState.nameError,
            priceError = uiState.priceError,
            onSave = { name, rawPrice -> onEvent(HomeEvent.Save(name, rawPrice)) },
            onNameEdited = { onEvent(HomeEvent.ClearNameError) },
            onPriceEdited = { onEvent(HomeEvent.ClearPriceError) },
            onDismiss = { onEvent(HomeEvent.DismissAddSheet) }
        )
    }
}

/**
 * Renders an amount with the localized pattern. Display only - the value itself stays in whole
 * minor units, and BigDecimal keeps the conversion exact.
 */
private fun Money.format(pattern: String): String =
    String.format(Locale.US, pattern, BigDecimal(cents).movePointLeft(2))

private fun previewSubscription(id: Long, name: String, cents: Long) = Subscription(
    id = id,
    name = name,
    price = Money(cents),
    currencyCode = "TRY",
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
        HomeScreen(uiState = HomeUiState(isLoading = false), onEvent = {})
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
                monthlyTotal = Money(21989),
                isLoading = false
            ),
            onEvent = {}
        )
    }
}
