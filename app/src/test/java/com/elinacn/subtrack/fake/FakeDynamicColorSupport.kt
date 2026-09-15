package com.elinacn.subtrack.fake

import com.elinacn.subtrack.ui.theme.DynamicColorSupport

/**
 * Stand-in for the platform's answer about Material You.
 *
 * Written by hand rather than mocked, per ARCHITECTURE section 11. The real implementation reads
 * `Build.VERSION.SDK_INT`, which is zero off a device, so without this every test would be a test
 * of the unsupported case.
 */
class FakeDynamicColorSupport(
    var available: Boolean = true
) : DynamicColorSupport {

    override fun isAvailable(): Boolean = available
}
