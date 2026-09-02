package com.elinacn.subtrack.ui.navigation

/**
 * Every place the app can navigate to.
 *
 * Plain string routes rather than the type-safe API: no destination takes an argument, which
 * is the whole thing type-safe routes buy, and they need a serialization plugin this project has
 * so far kept out of the build. Revisited in phase 15; see ARCHITECTURE section 13.
 */
object Destination {

    const val HOME = "home"

    const val SETTINGS = "settings"

    const val EXCHANGE_RATES = "exchange_rates"
}
