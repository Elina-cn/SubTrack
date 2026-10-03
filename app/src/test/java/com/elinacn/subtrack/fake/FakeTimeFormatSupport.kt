package com.elinacn.subtrack.fake

import com.elinacn.subtrack.ui.common.TimeFormatSupport

/**
 * Answers the clock-format question from a field instead of the phone's settings.
 *
 * A var so a test can switch the phone between 24 and 12 hours "while the user was away" and check
 * that coming back to the screen picks it up.
 */
class FakeTimeFormatSupport(var is24Hour: Boolean = true) : TimeFormatSupport {

    override fun is24HourFormat(): Boolean = is24Hour
}
