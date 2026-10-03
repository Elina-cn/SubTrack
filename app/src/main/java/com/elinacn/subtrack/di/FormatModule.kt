package com.elinacn.subtrack.di

import com.elinacn.subtrack.ui.common.AndroidTimeFormatSupport
import com.elinacn.subtrack.ui.common.TimeFormatSupport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Points the display-format interfaces at their platform-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FormatModule {

    /** @Binds for the same reason as the repositories: the implementation builds itself. */
    @Binds
    @Singleton
    abstract fun bindTimeFormatSupport(impl: AndroidTimeFormatSupport): TimeFormatSupport
}
