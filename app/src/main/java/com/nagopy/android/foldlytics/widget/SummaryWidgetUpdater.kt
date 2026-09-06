package com.nagopy.android.foldlytics.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nagopy.android.foldlytics.FoldlyticsApplication
import com.nagopy.android.foldlytics.data.CalibrationStore
import com.nagopy.android.foldlytics.data.CollectionGap
import com.nagopy.android.foldlytics.data.UsageSyncResult
import com.nagopy.android.foldlytics.data.detectCollectionGaps
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object SummaryWidgetUpdater {
    internal const val SYNC_INPUT = "sync"
    private val renderMutex = Mutex()

    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, SummaryWidgetProvider::class.java))

    fun enqueue(context: Context, sync: Boolean = false) {
        if (ids(context).isEmpty()) return
        val request = OneTimeWorkRequestBuilder<SummaryWidgetWorker>()
            .setInputData(workDataOf(SYNC_INPUT to sync))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (sync) "summary_widget_sync" else "summary_widget_render",
            if (sync) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun onSyncCompleted(context: Context, result: UsageSyncResult) {
        SummaryWidgetPreferences(context).recordSyncResult(result)
        enqueue(context)
    }

    internal suspend fun updateAll(context: Context) = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val ids = ids(context)
            if (ids.isEmpty()) return@withLock
            val app = context.applicationContext as FoldlyticsApplication
            val preferences = SummaryWidgetPreferences(context)
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val hasPermission = app.usageSyncRepository.hasUsageAccess()
            val states = try {
                app.dailySummaryRepository.withDatabaseSnapshot {
                    val syncState = app.usageSyncRepository.observeSyncState().first()
                    val summaries = if (hasPermission && syncState != null) {
                        val attempts = app.usageSyncRepository.loadSyncAttempts(0L, now + 1L)
                        app.dailySummaryRepository.ensureUpToDate(
                            calibration = CalibrationStore(context).load(),
                            syncedThroughMillis = syncState.lastSuccessfulEndMillis,
                            syncQueryBeginMillis = syncState.lastQueryBeginMillis,
                            checkpointRevision = app.postureCheckpointRepository.observeRevision().first(),
                            zoneId = zone,
                            collectionGapStarts = detectCollectionGaps(attempts).map(CollectionGap::startMillis),
                        )
                    } else emptyList()
                    ids.associateWith { id ->
                        buildSummaryWidgetState(
                            period = preferences.period(id),
                            summaries = summaries,
                            syncedThroughMillis = syncState?.lastSuccessfulEndMillis,
                            lastSyncMillis = syncState?.lastSuccessfulAtMillis,
                            hasPermission = hasPermission,
                            updateFailed = preferences.updateFailed,
                            nowMillis = now,
                            zoneId = zone,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ids.associateWith { id ->
                    buildSummaryWidgetState(
                        period = preferences.period(id),
                        summaries = emptyList(),
                        syncedThroughMillis = null,
                        lastSyncMillis = null,
                        hasPermission = hasPermission,
                        updateFailed = true,
                        nowMillis = now,
                        zoneId = zone,
                    )
                }
            }
            val manager = AppWidgetManager.getInstance(context)
            states.forEach { (id, state) ->
                // A widget may have been removed while aggregation was running.
                if (manager.getAppWidgetInfo(id) != null) {
                    manager.updateAppWidget(id, SummaryWidgetRenderer.responsive(context, id, state))
                }
            }
        }
    }
}

class SummaryWidgetWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (inputData.getBoolean(SummaryWidgetUpdater.SYNC_INPUT, false)) {
            val app = applicationContext as FoldlyticsApplication
            SummaryWidgetPreferences(applicationContext).recordSyncResult(app.usageSyncRepository.sync())
        }
        SummaryWidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }
}
