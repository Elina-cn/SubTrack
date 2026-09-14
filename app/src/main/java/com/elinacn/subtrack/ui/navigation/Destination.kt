package com.elinacn.subtrack.ui.navigation

/**
 * Every place the app can navigate to.
 *
 * **Plain string routes, and still not the type-safe API** - the question §13 left open for this
 * phase, now that a destination finally takes an argument. Three reasons it stays as it is:
 *
 * 1. Type-safe routes are `@Serializable` classes, which means the kotlinx.serialization compiler
 *    plugin in the build. This phase adds no dependency, and this project has already lost a round
 *    to a compiler plugin on AGP 9 (`@Parcelize`, phase 0).
 * 2. The argument is **already typed where it matters**. The graph declares it as
 *    `NavType.LongType`, so it is parsed and checked at the boundary and arrives as a `Long`.
 * 3. What is left untyped is building the route string, and that is one function -
 *    [editSubscription] - which nothing can bypass: [EDIT_SUBSCRIPTION] is a pattern with a
 *    placeholder in it and would never resolve if it were navigated to directly.
 *
 * Worth revisiting the day something else brings serialization into the build on its own.
 */
object Destination {

    const val HOME = "home"

    const val STATISTICS = "statistics"

    const val SETTINGS = "settings"

    const val EXCHANGE_RATES = "exchange_rates"

    /** The name of the id argument, shared by the graph and the ViewModel reading it back. */
    const val EDIT_SUBSCRIPTION_ARG = "subscriptionId"

    /** The pattern the graph registers. Navigate with [editSubscription], not with this. */
    const val EDIT_SUBSCRIPTION = "edit_subscription/{$EDIT_SUBSCRIPTION_ARG}"

    /** The route that opens the edit screen on one subscription. */
    fun editSubscription(id: Long): String = "edit_subscription/$id"
}
