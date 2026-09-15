package com.elinacn.subtrack.ui.theme

import javax.inject.Inject

/** Answers [DynamicColorSupport] from the platform. */
class AndroidDynamicColorSupport @Inject constructor() : DynamicColorSupport {

    override fun isAvailable(): Boolean = DynamicColorSupport.isAvailableOnThisBuild()
}
