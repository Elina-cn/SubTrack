package com.elinacn.subtrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Entry point of the app.
 *
 * The body is empty on purpose: the graph it used to build by hand now lives in di/DatabaseModule
 * and di/RepositoryModule, and @HiltAndroidApp generates the component that assembles it.
 */
@HiltAndroidApp
class SubTrackApplication : Application()
