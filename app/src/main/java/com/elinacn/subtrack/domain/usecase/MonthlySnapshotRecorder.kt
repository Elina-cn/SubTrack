package com.elinacn.subtrack.domain.usecase

import com.elinacn.subtrack.domain.model.MonthlySnapshot
import com.elinacn.subtrack.domain.model.TotalPeriod
import com.elinacn.subtrack.domain.repository.MonthlySnapshotRepository
import com.elinacn.subtrack.domain.repository.SettingsRepository
import com.elinacn.subtrack.domain.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the current month's recorded total in step with what the subscriptions actually cost.
 *
 * **Written on every change, not on a schedule** (ARCHITECTURE §19). Hanging the write off app
 * launch leaves a hole in the series for any month the user never opened the app, and a hole reads
 * as "spent nothing"; hanging it off the turn of the month needs a timer that Doze can slide. The
 * accepted cost is that a month the user edits all the way through keeps its **last** figure, not
 * an average - which is the right answer to "what changed since last month" anyway.
 *
 * **Why here and not in a ViewModel.** The total is a fact about the stored data, not about a
 * screen: it must be recorded whether the change came from the list, the currency picker or the
 * rates screen, and it must be the total of *everything*. A ViewModel would record only what its
 * own screen saw - and the home screen's total follows the category filter, so it would write a
 * filtered figure the moment a filter was on. Reading the repositories directly makes that mistake
 * unreachable rather than merely avoided: the filter lives in HomeViewModel's own state and is
 * never visible from here.
 *
 * **Why this cannot loop.** The inputs are the subscriptions table and the stored preferences; the
 * output is a different table. Room re-runs an observing query only when a table it reads is
 * written, and nothing here observes `monthly_snapshots` - [MonthlySnapshotRepository.getByPeriod]
 * is a one-shot read, not a Flow. A recorded snapshot therefore produces no new emission. The
 * skip in [record] is the second line of defence, not the first: even a future flow that did
 * observe the table would settle after one pass, because an unchanged figure is not written.
 */
@Singleton
class MonthlySnapshotRecorder @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val settings: SettingsRepository,
    private val snapshots: MonthlySnapshotRepository,
    private val clock: Clock
) {

    /**
     * Starts watching, for as long as [scope] lives.
     *
     * The scope is handed in rather than made here so its lifetime belongs to whoever owns the
     * process - and so a test can drive [record] directly instead of racing a background job.
     */
    fun start(scope: CoroutineScope): Job = scope.launch { record() }

    /**
     * Collects for as long as it is allowed to, recording each new total against the month it
     * falls in.
     *
     * The figure is always the **monthly** one: a yearly total is the same fact shown differently
     * (ARCHITECTURE §6), and storing both spans would let them disagree. The arithmetic is the
     * converter the screen already uses - no second total is computed anywhere.
     *
     * An empty list is recorded as **zero, not as nothing**. Phase 13 has to be able to tell "that
     * month cost nothing" from "that month has no record", and it can only do that if absence
     * means absence.
     */
    suspend fun record() {
        combine(
            subscriptions.observeAll(),
            settings.observeMainCurrency(),
            settings.observeRates()
        ) { all, mainCurrency, rates ->
            MonthlySnapshot(
                // The month as the device reckons it, from the injected clock (ARCHITECTURE §17):
                // never LocalDate.now(), so a test can put the run in any month it likes.
                period = YearMonth.now(clock),
                total = CurrencyConverter(rates).totalIn(all, mainCurrency, TotalPeriod.MONTHLY),
                currency = mainCurrency,
                recordedAt = clock.millis()
            )
        }.collect { snapshot -> store(snapshot) }
    }

    /**
     * Writes the month's row, unless it already says this.
     *
     * A rename or a date edit re-emits the list without moving the total; rewriting the row then
     * would only move [MonthlySnapshot.recordedAt], which is meant to say when the figure last
     * changed.
     *
     * A failed write is swallowed on purpose. There is no screen to tell - this runs behind
     * whatever the user is doing - and losing one month's bookkeeping is a far smaller harm than
     * taking the app down with it. Cancellation is not a failure and is rethrown, or the scope
     * would never come down.
     */
    private suspend fun store(snapshot: MonthlySnapshot) {
        try {
            val recorded = snapshots.getByPeriod(snapshot.period)
            if (recorded?.total == snapshot.total && recorded.currency == snapshot.currency) return
            snapshots.upsert(snapshot)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Deliberately dropped; see above.
        }
    }
}
