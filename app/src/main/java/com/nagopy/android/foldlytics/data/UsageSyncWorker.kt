package com.nagopy.android.foldlytics.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nagopy.android.foldlytics.FoldlyticsApplication
import java.util.concurrent.TimeUnit

class UsageSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FoldlyticsApplication ?: return Result.failure()
        return when (val result = app.usageSyncRepository.sync()) {
            is UsageSyncResult.Success -> Result.success()
            is UsageSyncResult.Skipped -> when (result.reason) {
                UsageReadUnavailableReason.PERMISSION_DENIED -> Result.success()
                UsageReadUnavailableReason.USER_LOCKED,
                UsageReadUnavailableReason.SYSTEM_UNAVAILABLE,
                -> Result.retry()
            }

            is UsageSyncResult.Failed -> Result.retry()
        }
    }
}

object UsageSyncScheduler {
    private const val UNIQUE_WORK_NAME = "periodic_usage_event_sync"
    private const val SYNC_INTERVAL_HOURS = 6L
    private const val RETRY_BACKOFF_MINUTES = 30L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setInitialDelay(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MINUTES,
                TimeUnit.MINUTES,
            )
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
