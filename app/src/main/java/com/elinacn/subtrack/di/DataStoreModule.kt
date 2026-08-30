package com.elinacn.subtrack.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Supplies the preferences store, living as long as the application itself. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * One store per file, enforced here.
     *
     * DataStore throws at runtime if a second instance is opened over the same file, so @Singleton
     * is not an optimisation - it is what keeps the app from crashing. @ApplicationContext for the
     * same reason as the database: this object is held for the life of the process.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(SETTINGS_STORE_NAME) }
        )

    /** File name of the preferences store, without the extension DataStore adds. */
    private const val SETTINGS_STORE_NAME = "settings"
}
