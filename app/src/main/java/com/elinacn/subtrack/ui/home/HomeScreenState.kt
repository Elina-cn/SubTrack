package com.elinacn.subtrack.ui.home

import com.elinacn.subtrack.domain.model.Subscription
import com.elinacn.subtrack.domain.model.SubscriptionCategory
import com.elinacn.subtrack.domain.model.TotalPeriod
import com.elinacn.subtrack.ui.common.UiText
import java.time.LocalDate

/**
 * What [HomeViewModel] holds that is not stored: sheet visibility, validation errors, the pending
 * undo, the view choices and the day the countdowns count from.
 */
internal data class HomeScreenState(
    /** The day the countdowns count from; moved on each time the screen starts. */
    val today: LocalDate,
    val isAddSheetOpen: Boolean = false,
    val nameError: UiText? = null,
    val priceError: UiText? = null,
    val dateError: UiText? = null,
    val errorMessage: UiText? = null,
    val pendingUndo: Subscription? = null,
    val shouldRequestNotificationPermission: Boolean = false,
    /** Survives a rotation with the ViewModel, and dies with the process. */
    val categoryFilter: SubscriptionCategory? = null,
    /** The same: a choice about the view, kept for as long as the screen is alive. */
    val totalPeriod: TotalPeriod = TotalPeriod.MONTHLY
)

/** Opening, dismissing and a successful save all leave the form without complaints. */
internal fun HomeScreenState.clearedErrors(open: Boolean) = copy(
    isAddSheetOpen = open,
    nameError = null,
    priceError = null,
    dateError = null
)
