package com.elinacn.subtrack.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.elinacn.subtrack.R
import com.elinacn.subtrack.ui.common.DelayedLoadingIndicator
import com.elinacn.subtrack.ui.common.rememberMoneyFormatter
import com.elinacn.subtrack.ui.home.components.AddSubscriptionSheet
import com.elinacn.subtrack.ui.home.components.DashboardCard
import com.elinacn.subtrack.ui.home.components.SubscriptionCard
import com.elinacn.subtrack.ui.home.components.SwipeToDeleteRow
import com.elinacn.subtrack.ui.theme.Dimens

/**
 * The home screen. Stateless with respect to data: it renders [uiState] and reports back through
 * [onEvent], holding nothing but whether the add sheet is open.
 *
 * [onNavigateToSettings] arrives as a lambda rather than a NavController, so the screen knows only
 * that a settings screen exists somewhere, not how to reach it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moneyFormatter = rememberMoneyFormatter()
    val snackbarHostState = remember { SnackbarHostState() }
    // skipPartiallyExpanded: half-open, the sheet cut the save button off below a 640dp-tall
    // screen and nothing on screen said it was there to be dragged up. Opening expanded shows the
    // whole form; the scroll inside it is what covers the case where the form is taller than the
    // screen anyway.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Resolved here rather than inside the effects below, which are not composable scopes.
    val deletedMessage = stringResource(id = R.string.subscription_deleted)
    val undoLabel = stringResource(id = R.string.undo)
    val errorText = uiState.errorMessage?.asString()
    val conversionNote = if (uiState.isTotalConverted) {
        stringResource(id = R.string.total_converted_note, uiState.baseCurrency.name)
    } else {
        null
    }

    // Offer the undo for as long as the snackbar is up; whichever way it ends, tell the ViewModel.
    //
    // duration is passed explicitly and must stay that way: showSnackbar defaults to Short only
    // when there is no action label, and to Indefinite when there is one. Leaving it out here left
    // the bar on screen forever, because its timer never started.
    //
    // A rotation re-runs this effect against a fresh host and shows the bar again, restarting the
    // timer. That is deliberate. The undo window is a promise to the user, and turning the phone is
    // not a decision to give it up; erring towards more time to reverse a deletion is the safe way
    // round. The row is already gone from the database either way - pendingUndo only holds what it
    // would take to put it back.
    LaunchedEffect(uiState.pendingUndo) {
        if (uiState.pendingUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        onEvent(
            if (result == SnackbarResult.ActionPerformed) HomeEvent.UndoDelete
            else HomeEvent.DismissUndo
        )
    }

    LaunchedEffect(errorText) {
        if (errorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(message = errorText, duration = SnackbarDuration.Short)
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
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(id = R.string.settings_title)
                        )
                    }
                }
            )
        },
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
                DashboardCard(
                    totalAmount = moneyFormatter.format(uiState.monthlyTotal, uiState.baseCurrency),
                    conversionNote = conversionNote
                )

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

            item {
                DelayedLoadingIndicator(isLoading = uiState.isLoading)
            }

            items(
                items = uiState.subscriptions,
                key = { it.id } // Room's AUTOINCREMENT never reuses an id, so this stays unique
            ) { subscription ->
                // Its own currency, not a converted figure: the user entered 12,99 USD and that
                // is what the row has to keep saying.
                val price = moneyFormatter.format(subscription.price, subscription.currency)
                SwipeToDeleteRow(
                    onDelete = { onEvent(HomeEvent.Delete(subscription.id)) },
                    contentDescription = stringResource(
                        id = R.string.subscription_row_description,
                        subscription.name,
                        price
                    )
                ) {
                    SubscriptionCard(name = subscription.name, price = price)
                }
            }
        }
    }

    if (uiState.isAddSheetOpen || sheetState.isVisible) {
        AddSubscriptionSheet(
            sheetState = sheetState,
            nameError = uiState.nameError,
            priceError = uiState.priceError,
            onSave = { name, rawPrice, currency ->
                onEvent(HomeEvent.Save(name, rawPrice, currency))
            },
            onNameEdited = { onEvent(HomeEvent.ClearNameError) },
            onPriceEdited = { onEvent(HomeEvent.ClearPriceError) },
            onDismiss = { onEvent(HomeEvent.DismissAddSheet) }
        )
    }
}
