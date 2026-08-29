package com.elinacn.subtrack.data.mapper

import com.elinacn.subtrack.data.local.entity.SubscriptionEntity
import com.elinacn.subtrack.domain.model.BillingPeriod
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionMapperTest {

    @Test
    fun toDomain_allFieldsSet_mapsEveryField() {
        val entity = entity(
            id = 7,
            name = "Netflix",
            priceInCents = 15999,
            billingPeriod = "YEARLY",
            nextPaymentDate = 1_700_000_000_000,
            category = "ENTERTAINMENT",
            iconKey = "netflix",
            createdAt = 42
        )

        val domain = entity.toDomain()

        assertEquals(7L, domain.id)
        assertEquals("Netflix", domain.name)
        assertEquals(Money(15999), domain.price)
        assertEquals(Currency.TRY, domain.currency)
        assertEquals(BillingPeriod.YEARLY, domain.billingPeriod)
        assertEquals(1_700_000_000_000, domain.nextPaymentDate)
        assertEquals(SubscriptionCategory.ENTERTAINMENT, domain.category)
        assertEquals("netflix", domain.iconKey)
        assertEquals(42L, domain.createdAt)
    }

    @Test
    fun toEntity_allFieldsSet_mapsEveryField() {
        val domain = subscription(
            id = 3,
            name = "Spotify",
            cents = 5990,
            billingPeriod = BillingPeriod.WEEKLY,
            nextPaymentDate = 99,
            category = SubscriptionCategory.HEALTH,
            iconKey = "spotify",
            createdAt = 11
        )

        val entity = domain.toEntity()

        assertEquals(3L, entity.id)
        assertEquals("Spotify", entity.name)
        assertEquals(5990L, entity.priceInCents)
        assertEquals("TRY", entity.currencyCode)
        assertEquals("WEEKLY", entity.billingPeriod)
        assertEquals(99L, entity.nextPaymentDate)
        assertEquals("HEALTH", entity.category)
        assertEquals("spotify", entity.iconKey)
        assertEquals(11L, entity.createdAt)
    }

    @Test
    fun roundTrip_domainToEntityAndBack_returnsEqualSubscription() {
        val original = subscription(
            id = 5,
            name = "iCloud",
            cents = 2999,
            billingPeriod = BillingPeriod.MONTHLY,
            nextPaymentDate = 1234,
            category = SubscriptionCategory.PRODUCTIVITY,
            iconKey = "icloud",
            createdAt = 5678
        )

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun roundTrip_nullableFieldsNull_staysNull() {
        val original = subscription(nextPaymentDate = null, iconKey = null)

        val restored = original.toEntity().toDomain()

        assertNull(restored.nextPaymentDate)
        assertNull(restored.iconKey)
        assertEquals(original, restored)
    }

    @Test
    fun toDomain_unknownBillingPeriod_fallsBackToMonthlyWithoutThrowing() {
        val domain = entity(billingPeriod = "FORTNIGHTLY").toDomain()

        assertEquals(BillingPeriod.MONTHLY, domain.billingPeriod)
    }

    @Test
    fun toDomain_unknownCategory_fallsBackToOtherWithoutThrowing() {
        val domain = entity(category = "GAMING").toDomain()

        assertEquals(SubscriptionCategory.OTHER, domain.category)
    }

    @Test
    fun toDomain_emptyEnumNames_fallBackRatherThanThrow() {
        val domain = entity(billingPeriod = "", category = "").toDomain()

        assertEquals(BillingPeriod.MONTHLY, domain.billingPeriod)
        assertEquals(SubscriptionCategory.OTHER, domain.category)
    }

    @Test
    fun toDomain_listOfEntities_mapsEachInOrder() {
        val entities = listOf(entity(id = 1, name = "A"), entity(id = 2, name = "B"))

        val domain = entities.toDomain()

        assertEquals(listOf(1L, 2L), domain.map { it.id })
        assertEquals(listOf("A", "B"), domain.map { it.name })
    }

    private fun entity(
        id: Long = 1,
        name: String = "Test",
        priceInCents: Long = 1000,
        currencyCode: String = "TRY",
        billingPeriod: String = "MONTHLY",
        nextPaymentDate: Long? = null,
        category: String = "OTHER",
        iconKey: String? = null,
        createdAt: Long = 0
    ) = SubscriptionEntity(
        id = id,
        name = name,
        priceInCents = priceInCents,
        currencyCode = currencyCode,
        billingPeriod = billingPeriod,
        nextPaymentDate = nextPaymentDate,
        category = category,
        iconKey = iconKey,
        createdAt = createdAt
    )

    private fun subscription(
        id: Long = 1,
        name: String = "Test",
        cents: Long = 1000,
        currency: Currency = Currency.TRY,
        billingPeriod: BillingPeriod = BillingPeriod.MONTHLY,
        nextPaymentDate: Long? = null,
        category: SubscriptionCategory = SubscriptionCategory.OTHER,
        iconKey: String? = null,
        createdAt: Long = 0
    ) = Subscription(
        id = id,
        name = name,
        price = Money(cents),
        currency = currency,
        billingPeriod = billingPeriod,
        nextPaymentDate = nextPaymentDate,
        category = category,
        iconKey = iconKey,
        createdAt = createdAt
    )
}
