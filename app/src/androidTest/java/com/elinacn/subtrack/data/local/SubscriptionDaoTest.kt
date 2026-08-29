package com.elinacn.subtrack.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elinacn.subtrack.data.local.dao.SubscriptionDao
import com.elinacn.subtrack.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real generated DAO against an in-memory database, so the SQL and the schema are
 * exercised rather than stubbed. Nothing is written to disk and each test starts empty.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionDaoTest {

    private lateinit var database: SubTrackDatabase
    private lateinit var dao: SubscriptionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SubTrackDatabase::class.java).build()
        dao = database.subscriptionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_thenObserveAll_emitsTheStoredRow() = runTest {
        val id = dao.insert(entity(name = "Netflix", priceInCents = 15999))

        val rows = dao.observeAll().first()

        assertEquals(1, rows.size)
        assertEquals("Netflix", rows.single().name)
        assertEquals(15999L, rows.single().priceInCents)
        assertEquals(id, rows.single().id)
    }

    @Test
    fun observeAll_severalRows_returnsNewestFirst() = runTest {
        dao.insert(entity(name = "Older", createdAt = 100))
        dao.insert(entity(name = "Newer", createdAt = 200))

        val names = dao.observeAll().first().map { it.name }

        assertEquals(listOf("Newer", "Older"), names)
    }

    @Test
    fun getById_knownId_returnsTheRow() = runTest {
        val id = dao.insert(entity(name = "Spotify"))

        assertEquals("Spotify", dao.getById(id)?.name)
    }

    @Test
    fun getById_unknownId_returnsNull() = runTest {
        assertNull(dao.getById(4242))
    }

    @Test
    fun deleteById_existingRow_removesIt() = runTest {
        val id = dao.insert(entity(name = "Spotify"))

        dao.deleteById(id)

        assertNull(dao.getById(id))
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun deleteById_unknownId_leavesTheTableAlone() = runTest {
        dao.insert(entity(name = "Netflix"))

        dao.deleteById(9999)

        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun insert_afterDeletingTheHighestRow_doesNotReuseThatId() = runTest {
        val first = dao.insert(entity(name = "First"))
        val second = dao.insert(entity(name = "Second"))
        dao.deleteById(second)

        val third = dao.insert(entity(name = "Third"))

        // AUTOINCREMENT never hands a deleted id back. This is the phase 1 bug - ids derived from
        // max+1 collided after a deletion and the list key stopped being unique - proven not to
        // return once Room owns the id.
        assertTrue("expected an id above $second but got $third", third > second)
        assertEquals(first + 1, second)
    }

    @Test
    fun update_existingRow_replacesItsValues() = runTest {
        val id = dao.insert(entity(name = "Netflix", priceInCents = 15999))

        dao.update(entity(id = id, name = "Netflix", priceInCents = 17999))

        assertEquals(17999L, dao.getById(id)?.priceInCents)
        assertEquals(1, dao.observeAll().first().size)
    }

    private fun entity(
        id: Long = 0,
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
}
