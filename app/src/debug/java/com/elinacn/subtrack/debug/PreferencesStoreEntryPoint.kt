package com.elinacn.subtrack.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hands a test the one preferences store the running app is using.
 *
 * It exists because a record written by one test class cannot be undone by any other means. The
 * store caches its contents in memory and only re-reads the file while it holds the write lock,
 * so deleting `files/datastore/settings.preferences_pb` leaves a live store still answering with
 * the old value; and opening a second store over that file is the runtime error DataStoreModule
 * exists to prevent. Reaching the instance itself is the only way in.
 *
 * Debug-only on purpose. An entry point has to be annotation-processed and only the app module
 * runs KSP, so it cannot live in androidTest - but it has no business in a release build either,
 * and in src/debug it is in neither.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesStoreEntryPoint {

    /** The very instance DataStoreModule provides, not a second one over the same file. */
    fun preferencesDataStore(): DataStore<Preferences>
}
