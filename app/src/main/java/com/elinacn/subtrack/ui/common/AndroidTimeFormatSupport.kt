package com.elinacn.subtrack.ui.common

import android.content.Context
import android.text.format.DateFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Answers [TimeFormatSupport] from the phone's own date and time settings. */
class AndroidTimeFormatSupport @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TimeFormatSupport {

    override fun is24HourFormat(): Boolean = DateFormat.is24HourFormat(context)
}
