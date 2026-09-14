package com.elinacn.subtrack.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.domain.repository.MonthlySnapshotRepository
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import com.elinacn.subtrack.domain.usecase.CurrencyConverter
import com.elinacn.subtrack.domain.usecase.MonthlyTrend
import com.elinacn.subtrack.domain.usecase.SubscriptionStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/**
 * Works out what the statistics screen shows.
 *
 * It reads the repositories directly and knows nothing of the home screen's category filter, so
 * the figures here always cover every subscription - the same reason the snapshot recorder lives
 * below the UI (ARCHITECTURE §19). Reading the filtered list would answer a different question
 * from the one the screen asks.
 *
 * The recorded months are **read** here and nowhere written: the writing side is phase 12a's
 * `MonthlySnapshotRecorder`, which runs off the application scope and never involves a screen.
 *
 * There is no `onEvent`: nothing on this screen changes anything. See [StatisticsUiState].
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    repository: SubscriptionRepository,
    settingsRepository: SettingsRepository,
    snapshotRepository: MonthlySnapshotRepository,
    private val clock: Clock
) : ViewModel() {

    /**
     * The single source of truth for the screen.
     *
     * WhileSubscribed(5_000) for the same reason as the home screen: a rotation tears the screen
     * down and rebuilds it well inside that window, so the database observers survive and the
     * chart does not blink.
     *
     * **One loading state for the whole screen.** The recorded months arrive on a fourth Flow, and
     * `combine` waits for every source before it emits anything, so `isLoading` covers the trend
     * as well - the screen appears in one piece. A second indicator over the chart would put two
     * spinners on a page that loads once from one database. The 300 ms delay in front of the
     * indicator (phase 8a) means the usual few-dozen-millisecond read shows no spinner at all.
     */
    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.observeAll(),
        settingsRepository.observeMainCurrency(),
        settingsRepository.observeRates(),
        snapshotRepository.observeAll()
    ) { subscriptions, mainCurrency, rates, snapshots ->
        // Built per emission rather than held as a field: the rates are editable, and a converter
        // that outlived them would keep charting at yesterday's numbers.
        val converter = CurrencyConverter(rates)
        // Read per emission, never cached: the month rolls over while the process lives, and the
        // clock is injected so a test can put the run in whichever month it wants (§17).
        val series = MonthlyTrend.series(snapshots, YearMonth.now(clock), mainCurrency)
        StatisticsUiState(
            categoryShares = SubscriptionStatistics.byCategory(subscriptions, converter, mainCurrency),
            mostExpensive = SubscriptionStatistics.mostExpensive(subscriptions, converter, mainCurrency),
            trend = series.points,
            trendPeak = MonthlyTrend.peak(series.points),
            monthsInOtherCurrency = series.monthsInOtherCurrency,
            monthlyChange = MonthlyTrend.changeSince(series.points),
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
