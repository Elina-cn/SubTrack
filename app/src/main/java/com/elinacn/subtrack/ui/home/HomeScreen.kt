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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.ui.home.components.AddSubscriptionSheet
import com.elinacn.subtrack.ui.home.components.DashboardCard
import com.elinacn.subtrack.ui.home.components.SubscriptionCard
import com.elinacn.subtrack.ui.home.components.SwipeToDeleteRow
import com.elinacn.subtrack.ui.theme.Dimens
import java.math.BigDecimal
import java.util.Locale

/**
 * The home screen. Stateless with respect to data: it renders [uiState] and reports back through
 * [onEvent], holding nothing but whether the add sheet is open.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddSheet by remember { mutableStateOf(false) }

    val priceFormat = stringResource(id = R.string.price_format)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
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

    if (showAddSheet) {
        AddSubscriptionSheet(
            onSave = { name, rawPrice -> onEvent(HomeEvent.Save(name, rawPrice)) },
            onDismiss = { showAddSheet = false }
        )
    }
}

/**
 * Renders an amount with the localized pattern. Display only - the value itself stays in whole
 * minor units, and BigDecimal keeps the conversion exact.
 */
private fun Money.format(pattern: String): String =
    String.format(Locale.US, pattern, BigDecimal(cents).movePointLeft(2))
