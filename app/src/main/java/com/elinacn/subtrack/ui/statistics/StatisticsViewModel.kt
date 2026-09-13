package com.elinacn.subtrack.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import com.elinacn.subtrack.domain.usecase.CurrencyConverter
import com.elinacn.subtrack.domain.usecase.SubscriptionStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Works out what the statistics screen shows.
 *
 * It reads the repositories directly and knows nothing of the home screen's category filter, so
 * the figures here always cover every subscription - the same reason the snapshot recorder lives
 * below the UI (ARCHITECTURE §19). Reading the filtered list would answer a different question
 * from the one the screen asks.
 *
 * There is no `onEvent`: nothing on this screen changes anything. See [StatisticsUiState].
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: SubscriptionRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /**
     * The single source of truth for the screen.
     *
     * WhileSubscribed(5_000) for the same reason as the home screen: a rotation tears the screen
     * down and rebuilds it well inside that window, so the database observer survives and the
     * chart does not blink.
     */
    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.observeAll(),
        settingsRepository.observeMainCurrency(),
        settingsRepository.observeRates()
    ) { subscriptions, mainCurrency, rates ->
        // Built per emission rather than held as a field: the rates are editable, and a converter
        // that outlived them would keep charting at yesterday's numbers.
        val converter = CurrencyConverter(rates)
        StatisticsUiState(
            categoryShares = SubscriptionStatistics.byCategory(subscriptions, converter, mainCurrency),
            mostExpensive = SubscriptionStatistics.mostExpensive(subscriptions, converter, mainCurrency),
            currency = mainCurrency,
            hasAnySubscriptions = subscriptions.isNotEmpty(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StatisticsUiState()
    )

    private companion object {
        /** Outlives a configuration change, expires on a real departure. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
