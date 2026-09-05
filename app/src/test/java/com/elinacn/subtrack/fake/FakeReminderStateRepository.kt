package com.elinacn.subtrack.fake

import com.elinacn.subtrack.domain.repository.ReminderStateRepository

/**
 * In-memory stand-in for the reminder bookkeeping.
 *
 * Written by hand rather than mocked, per ARCHITECTURE section 11. It holds the values, so a test
 * can assert that the flag was really stored rather than that a setter was called.
 */
class FakeReminderStateRepository(
    private var notifiedDay: Long? = null,
    private var permissionRequested: Boolean = false
) : ReminderStateRepository {

    /** Every write in order, which is how a test tells a real write from a guessed state. */
    val permissionRequestWrites = mutableListOf<Boolean>()

    /** Thrown by every writer when set, so the failure path can be exercised. */
    var failOnWrite: Exception? = null

    override suspend fun lastNotifiedDay(): Long? = notifiedDay

    override suspend fun setLastNotifiedDay(epochDay: Long) {
        failOnWrite?.let { throw it }
        notifiedDay = epochDay
    }

    override suspend fun wasPermissionRequested(): Boolean = permissionRequested

    override suspend fun setPermissionRequested() {
        failOnWrite?.let { throw it }
        permissionRequested = true
        permissionRequestWrites += true
    }
}
