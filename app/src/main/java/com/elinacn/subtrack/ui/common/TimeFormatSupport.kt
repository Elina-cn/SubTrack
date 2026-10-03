package com.elinacn.subtrack.ui.common

/**
 * Whether the phone shows times on a 24-hour clock.
 *
 * A setting of the phone, not of the app, so it is read rather than stored. An interface for the
 * same reason as [DynamicColorSupport][com.elinacn.subtrack.ui.theme.DynamicColorSupport]: the
 * settings ViewModel reads it and is tested off a device.
 */
interface TimeFormatSupport {

    /** True when the phone is set to the 24-hour clock, or its language uses one by default. */
    fun is24HourFormat(): Boolean
}
