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
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionMapperTest {

    @Test
    fun toDomain_allFieldsSet_mapsEveryField() {
        val entity = entity(
            id = 7,
            name = "Netflix",
            priceInCents = 15999,
            billingPeriod = "YEARLY",
            nextPaymentDate = MARCH_15_LOCAL_MIDNIGHT,
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
        assertEquals(LocalDate.of(2026, 3, 15), domain.nextPaymentDate)
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
            nextPaymentDate = LocalDate.of(2026, 3, 15),
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
        assertEquals(MARCH_15_LOCAL_MIDNIGHT, entity.nextPaymentDate)
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
            nextPaymentDate = LocalDate.of(2027, 1, 31),
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
    fun toDomain_middayInstant_isTheSameCalendarDayAsMidnight() {
        // Anything within the day maps to that day: the conversion is calendar based, and a stored
        // instant that is not exactly midnight must not slide onto the day before or after.
        val midday = LocalDate.of(2026, 3, 15).atStartOfDay(ZoneId.systemDefault())
            .plusHours(13).toInstant().toEpochMilli()

        val domain = entity(nextPaymentDate = midday).toDomain()

        assertEquals(LocalDate.of(2026, 3, 15), domain.nextPaymentDate)
    }

    @Test
    fun roundTrip_aDateNearMidnight_survivesUnchanged() {
        val original = subscription(nextPaymentDate = LocalDate.of(2026, 12, 31))

        assertEquals(LocalDate.of(2026, 12, 31), original.toEntity().toDomain().nextPaymentDate)
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
        nextPaymentDate: LocalDate? = null,
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

    private companion object {
        /** Local midnight on 15 March 2026, so the expectation does not depend on the test zone. */
        val MARCH_15_LOCAL_MIDNIGHT: Long = LocalDate.of(2026, 3, 15)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
