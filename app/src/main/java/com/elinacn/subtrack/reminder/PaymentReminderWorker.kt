package com.elinacn.subtrack.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs once a day and notifies about payments that are due, nearly due, or recently overdue. */
@HiltWorker
class PaymentReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = Result.success()
}
