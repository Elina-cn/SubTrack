package com.elinacn.subtrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Holds the settings screen's state and turns its events into preference writes. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    /** Everything that is not stored; only the failed-write message so far. */
    private val screenState = MutableStateFlow<UiText?>(null)

    /**
     * The stored preference is the single source of truth: a tap writes and the screen updates
     * because the store emits again, not because the ViewModel guessed. A write that fails
     * therefore leaves the chips where they were, which is the truth.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeMainCurrency(),
        screenState
    ) { currency, errorMessage ->
        SettingsUiState(mainCurrency = currency, errorMessage = errorMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState()
    )

    /** Single entry point for everything the screen can ask for. */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectMainCurrency -> setMainCurrency(event.currency)

            SettingsEvent.DismissError -> screenState.update { null }
        }
    }

    private fun setMainCurrency(currency: Currency) {
        viewModelScope.launch {
            try {
                repository.setMainCurrency(currency)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Never swallowed: a preference that did not stick has to say so.
                screenState.update { UiText.Resource(R.string.error_setting_save_failed) }
            }
        }
    }

    private companion object {
        /** Outlives a configuration change, expires on a real departure. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
