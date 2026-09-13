package com.elinacn.subtrack.data.mapper

import com.elinacn.subtrack.data.local.entity.MonthlySnapshotEntity
import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.MonthlySnapshot
import com.elinacn.subtrack.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/**
 * Both directions of the snapshot mapper, and the edges of the one encoded column.
 *
 * The month travels as `year * 100 + month`, so January and December are the interesting values:
 * they are where a division or a remainder would go wrong without anything else noticing.
 */
class MonthlySnapshotMapperTest {

    @Test
    fun toEntity_writesTheMonthAsOneSortableNumber() {
        val entity = snapshot(YearMonth.of(2026, 9)).toEntity()

        assertEquals(202609, entity.period)
    }

    @Test
    fun toEntity_january_keepsTheLeadingZeroOnTheMonth() {
        assertEquals(202601, snapshot(YearMonth.of(2026, 1)).toEntity().period)
    }

    @Test
    fun toEntity_december_isTheLastNumberOfItsYear() {
        assertEquals(202612, snapshot(YearMonth.of(2026, 12)).toEntity().period)
    }

    @Test
    fun storedPeriods_sortIntoCalendarOrder() {
        val months = listOf(
            YearMonth.of(2027, 1),
            YearMonth.of(2026, 12),
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 9)
        )

        val sorted = months.map { it.toStoredPeriod() }.sorted()

        // The whole reason for this encoding: comparing the numbers compares the months.
        assertEquals(listOf(202601, 202609, 202612, 202701), sorted)
    }

    @Test
    fun toEntity_writesMoneyAsMinorUnitsAndTheCurrencyAsItsCode() {
        val entity = MonthlySnapshot(
            period = YearMonth.of(2026, 9),
            total = Money(24_333),
            currency = Currency.USD,
            recordedAt = 1_700_000_000_000
        ).toEntity()

        assertEquals(24_333L, entity.totalInCents)
        assertEquals("USD", entity.currencyCode)
        assertEquals(1_700_000_000_000L, entity.recordedAt)
    }

    @Test
    fun toDomain_readsBackWhatWasWritten() {
        val original = MonthlySnapshot(
            period = YearMonth.of(2026, 12),
            total = Money(1_234_567),
            currency = Currency.GBP,
            recordedAt = 42
        )

        assertEquals(original, original.toEntity().toDomainOrNull())
    }

    @Test
    fun toDomain_zeroTotal_survivesTheRoundTrip() {
        // Zero is a real answer - "that month cost nothing" - and must not read back as missing.
        val original = snapshot(YearMonth.of(2026, 9), cents = 0)

        assertEquals(Money(0), original.toEntity().toDomainOrNull()?.total)
    }

    @Test
    fun toDomain_aVeryLargeTotal_isNotNarrowed() {
        val original = snapshot(YearMonth.of(2026, 9), cents = Long.MAX_VALUE)

        assertEquals(Money(Long.MAX_VALUE), original.toEntity().toDomainOrNull()?.total)
    }

    @Test
    fun toDomain_unknownCurrencyCode_fallsBackToTheBase() {
        val row = entity(period = 202609, currencyCode = "XXX")

        // Same rule as the subscription mapper: one unreadable field must not lose the row.
        assertEquals(Currency.Base, row.toDomainOrNull()?.currency)
    }

    @Test
    fun toDomain_aMonthThatDoesNotExist_isDropped() {
        // A made-up point on a chart is worse than a missing one, so there is no fallback here.
        assertNull(entity(period = 202613).toDomainOrNull())
        assertNull(entity(period = 202600).toDomainOrNull())
        assertNull(entity(period = 0).toDomainOrNull())
        assertNull(entity(period = -202609).toDomainOrNull())
    }

    @Test
    fun toDomain_aList_keepsTheReadableRowsAndSkipsTheRest() {
        val rows = listOf(
            entity(period = 202601),
            entity(period = 202699),
            entity(period = 202612)
        )

        val periods = rows.toDomain().map { it.period }

        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 12)), periods)
    }

    private fun snapshot(period: YearMonth, cents: Long = 10_000) = MonthlySnapshot(
        period = period,
        total = Money(cents),
        currency = Currency.TRY,
        recordedAt = 1_000
    )

    private fun entity(
        period: Int,
        totalInCents: Long = 10_000,
        currencyCode: String = "TRY"
    ) = MonthlySnapshotEntity(
        period = period,
        totalInCents = totalInCents,
        currencyCode = currencyCode,
        recordedAt = 1_000
    )
}
