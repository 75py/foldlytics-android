package com.nagopy.android.foldlytics

import android.app.Application
import com.nagopy.android.foldlytics.data.DailySummaryRepository
import com.nagopy.android.foldlytics.data.FoldlyticsDatabase
import com.nagopy.android.foldlytics.data.PostureCheckpointRepository
import com.nagopy.android.foldlytics.data.RoomUsageEventStore
import com.nagopy.android.foldlytics.data.UsageEventReader
import com.nagopy.android.foldlytics.data.UsageSyncRepository
import com.nagopy.android.foldlytics.data.UsageSyncScheduler

class FoldlyticsApplication : Application() {
    val database: FoldlyticsDatabase by lazy {
        FoldlyticsDatabase.getInstance(this)
    }

    val usageEventReader: UsageEventReader by lazy {
        UsageEventReader(this)
    }

    val usageSyncRepository: UsageSyncRepository by lazy {
        UsageSyncRepository(
            eventSource = usageEventReader,
            eventStore = RoomUsageEventStore(database.usageEventDao()),
        )
    }

    val postureCheckpointRepository: PostureCheckpointRepository by lazy {
        PostureCheckpointRepository(
            dao = database.postureCheckpointDao(),
        )
    }

    val dailySummaryRepository: DailySummaryRepository by lazy {
        DailySummaryRepository(
            usageEventDao = database.usageEventDao(),
            checkpointDao = database.postureCheckpointDao(),
            summaryDao = database.dailyPostureSummaryDao(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        UsageSyncScheduler.schedule(this)
    }
}
