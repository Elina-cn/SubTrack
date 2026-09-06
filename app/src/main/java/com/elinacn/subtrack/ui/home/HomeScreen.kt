package com.elinacn.subtrack.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.usecase.PaymentCountdown
import com.elinacn.subtrack.ui.common.DelayedLoadingIndicator
import com.elinacn.subtrack.ui.common.CategoryFilterBar
import com.elinacn.subtrack.ui.common.EmptyCategory
import com.elinacn.subtrack.ui.common.EmptySubscriptions
import com.elinacn.subtrack.ui.common.labelRes
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

    // Registered unconditionally. A launcher created inside an if would already be gone by the
    // time the system handed the answer back.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Whatever the answer, the row in settings is where it can be changed from now on. */ }

    // Exactly the condition that keeps the sheet in the tree, read the other way round: the sheet
    // is gone only when the state says closed AND the hide animation has finished. Asking before
    // that puts the system dialog on top of a sheet still sliding away and clips both.
    val isAddSheetGone = !uiState.isAddSheetOpen && !sheetState.isVisible

    LaunchedEffect(uiState.shouldRequestNotificationPermission, isAddSheetGone) {
        if (!uiState.shouldRequestNotificationPermission || !isAddSheetGone) return@LaunchedEffect
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Cleared straight away, so a rotation while the dialog is up cannot ask twice.
        onEvent(HomeEvent.NotificationRequestHandled)
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

                CategoryFilterBar(
                    selected = uiState.categoryFilter,
                    onSelect = { onEvent(HomeEvent.SelectCategoryFilter(it)) }
                )
            }

            item {
                DelayedLoadingIndicator(isLoading = uiState.isLoading)
            }

            // Only once the list is known to be empty, never while it is still unknown. isLoading
            // and the list arrive in the same emission, so there is no frame where the screen has
            // one without the other and flashes "nothing here" at a user who has ten rows.
            //
            // Two different emptinesses, and they must not be confused: nothing stored at all, or
            // nothing that survived the filter. hasAnySubscriptions is what tells them apart.
            if (!uiState.isLoading && uiState.subscriptions.isEmpty()) {
                item {
                    if (uiState.hasAnySubscriptions) EmptyCategory() else EmptySubscriptions()
                }
            }

            items(
                items = uiState.subscriptions,
                key = { it.id } // Room's AUTOINCREMENT never reuses an id, so this stays unique
            ) { subscription ->
                // Its own currency, not a converted figure: the user entered 12,99 USD and that
                // is what the row has to keep saying.
                val price = moneyFormatter.format(subscription.price, subscription.currency)
                val countdown = uiState.countdowns[subscription.id]
                // The countdown has to reach the spoken description too: the row is one focus stop
                // with its own label, so anything left out of the label is simply not announced.
                val countdownText = countdown?.asString()
                // Left out when it is OTHER, exactly as the card leaves it out: a screen reader
                // should hear the row the sighted user sees, not a default nobody chose.
                val categoryText = subscription.category
                    .takeIf { it != SubscriptionCategory.OTHER }
                    ?.let { stringResource(id = it.labelRes()) }
                // Built in two steps rather than as four separate resources: name/price and the
                // optional countdown make the sentence, then the category is appended when there
                // is one. Four combinations would otherwise need four strings to translate.
                val rowSentence = if (countdownText == null) {
                    stringResource(
                        id = R.string.subscription_row_description,
                        subscription.name,
                        price
                    )
                } else {
                    stringResource(
                        id = R.string.subscription_row_description_dated,
                        subscription.name,
                        price,
                        countdownText
                    )
                }
                SwipeToDeleteRow(
                    onDelete = { onEvent(HomeEvent.Delete(subscription.id)) },
                    contentDescription = if (categoryText == null) {
                        rowSentence
                    } else {
                        stringResource(
                            id = R.string.subscription_row_description_with_category,
                            rowSentence,
                            categoryText
                        )
                    }
                ) {
                    SubscriptionCard(
                        name = subscription.name,
                        price = price,
                        countdown = countdown,
                        category = subscription.category
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
            dateError = uiState.dateError,
            onSave = { name, rawPrice, currency, nextPaymentDate, category, billingPeriod ->
                onEvent(
                    HomeEvent.Save(
                        name,
                        rawPrice,
                        currency,
                        nextPaymentDate,
                        category,
                        billingPeriod
                    )
                )
            },
            onNameEdited = { onEvent(HomeEvent.ClearNameError) },
            onPriceEdited = { onEvent(HomeEvent.ClearPriceError) },
            onDateEdited = { onEvent(HomeEvent.ClearDateError) },
            onDismiss = { onEvent(HomeEvent.DismissAddSheet) }
        )
    }
}

/**
 * The countdown as one phrase for a screen reader, resolved here because the row's whole label is
 * built in one place.
 */
@Composable
private fun PaymentCountdown.asString(): String = when (this) {
    is PaymentCountdown.Upcoming ->
        pluralStringResource(R.plurals.days_until_payment, days.toInt(), days)
    PaymentCountdown.DueToday -> stringResource(id = R.string.due_today)
    is PaymentCountdown.Overdue ->
        pluralStringResource(R.plurals.days_overdue, days.toInt(), days)
}
