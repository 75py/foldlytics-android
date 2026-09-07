package com.nagopy.android.foldlytics.data

import androidx.room.withTransaction
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.CalibrationValidationFailure
import com.nagopy.android.foldlytics.model.DailyAppUsageSummary
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DailySummaryRepository(
    private val usageEventDao: UsageEventDao,
    private val checkpointDao: PostureCheckpointDao,
    private val summaryDao: DailyPostureSummaryDao,
    private val database: FoldlyticsDatabase? = null,
) {
    private val analyzer = UsageAnalyzer { packageName -> packageName }
    private val aggregationMutex = Mutex()

    /** Encloses source reads and derived-cache updates in the same Room snapshot. */
    internal suspend fun <T> withDatabaseSnapshot(read: suspend () -> T): T =
        if (database == null) read() else database.withTransaction { read() }

    suspend fun <T> withUpToDateSnapshot(
        calibration: Calibration,
        syncedThroughMillis: Long,
        syncQueryBeginMillis: Long,
        checkpointRevision: Long,
        zoneId: ZoneId,
        collectionGapStarts: List<Long>,
        read: suspend DailySummarySnapshot.() -> T,
    ): T = withDatabaseSnapshot {
        aggregationMutex.withLock {
            DailySummarySnapshot(
                dailySummaries = ensureUpToDateLocked(
                    calibration = calibration,
                    syncedThroughMillis = syncedThroughMillis,
                    syncQueryBeginMillis = syncQueryBeginMillis,
                    checkpointRevision = checkpointRevision,
                    zoneId = zoneId,
                    collectionGapStarts = collectionGapStarts,
                ),
                summaryDao = summaryDao,
            ).read()
        }
    }

    suspend fun ensureUpToDate(
        calibration: Calibration,
        syncedThroughMillis: Long,
        syncQueryBeginMillis: Long,
        checkpointRevision: Long,
        zoneId: ZoneId,
        collectionGapStarts: List<Long>,
    ): List<DailyPostureSummary> = withUpToDateSnapshot(
        calibration = calibration,
        syncedThroughMillis = syncedThroughMillis,
        syncQueryBeginMillis = syncQueryBeginMillis,
        checkpointRevision = checkpointRevision,
        zoneId = zoneId,
        collectionGapStarts = collectionGapStarts,
    ) {
        dailySummaries
    }

    private suspend fun ensureUpToDateLocked(
        calibration: Calibration,
        syncedThroughMillis: Long,
        syncQueryBeginMillis: Long,
        checkpointRevision: Long,
        zoneId: ZoneId,
        collectionGapStarts: List<Long>,
    ): List<DailyPostureSummary> {
        val rangeEnd = syncedThroughMillis.coerceAtLeast(0L)
        val calibrationKey = calibration.dailySummaryCacheKey()
        val existingState = summaryDao.loadState()
        val latestSyncHistoryId = usageEventDao
            .latestSuccessfulSyncHistoryIdThrough(rangeEnd)
            ?: 0L
        val cacheIdentityMatches = existingState != null &&
            existingState.calibrationKey == calibrationKey &&
            existingState.zoneId == zoneId.id &&
            existingState.aggregationVersion == AGGREGATION_VERSION
        if (
            cacheIdentityMatches &&
            existingState.lastAggregatedThroughMillis == rangeEnd &&
            existingState.checkpointRevision == checkpointRevision &&
            existingState.lastAggregatedSyncHistoryId == latestSyncHistoryId
        ) {
            return summaryDao.loadAll().map(DailyPostureSummaryEntity::toModel)
        }

        val fullRebuild = existingState == null ||
            !cacheIdentityMatches ||
            existingState.lastAggregatedThroughMillis > rangeEnd
        val earliestEvent = usageEventDao.earliestDeviceEventTimestamp(
            StoredUsageEventTypes.all,
        )
        val earliestCheckpoint = checkpointDao.earliestTimestamp()
        val earliestDeviceStateCheckpoint =
            usageEventDao.earliestDeviceStateCheckpointTimestamp()
        val earliestGap = collectionGapStarts.asSequence()
            .filter { it < rangeEnd }
            .minOrNull()
        val earliestEvidence = listOfNotNull(
            earliestEvent,
            earliestCheckpoint,
            earliestDeviceStateCheckpoint,
            earliestGap,
        ).minOrNull()
        val checkpointChanged = existingState?.checkpointRevision != checkpointRevision
        val latestCheckpoint = if (checkpointChanged) checkpointDao.latestTimestamp() else null
        val earliestInterveningSyncQueryBegin = existingState?.let { state ->
            usageEventDao.earliestSuccessfulSyncQueryBeginAfter(
                afterHistoryId = state.lastAggregatedSyncHistoryId,
                throughHistoryId = latestSyncHistoryId,
                syncedThroughMillis = rangeEnd,
            )
        }
        val earliestDirtySourceMillis = listOfNotNull(
            syncQueryBeginMillis.takeIf { it < rangeEnd },
            earliestInterveningSyncQueryBegin,
        ).minOrNull() ?: syncQueryBeginMillis
        val plannedRebuildStart = chooseDailySummaryRebuildStart(
            fullRebuild = fullRebuild,
            earliestEvidenceMillis = earliestEvidence,
            previousAggregatedThroughMillis = existingState?.lastAggregatedThroughMillis,
            syncedThroughMillis = rangeEnd,
            earliestDirtySourceMillis = earliestDirtySourceMillis,
            checkpointChanged = checkpointChanged,
            latestCheckpointMillis = latestCheckpoint,
            zoneId = zoneId,
        )
        val rebuildStart = if (fullRebuild || plannedRebuildStart == null) {
            plannedRebuildStart
        } else {
            val safeSessionStart = minOf(
                plannedRebuildStart,
                summaryDao.earliestInnerSessionStartOverlapping(plannedRebuildStart)
                    ?: plannedRebuildStart,
            )
            Instant.ofEpochMilli(safeSessionStart)
                .atZone(zoneId)
                .toLocalDate()
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        }
        val state = DailySummaryStateEntity(
            lastAggregatedThroughMillis = rangeEnd,
            calibrationKey = calibrationKey,
            zoneId = zoneId.id,
            checkpointRevision = checkpointRevision,
            aggregationVersion = AGGREGATION_VERSION,
            lastAggregatedSyncHistoryId = latestSyncHistoryId,
        )

        if (rebuildStart == null) {
            if (fullRebuild) {
                summaryDao.replaceAll(emptyList(), emptyList(), emptyList(), state)
            } else {
                summaryDao.upsertState(state)
            }
            return summaryDao.loadAll().map(DailyPostureSummaryEntity::toModel)
        }

        val rebuilt = analyzeInChunks(
            rangeStartMillis = rebuildStart,
            rangeEndMillis = rangeEnd,
            calibration = calibration,
            zoneId = zoneId,
            collectionGapStarts = collectionGapStarts,
        )
        if (fullRebuild) {
            summaryDao.replaceAll(
                summaries = rebuilt.posture.map(DailyPostureSummary::toEntity),
                appUsage = rebuilt.appUsage.map { it.toEntity() },
                innerSessions = rebuilt.innerSessions.map(InnerDisplaySession::toEntity),
                innerSessionAppUsages = rebuilt.innerSessionAppUsages,
                state = state,
            )
        } else {
            summaryDao.replaceFrom(
                beginMillis = rebuildStart,
                summaries = rebuilt.posture.map(DailyPostureSummary::toEntity),
                appUsage = rebuilt.appUsage.map { it.toEntity() },
                innerSessions = rebuilt.innerSessions.map(InnerDisplaySession::toEntity),
                innerSessionAppUsages = rebuilt.innerSessionAppUsages,
                state = state,
            )
        }
        return summaryDao.loadAll().map(DailyPostureSummaryEntity::toModel)
    }

    private suspend fun analyzeInChunks(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        calibration: Calibration,
        zoneId: ZoneId,
        collectionGapStarts: List<Long>,
    ): RebuiltDailySummaries {
        if (rangeStartMillis >= rangeEndMillis) return RebuiltDailySummaries()
        val orderedGapStarts = collectionGapStarts
            .asSequence()
            .filter { it < rangeEndMillis }
            .distinct()
            .sorted()
            .toList()
        val summaries = mutableListOf<DailyPostureSummary>()
        val appUsage = mutableListOf<DailyAppUsageSummary>()
        val sessionAnalyzer = InnerDisplaySessionAnalyzer(
            calibration = calibration,
            analysisStartMillis = rangeStartMillis,
        )
        var chunkStart = rangeStartMillis
        var firstChunk = true
        while (chunkStart < rangeEndMillis) {
            val chunkStartDate = Instant.ofEpochMilli(chunkStart).atZone(zoneId).toLocalDate()
            val chunkEnd = minOf(
                rangeEndMillis,
                chunkStartDate.plusDays(AGGREGATION_CHUNK_DAYS)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            )
            check(chunkEnd > chunkStart) { "Daily aggregation chunk did not advance" }

            val records = usageEventDao.loadUsageEventsForAnalysis(chunkStart, chunkEnd)
                .map(UsageEventEntity::toModel)
            val currentRecords = records.filter { it.timestampMillis >= chunkStart }
            val checkpoints = checkpointDao.loadForAnalysis(chunkStart, chunkEnd)
                .map(PostureCheckpointEntity::toModel)
            val currentCheckpoints = checkpoints.filter { it.timestampMillis >= chunkStart }
            val deviceStateCheckpoints =
                usageEventDao.loadDeviceStateCheckpointsForAnalysis(chunkStart, chunkEnd)
                    .mapNotNull(SyncHistoryEntity::toDeviceStateCheckpoint)
            val currentDeviceStateCheckpoints = deviceStateCheckpoints.filter {
                it.observedAtMillis >= chunkStart
            }
            val currentGaps = orderedGapStarts.filter { it in chunkStart until chunkEnd }
            val gaps = buildList {
                orderedGapStarts.lastOrNull { it < chunkStart }?.let(::add)
                addAll(currentGaps)
            }
            val analysis = analyzer.analyze(
                records = records,
                rangeStartMillis = chunkStart,
                rangeEndMillis = chunkEnd,
                calibration = calibration,
                checkpoints = checkpoints,
                zoneId = zoneId,
                collectionGapStarts = gaps,
                deviceStateCheckpoints = deviceStateCheckpoints,
            )
            summaries += includeRecordedZeroUsageDays(
                summaries = analysis.dailySummaries,
                rangeStartMillis = chunkStart,
                rangeEndMillis = chunkEnd,
                zoneId = zoneId,
            )
            appUsage += analysis.dailyAppSummaries
            sessionAnalyzer.processChunk(
                records = if (firstChunk) {
                    records
                } else {
                    currentRecords
                },
                checkpoints = if (firstChunk) checkpoints else currentCheckpoints,
                deviceStateCheckpoints = if (firstChunk) {
                    deviceStateCheckpoints
                } else {
                    currentDeviceStateCheckpoints
                },
                collectionGapStarts = if (firstChunk) gaps else currentGaps,
                chunkEndMillis = chunkEnd,
            )
            firstChunk = false
            chunkStart = chunkEnd
        }
        val innerSessions = sessionAnalyzer.sessionsAtEnd()
        return RebuiltDailySummaries(
            posture = summaries,
            appUsage = appUsage,
            innerSessions = innerSessions,
            innerSessionAppUsages = innerSessions.flatMap(
                InnerDisplaySession::toAppUsageEntities,
            ),
        )
    }

    private companion object {
        const val AGGREGATION_VERSION = 10
        const val AGGREGATION_CHUNK_DAYS = 93L
    }

    private data class RebuiltDailySummaries(
        val posture: List<DailyPostureSummary> = emptyList(),
        val appUsage: List<DailyAppUsageSummary> = emptyList(),
        val innerSessions: List<InnerDisplaySession> = emptyList(),
        val innerSessionAppUsages: List<InnerDisplaySessionAppUsageEntity> = emptyList(),
    )
}

/** Keep recorded, inactive days in the cache, including days before the first usage interval. */
internal fun includeRecordedZeroUsageDays(
    summaries: List<DailyPostureSummary>,
    rangeStartMillis: Long,
    rangeEndMillis: Long,
    zoneId: ZoneId,
): List<DailyPostureSummary> {
    if (rangeStartMillis >= rangeEndMillis) return emptyList()
    val summariesByDay = summaries.associateBy(DailyPostureSummary::dayStartMillis)
    return buildList {
        var date = Instant.ofEpochMilli(rangeStartMillis).atZone(zoneId).toLocalDate()
        while (true) {
            val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            if (dayStart >= rangeEndMillis) break
            val dayEnd = date.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
            add(
                summariesByDay[dayStart] ?: DailyPostureSummary(
                    dayStartMillis = dayStart,
                    dayEndMillis = dayEnd,
                    zoneId = zoneId.id,
                    coverMillis = 0L,
                    innerMillis = 0L,
                    excludedMillis = 0L,
                    openedCount = 0,
                    closedCount = 0,
                    evidenceGapCount = 0,
                ),
            )
            date = date.plusDays(1L)
        }
    }
}

class DailySummarySnapshot internal constructor(
    val dailySummaries: List<DailyPostureSummary>,
    private val summaryDao: DailyPostureSummaryDao,
) {
    suspend fun loadAggregatedAppUsage(
        beginMillis: Long,
        endMillis: Long,
    ): List<AggregatedAppUsage> =
        summaryDao.loadAggregatedAppUsage(beginMillis, endMillis)

    suspend fun loadCompleteInnerSessions(
        beginMillis: Long,
        endMillis: Long,
    ): List<InnerDisplaySession> {
        val sessions = summaryDao.loadCompleteInnerSessions(beginMillis, endMillis)
        if (sessions.isEmpty()) return emptyList()
        val appUsages = summaryDao.loadCompleteInnerSessionAppUsages(beginMillis, endMillis)
            .groupBy { it.openedAtMillis to it.openedSequenceAtTimestamp }
        return sessions.map { session ->
            session.toModel(
                appUsages = appUsages[
                    session.openedAtMillis to session.openedSequenceAtTimestamp
                ].orEmpty(),
            )
        }
    }
}

internal fun Calibration.dailySummaryCacheKey(): String =
    if (validationFailure == CalibrationValidationFailure.ANCHORS_TOO_CLOSE) {
        "cover=none|inner=none"
    } else {
        "cover=${cover.cacheKeyPart()}|inner=${inner.cacheKeyPart()}"
    }

private fun DisplayConfiguration?.cacheKeyPart(): String = this?.let {
    listOf(
        it.screenWidthDp,
        it.screenHeightDp,
        it.smallestScreenWidthDp,
        it.orientation,
        it.densityDpi,
    ).joinToString(separator = ",")
} ?: "none"

internal fun chooseDailySummaryRebuildStart(
    fullRebuild: Boolean,
    earliestEvidenceMillis: Long?,
    previousAggregatedThroughMillis: Long?,
    syncedThroughMillis: Long,
    earliestDirtySourceMillis: Long,
    checkpointChanged: Boolean,
    latestCheckpointMillis: Long?,
    zoneId: ZoneId,
): Long? {
    val earliestEvidence = earliestEvidenceMillis ?: return null
    if (syncedThroughMillis <= earliestEvidence) return null
    val dirtyMillis = if (fullRebuild) {
        earliestEvidence
    } else {
        listOfNotNull(
            previousAggregatedThroughMillis,
            earliestDirtySourceMillis.takeIf { it < syncedThroughMillis },
            latestCheckpointMillis?.takeIf {
                checkpointChanged && it < syncedThroughMillis
            },
        ).minOrNull() ?: return null
    }
    val boundedDirtyMillis = maxOf(
        earliestEvidence,
        dirtyMillis.coerceAtMost(syncedThroughMillis - 1L),
    )
    return Instant.ofEpochMilli(boundedDirtyMillis)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
}
