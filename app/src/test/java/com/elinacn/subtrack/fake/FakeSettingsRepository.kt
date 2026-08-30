package com.elinacn.subtrack.fake

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory stand-in for the real settings store.
 *
 * Written by hand rather than mocked, per ARCHITECTURE section 11. It actually holds the value and
 * re-emits on a write, so a test can assert that a screen reacted to the stored preference rather
 * than that a setter was called.
 *
 * [writes] records every stored value in order, which is how a test tells a real write apart from
 * a ViewModel that only updated its own copy.
 */
class FakeSettingsRepository(
    initial: Currency = Currency.Base
) : SettingsRepository {

    private val stored = MutableStateFlow(initial)

    val writes = mutableListOf<Currency>()

    /** Thrown by [setMainCurrency] when set, so the failure path can be exercised. */
    var failOnWrite: Exception? = null

    override fun observeMainCurrency(): Flow<Currency> = stored.asStateFlow()

    override suspend fun setMainCurrency(currency: Currency) {
        failOnWrite?.let { throw it }
        writes += currency
        stored.value = currency
    }
}
