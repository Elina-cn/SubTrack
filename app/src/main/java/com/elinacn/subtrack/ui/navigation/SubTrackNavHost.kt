package com.elinacn.subtrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elinacn.subtrack.ui.edit.EditSubscriptionScreen
import com.elinacn.subtrack.ui.edit.EditSubscriptionViewModel
import com.elinacn.subtrack.ui.home.HomeScreen
import com.elinacn.subtrack.ui.home.HomeViewModel
import com.elinacn.subtrack.ui.settings.SettingsScreen
import com.elinacn.subtrack.ui.statistics.StatisticsScreen
import com.elinacn.subtrack.ui.statistics.StatisticsViewModel
import com.elinacn.subtrack.ui.settings.SettingsViewModel
import com.elinacn.subtrack.ui.settings.rates.ExchangeRatesScreen
import com.elinacn.subtrack.ui.settings.rates.ExchangeRatesViewModel

/**
 * The app's only navigation graph.
 *
 * Each destination resolves its own ViewModel here rather than in the screen, so the screens stay
 * stateless and previewable without a Hilt graph. A ViewModel obtained this way is scoped to its
 * back stack entry, so leaving settings for good disposes it, while going home keeps the home one
 * alive underneath.
 */
@Composable
fun SubTrackNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destination.HOME,
        modifier = modifier
    ) {
        composable(Destination.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateToSettings = {
                    // launchSingleTop so a double tap on the icon cannot stack two copies of
                    // settings, which would then need two back presses to leave.
                    navController.navigate(Destination.SETTINGS) { launchSingleTop = true }
                },
                onNavigateToStatistics = {
                    navController.navigate(Destination.STATISTICS) { launchSingleTop = true }
                },
                onEditSubscription = { id ->
                    navController.navigate(Destination.editSubscription(id)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Destination.EDIT_SUBSCRIPTION,
            // Declared as a Long here, which is what makes the id typed by the time the ViewModel
            // reads it out of its SavedStateHandle - see Destination.
            arguments = listOf(
                navArgument(Destination.EDIT_SUBSCRIPTION_ARG) { type = NavType.LongType }
            )
        ) {
            val viewModel: EditSubscriptionViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            EditSubscriptionScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destination.STATISTICS) {
            val viewModel: StatisticsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            StatisticsScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destination.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExchangeRates = {
                    navController.navigate(Destination.EXCHANGE_RATES) { launchSingleTop = true }
                }
            )
        }

        composable(Destination.EXCHANGE_RATES) {
            val viewModel: ExchangeRatesViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            ExchangeRatesScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
