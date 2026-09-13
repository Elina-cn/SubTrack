package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.Currency
import com.elinacn.subtrack.domain.model.Money
import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.model.TotalPeriod

/**
 * What one category costs a month, and how much of the whole that is.
 *
 * [percent] is a whole number and the percentages of a breakdown always add up to exactly 100 -
 * see [SubscriptionStatistics.shareOf].
 */
data class CategoryShare(
    val category: SubscriptionCategory,
    val total: Money,
    val percent: Int
)

/** A subscription beside what it actually costs per month, converted into one currency. */
data class SubscriptionCost(
    val subscription: Subscription,
    val monthlyCost: Money
)

/**
 * The figures behind the statistics screen.
 *
 * Pure functions over a list: no repository, no clock, no Android. Every amount goes through
 * [CurrencyConverter], which is the app's only money arithmetic - a second total written here
 * would be a second answer to the same question (ARCHITECTURE §6).
 *
 * Everything is a **monthly** figure. A yearly view is a way of showing the same fact and belongs
 * to whoever is showing it.
 */
object SubscriptionStatistics {

    /**
     * How many subscriptions the "most expensive" list names.
     *
     * Five because the list answers "which ones are the big ones", and that is a question about
     * the top of the list rather than about all of it: five rows fit under the breakdown on a
     * 360dp screen without the section turning into a second copy of the home list. A shorter list
     * would hide a close contender; a longer one stops being a summary.
     */
    const val MOST_EXPENSIVE_COUNT = 5

    /**
     * What each category costs per month, biggest first, with the categories that cost nothing
     * left out.
     *
     * **A category with no money in it gets no row.** The four categories are a fixed vocabulary,
     * not a fixed list of answers: a row reading "Health, 0,00, 0%" draws a bar of nothing and
     * gives a screen reader an extra stop that says the user has no health subscriptions - which
     * is not what the screen is for. The same reasoning kept OTHER off the subscription cards in
     * phase 11a.
     *
     * Each category is totalled as a group rather than row by row, so each figure rounds once -
     * the same rule the dashboard total follows.
     */
    fun byCategory(
        subscriptions: List<Subscription>,
        converter: CurrencyConverter,
        target: Currency
    ): List<CategoryShare> {
        val shown = SubscriptionCategory.entries
            .map { category ->
                category to converter.totalIn(
                    subscriptions.filter { it.category == category },
                    target,
                    TotalPeriod.MONTHLY
                )
            }
            .filter { (_, total) -> total > Money.ZERO }
        val percents = shareOf(shown.map { (_, total) -> total.cents })
        return shown
            .mapIndexed { index, (category, total) ->
                CategoryShare(category, total, percents[index])
            }
            // Biggest first: a breakdown is read to find out where the money goes. Ties fall back
            // to the declaration order so the list cannot reshuffle itself between visits.
            .sortedWith(compareByDescending<CategoryShare> { it.total }.thenBy { it.category })
    }

    /**
     * The dearest subscriptions by monthly cost, dearest first, at most [limit] of them.
     *
     * Each row is converted on its own because each row is shown on its own - this is the one
     * place in the app where a per-subscription converted figure reaches the screen. They are
     * never added together, so the per-row rounding cannot turn into a total that disagrees with
     * itself.
     *
     * The name breaks a tie, so two subscriptions costing the same always come out in the same
     * order.
     */
    fun mostExpensive(
        subscriptions: List<Subscription>,
        converter: CurrencyConverter,
        target: Currency,
        limit: Int = MOST_EXPENSIVE_COUNT
    ): List<SubscriptionCost> = subscriptions
        .map { SubscriptionCost(it, converter.totalIn(listOf(it), target, TotalPeriod.MONTHLY)) }
        .sortedWith(
            compareByDescending<SubscriptionCost> { it.monthlyCost }
                .thenBy { it.subscription.name }
        )
        .take(limit)

    /**
     * Turns amounts into whole percentages that add up to exactly 100.
     *
     * Rounding each share on its own gives 33 + 33 + 33 = 99, and a breakdown whose parts do not
     * make a whole is a breakdown the reader has to distrust. This is the largest remainder
     * method: floor every share, then hand the leftover points to whoever was cut by the most.
     * The result is still whole percentages, and it still sums to 100.
     *
     * Integer arithmetic throughout - no Double anywhere near money (§6). The widening by a
     * hundred is safe at any total the price ceiling allows: a Long runs out around 9,2 × 10¹⁸ and
     * the biggest total this app can build is many orders below that even after the multiplication.
     *
     * An empty list, or one that adds up to nothing, comes back as zeros: there is no whole to
     * take a share of, and nothing is divided by it.
     */
    fun shareOf(values: List<Long>): List<Int> {
        val total = values.sum()
        if (total <= 0L) return List(values.size) { 0 }

        val floored = values.map { (it * PERCENT) / total }
        val shares = floored.toMutableList()
        var leftover = PERCENT - floored.sum()
        // Whoever lost the most to the floor gets the first point back. Sorting is stable, so a
        // tie goes to the earlier entry and the same input always gives the same answer.
        val byRemainder = values.indices.sortedByDescending { (values[it] * PERCENT) % total }
        for (index in byRemainder) {
            if (leftover <= 0L) break
            shares[index] = shares[index] + 1
            leftover--
        }
        return shares.map { it.toInt() }
    }

    private const val PERCENT = 100L
}
