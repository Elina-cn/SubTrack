package com.elinacn.subtrack.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.elinacn.subtrack.domain.repository.ReminderStateRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

/**
 * Preference-backed reminder bookkeeping.
 *
 * Shares the one [DataStore] Hilt provides rather than opening a file of its own; a second
 * instance over the same file is a runtime error, and a second file for a single Long would be
 * a file to migrate later for no gain. See ARCHITECTURE section 14.
 */
class ReminderStateRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ReminderStateRepository {

    /**
     * Read once rather than observed: the caller is a background job that runs, decides and ends.
     *
     * An unreadable file falls back to "never notified", which costs at most one extra reminder.
     * Only [IOException] is treated that way; anything else is a bug and is rethrown rather than
     * hidden the way ARCHITECTURE section 9 forbids.
     */
    override suspend fun lastNotifiedDay(): Long? = readPreferences()[LAST_NOTIFIED_DAY]

    override suspend fun setLastNotifiedDay(epochDay: Long) {
        dataStore.edit { preferences -> preferences[LAST_NOTIFIED_DAY] = epochDay }
    }

    /** Absent means never asked, which is also the right answer for a file that will not open. */
    override suspend fun wasPermissionRequested(): Boolean = readPreferences()[PERMISSION_REQUESTED] == true

    override suspend fun setPermissionRequested() {
        dataStore.edit { preferences -> preferences[PERMISSION_REQUESTED] = true }
    }

    private suspend fun readPreferences(): Preferences = dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .first()

    private companion object {
        /** Epoch day, not epoch millis: "was one sent today" is a calendar question. */
        val LAST_NOTIFIED_DAY = longPreferencesKey("reminder_last_notified_day")

        /** Survives the app being closed; that is the whole point of storing it. */
        val PERMISSION_REQUESTED = booleanPreferencesKey("reminder_permission_requested")
    }
}
