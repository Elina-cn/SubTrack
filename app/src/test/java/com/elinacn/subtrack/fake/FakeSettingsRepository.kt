package com.elinacn.subtrack.fake

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.ExchangeRateTable
import com.elinacn.subtrack.domain.model.ThemeMode
import com.elinacn.subtrack.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for the real settings store.
 *
 * Written by hand rather than mocked, per ARCHITECTURE section 11. It actually holds the values
 * and re-emits on a write, so a test can assert that a screen reacted to the stored preference
 * rather than that a setter was called.
 *
 * [writes] and [rateWrites] record every stored value in order, which is how a test tells a real
 * write apart from a ViewModel that only updated its own copy.
 */
class FakeSettingsRepository(
    initial: Currency = Currency.Base,
    initialThemeMode: ThemeMode = ThemeMode.Default,
    initialDynamicColor: Boolean = false
) : SettingsRepository {

    private val stored = MutableStateFlow(initial)

    private val storedThemeMode = MutableStateFlow(initialThemeMode)

    private val storedDynamicColor = MutableStateFlow(initialDynamicColor)

    /** Only the edited rates, exactly as the real store keeps them. */
    private val storedRates = MutableStateFlow<Map<Currency, Long>>(emptyMap())

    private val updatedAt = MutableStateFlow<Long?>(null)

    val writes = mutableListOf<Currency>()

    val rateWrites = mutableListOf<Pair<Currency, Long>>()

    val themeModeWrites = mutableListOf<ThemeMode>()

    val dynamicColorWrites = mutableListOf<Boolean>()

    var resetCount = 0
        private set

    /** Thrown by every writer when set, so the failure path can be exercised. */
    var failOnWrite: Exception? = null

    /** What [setRate] stamps as the edit time, so tests do not depend on the clock. */
    var now: Long = 1_000L

    override fun observeMainCurrency(): Flow<Currency> = stored.asStateFlow()

    override suspend fun setMainCurrency(currency: Currency) {
        failOnWrite?.let { throw it }
        writes += currency
        stored.value = currency
    }

    override fun observeRates(): Flow<ExchangeRateTable> =
        storedRates.map { ExchangeRateTable.of(it) }

    override suspend fun setRate(currency: Currency, scaledRate: Long) {
        failOnWrite?.let { throw it }
        rateWrites += currency to scaledRate
        storedRates.value = storedRates.value + (currency to scaledRate)
        updatedAt.value = now
    }

    override suspend fun resetRates() {
        failOnWrite?.let { throw it }
        resetCount++
        storedRates.value = emptyMap()
        updatedAt.value = null
    }

    override fun observeRatesUpdatedAt(): Flow<Long?> = updatedAt.asStateFlow()

    override fun observeThemeMode(): Flow<ThemeMode> = storedThemeMode.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        failOnWrite?.let { throw it }
        themeModeWrites += mode
        storedThemeMode.value = mode
    }

    override fun observeDynamicColor(): Flow<Boolean> = storedDynamicColor.asStateFlow()

    override suspend fun setDynamicColor(enabled: Boolean) {
        failOnWrite?.let { throw it }
        dynamicColorWrites += enabled
        storedDynamicColor.value = enabled
    }
}
