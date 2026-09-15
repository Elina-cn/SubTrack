package com.elinacn.subtrack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The colours the whole app draws in, read once for the activity rather than per screen.
 *
 * It belongs to the activity because the theme wraps the navigation graph: a screen's ViewModel
 * would be created too late and destroyed too early to decide it.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    repository: SettingsRepository
) : ViewModel() {

    /**
     * Null until the stored preference has actually been read.
     *
     * The distinction matters at launch. "Not read yet" is not the same as "follow the system",
     * and drawing the second while waiting for the first is what makes the theme visibly change
     * under the user - see MainActivity, which holds the first frame rather than guessing.
     *
     * Eagerly, not WhileSubscribed: the read should be in flight while the activity is still
     * setting up its content, not start when composition first subscribes.
     */
    val themeState: StateFlow<ThemeState?> = combine(
        repository.observeThemeMode(),
        repository.observeDynamicColor()
    ) { mode, dynamicColor -> ThemeState(mode = mode, dynamicColor = dynamicColor) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )
}

/** The two stored preferences that together choose a colour scheme. */
data class ThemeState(
    val mode: ThemeMode = ThemeMode.Default,
    val dynamicColor: Boolean = false
)
