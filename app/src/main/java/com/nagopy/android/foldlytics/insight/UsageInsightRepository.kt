package com.nagopy.android.foldlytics.insight

import com.nagopy.android.foldlytics.FoldlyticsApplication
import com.nagopy.android.foldlytics.data.CalibrationStore
import com.nagopy.android.foldlytics.data.CollectionGap
import com.nagopy.android.foldlytics.data.detectCollectionGaps
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** The immutable evidence leaves the database transaction before any model work starts. */
internal class UsageInsightRepository(private val app: FoldlyticsApplication) {
    suspend fun load(nowMillis: Long, zone: ZoneId, language: String): InsightEvidence {
        val calibration = CalibrationStore(app).load()
        return app.dailySummaryRepository.withDatabaseSnapshot {
            val sync = app.usageSyncRepository.observeSyncState().first()
            val analyzer = UsageInsightAnalyzer()
            if (sync == null) {
                return@withDatabaseSnapshot InsightEvidence(
                    analyzer.analyze(emptyList(), emptyList(), null, nowMillis, zone, language),
                    calibration.toString(),
                )
            }
            val range = UsageInsightAnalyzer.resolveRange(nowMillis, zone)
            val attempts = app.usageSyncRepository.loadSyncAttempts(0L, nowMillis + 1L)
            app.dailySummaryRepository.withUpToDateSnapshot(
                calibration = calibration,
                syncedThroughMillis = sync.lastSuccessfulEndMillis,
                syncQueryBeginMillis = sync.lastQueryBeginMillis,
                checkpointRevision = app.postureCheckpointRepository.observeRevision().first(),
                zoneId = zone,
                collectionGapStarts = detectCollectionGaps(attempts).map(CollectionGap::startMillis),
            ) {
                InsightEvidence(
                    analyzer.analyze(
                        dailySummaries = dailySummaries,
                        completedSessions = loadCompleteInnerSessions(
                            range.currentStartMillis, range.currentEndMillis,
                        ),
                        syncedThroughMillis = sync.lastSuccessfulEndMillis,
                        nowMillis = nowMillis,
                        zoneId = zone,
                        languageTag = language,
                    ),
                    calibration.toString(),
                )
            }
        }
    }
}

internal data class InsightEvidence(val facts: UsageInsightFacts, val calibrationKey: String)
