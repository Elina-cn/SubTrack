package com.elinacn.subtrack.ui.settings

import com.elinacn.subtrack.R
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.fake.FakeDynamicColorSupport
import com.elinacn.subtrack.fake.FakeReminderNotificationStatus
import com.elinacn.subtrack.fake.FakeReminderStateRepository
import com.elinacn.subtrack.fake.FakeSettingsRepository
import com.elinacn.subtrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The theme preferences through the screen's public surface: send an event, read the state.
 *
 * Nothing here reaches past `onEvent`, per ARCHITECTURE section 11 - what is being tested is that
 * a tap ends up stored and that the stored value is what the screen draws, not which private
 * method carried it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelThemeTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var dynamicColorSupport: FakeDynamicColorSupport
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        dynamicColorSupport = FakeDynamicColorSupport()
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_nothingStored_followsTheSystemWithTheAppsOwnPalette() = runTest {
        collectState()

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
        assertFalse(viewModel.uiState.value.isDynamicColorEnabled)
    }

    @Test
    fun uiState_storedThemeMode_isWhatTheScreenShows() = runTest {
        repository = FakeSettingsRepository(initialThemeMode = ThemeMode.DARK)
        viewModel = buildViewModel()
        collectState()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun uiState_storedDynamicColor_isWhatTheScreenShows() = runTest {
        repository = FakeSettingsRepository(initialDynamicColor = true)
        viewModel = buildViewModel()
        collectState()

        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)
    }

    @Test
    fun selectThemeMode_writesTheChoiceToTheRepository() = runTest {
        collectState()

        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.LIGHT))
        advanceUntilIdle()

        assertEquals(listOf(ThemeMode.LIGHT), repository.themeModeWrites)
    }

    @Test
    fun selectThemeMode_afterWriting_theStoredValueDrivesTheState() = runTest {
        collectState()

        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        // The state followed the store, not an optimistic local copy.
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun selectThemeMode_writeFails_reportsItAndLeavesTheThemeAlone() = runTest {
        repository.failOnWrite = IOException("disk full")
        collectState()

        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(
            UiText.Resource(R.string.error_setting_save_failed),
            viewModel.uiState.value.errorMessage
        )
        assertTrue(repository.themeModeWrites.isEmpty())
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
    }

    @Test
    fun themeRowTapped_opensTheChooser() = runTest {
        collectState()

        viewModel.onEvent(SettingsEvent.ThemeRowTapped)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isThemeDialogVisible)
    }

    @Test
    fun selectThemeMode_closesTheChooser() = runTest {
        collectState()
        viewModel.onEvent(SettingsEvent.ThemeRowTapped)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.LIGHT))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isThemeDialogVisible)
    }

    /** A failed write still closes the chooser; what it must not do is claim the theme changed. */
    @Test
    fun selectThemeMode_writeFails_stillClosesTheChooser() = runTest {
        repository.failOnWrite = IOException("disk full")
        collectState()
        viewModel.onEvent(SettingsEvent.ThemeRowTapped)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isThemeDialogVisible)
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
    }

    @Test
    fun themeDialogDismissed_closesItWithoutWritingAnything() = runTest {
        collectState()
        viewModel.onEvent(SettingsEvent.ThemeRowTapped)
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.ThemeDialogDismissed)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isThemeDialogVisible)
        assertTrue(repository.themeModeWrites.isEmpty())
    }

    @Test
    fun setDynamicColor_writesTheChoiceAndTheStoredValueDrivesTheState() = runTest {
        collectState()

        viewModel.onEvent(SettingsEvent.SetDynamicColor(true))
        advanceUntilIdle()

        assertEquals(listOf(true), repository.dynamicColorWrites)
        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)
    }

    @Test
    fun setDynamicColor_turnedBackOff_isStoredAsOff() = runTest {
        repository = FakeSettingsRepository(initialDynamicColor = true)
        viewModel = buildViewModel()
        collectState()

        viewModel.onEvent(SettingsEvent.SetDynamicColor(false))
        advanceUntilIdle()

        assertEquals(listOf(false), repository.dynamicColorWrites)
        assertFalse(viewModel.uiState.value.isDynamicColorEnabled)
    }

    @Test
    fun setDynamicColor_writeFails_reportsItAndLeavesTheSwitchAlone() = runTest {
        repository.failOnWrite = IOException("disk full")
        collectState()

        viewModel.onEvent(SettingsEvent.SetDynamicColor(true))
        advanceUntilIdle()

        assertEquals(
            UiText.Resource(R.string.error_setting_save_failed),
            viewModel.uiState.value.errorMessage
        )
        assertFalse(viewModel.uiState.value.isDynamicColorEnabled)
    }

    /**
     * The two preferences answer different questions, so neither may move the other.
     *
     * This is the behaviour the settings screen promises: turning the wallpaper colours on does
     * not hand the light and dark decision back to the system.
     */
    @Test
    fun setDynamicColor_withAForcedTheme_leavesTheForcedThemeInPlace() = runTest {
        collectState()
        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        viewModel.onEvent(SettingsEvent.SetDynamicColor(true))
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)
    }

    @Test
    fun selectThemeMode_withDynamicColorOn_leavesDynamicColorOn() = runTest {
        repository = FakeSettingsRepository(initialDynamicColor = true)
        viewModel = buildViewModel()
        collectState()

        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.LIGHT))
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)
    }

    /** Neither preference is the currency's business, and the currency is not theirs. */
    @Test
    fun selectThemeMode_leavesTheMainCurrencyAlone() = runTest {
        repository = FakeSettingsRepository(initial = Currency.GBP)
        viewModel = buildViewModel()
        collectState()

        viewModel.onEvent(SettingsEvent.SelectThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(Currency.GBP, viewModel.uiState.value.mainCurrency)
    }

    @Test
    fun uiState_onABuildWithMaterialYou_reportsTheRowAsUsable() = runTest {
        dynamicColorSupport.available = true
        viewModel = buildViewModel()
        collectState()

        assertTrue(viewModel.uiState.value.isDynamicColorSupported)
    }

    /**
     * Below Android 12 the row is drawn but cannot be used.
     *
     * The stored value is still read and still reported, because the preference is real - it just
     * has nothing to act on. The theme reads the same pair and falls back to the app's palette.
     */
    @Test
    fun uiState_onABuildWithoutMaterialYou_reportsTheRowAsUnusableButStillReadsTheValue() = runTest {
        dynamicColorSupport.available = false
        repository = FakeSettingsRepository(initialDynamicColor = true)
        viewModel = buildViewModel()
        collectState()

        assertFalse(viewModel.uiState.value.isDynamicColorSupported)
        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)
    }

    /** uiState is WhileSubscribed, so it stays cold until something collects it. */
    private fun buildViewModel() = SettingsViewModel(
        repository,
        FakeReminderStateRepository(),
        FakeReminderNotificationStatus(),
        dynamicColorSupport
    )

    private fun TestScope.collectState() {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
    }
}
