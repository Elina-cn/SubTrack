package com.elinacn.subtrack.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elinacn.subtrack.data.local.dao.MonthlySnapshotDao
import com.elinacn.subtrack.data.local.entity.MonthlySnapshotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real generated DAO against an in-memory database, so the SQL and the schema are
 * exercised rather than stubbed. Nothing is written to disk and each test starts empty.
 *
 * The claim worth testing here is the one the whole table rests on: a month has one row, however
 * many times it is written.
 */
@RunWith(AndroidJUnit4::class)
class MonthlySnapshotDaoTest {

    private lateinit var database: SubTrackDatabase
    private lateinit var dao: MonthlySnapshotDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SubTrackDatabase::class.java).build()
        dao = database.monthlySnapshotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsert_thenObserveAll_emitsTheStoredRow() = runTest {
        dao.upsert(entity(period = 202609, totalInCents = 24_333))

        val rows = dao.observeAll().first()

        assertEquals(1, rows.size)
        assertEquals(202609, rows.single().period)
        assertEquals(24_333L, rows.single().totalInCents)
        assertEquals("TRY", rows.single().currencyCode)
    }

    @Test
    fun upsert_sameMonthTwice_revisesTheRowInsteadOfAddingOne() = runTest {
        dao.upsert(entity(period = 202609, totalInCents = 10_000, recordedAt = 100))

        dao.upsert(entity(period = 202609, totalInCents = 25_000, recordedAt = 200))

        val rows = dao.observeAll().first()
        assertEquals("a second row was opened for the same month", 1, rows.size)
        assertEquals(25_000L, rows.single().totalInCents)
        assertEquals(200L, rows.single().recordedAt)
    }

    @Test
    fun upsert_differentMonths_keepsBothRows() = runTest {
        dao.upsert(entity(period = 202608, totalInCents = 10_000))
        dao.upsert(entity(period = 202609, totalInCents = 25_000))

        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun upsert_theCurrencyChanges_isCarriedWithTheFigure() = runTest {
        dao.upsert(entity(period = 202609, totalInCents = 24_333, currencyCode = "TRY"))

        dao.upsert(entity(period = 202609, totalInCents = 568, currencyCode = "USD"))

        assertEquals("USD", dao.observeAll().first().single().currencyCode)
    }

    @Test
    fun getByPeriod_returnsThatMonthAndNotAnother() = runTest {
        dao.upsert(entity(period = 202608, totalInCents = 10_000))
        dao.upsert(entity(period = 202609, totalInCents = 25_000))

        assertEquals(10_000L, dao.getByPeriod(202608)?.totalInCents)
        assertEquals(25_000L, dao.getByPeriod(202609)?.totalInCents)
    }

    @Test
    fun getByPeriod_aMonthNeverRecorded_isNull() = runTest {
        dao.upsert(entity(period = 202609))

        assertNull(dao.getByPeriod(202601))
    }

    @Test
    fun observeAll_severalMonths_returnsOldestFirst() = runTest {
        // Inserted out of order, and across a year boundary, so the order can only come from the
        // query - and so a plain string sort would not pass.
        dao.upsert(entity(period = 202701))
        dao.upsert(entity(period = 202601))
        dao.upsert(entity(period = 202612))
        dao.upsert(entity(period = 202609))

        val periods = dao.observeAll().first().map { it.period }

        assertEquals(listOf(202601, 202609, 202612, 202701), periods)
    }

    @Test
    fun observeAll_emptyTable_emitsAnEmptyList() = runTest {
        assertEquals(emptyList<MonthlySnapshotEntity>(), dao.observeAll().first())
    }

    private fun entity(
        period: Int,
        totalInCents: Long = 10_000,
        currencyCode: String = "TRY",
        recordedAt: Long = 1_000
    ) = MonthlySnapshotEntity(
        period = period,
        totalInCents = totalInCents,
        currencyCode = currencyCode,
        recordedAt = recordedAt
    )
}
