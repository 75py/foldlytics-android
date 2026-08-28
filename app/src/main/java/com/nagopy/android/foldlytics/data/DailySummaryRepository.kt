package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DailyAppUsageSummary
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import java.time.Instant
import java.time.ZoneId

class DailySummaryRepository(
    private val usageEventDao: UsageEventDao,
    private val checkpointDao: PostureCheckpointDao,
    private val summaryDao: DailyPostureSummaryDao,
) {
    private val analyzer = UsageAnalyzer { packageName -> packageName }

    suspend fun loadAggregatedAppUsage(
        beginMillis: Long,
        endMillis: Long,
    ): List<AggregatedAppUsage> =
        summaryDao.loadAggregatedAppUsage(beginMillis, endMillis)

    suspend fun loadCompleteInnerSessions(
        beginMillis: Long,
        endMillis: Long,
    ): List<InnerDisplaySession> =
        summaryDao.loadCompleteInnerSessions(beginMillis, endMillis)
            .map(InnerDisplaySessionEntity::toModel)

    suspend fun ensureUpToDate(
        calibration: Calibration,
        syncedThroughMillis: Long,
        syncQueryBeginMillis: Long,
        checkpointRevision: Long,
        zoneId: ZoneId,
        collectionGapStarts: List<Long>,
    ): List<DailyPostureSummary> {
        val rangeEnd = syncedThroughMillis.coerceAtLeast(0L)
        val calibrationKey = calibration.cacheKey()
        val existingState = summaryDao.loadState()
        val cacheIdentityMatches = existingState != null &&
            existingState.calibrationKey == calibrationKey &&
            existingState.zoneId == zoneId.id &&
            existingState.aggregationVersion == AGGREGATION_VERSION
        if (
            cacheIdentityMatches &&
            existingState.lastAggregatedThroughMillis == rangeEnd &&
            existingState.checkpointRevision == checkpointRevision
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
        val plannedRebuildStart = chooseDailySummaryRebuildStart(
            fullRebuild = fullRebuild,
            earliestEvidenceMillis = earliestEvidence,
            previousAggregatedThroughMillis = existingState?.lastAggregatedThroughMillis,
            syncedThroughMillis = rangeEnd,
            syncQueryBeginMillis = syncQueryBeginMillis,
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
                state = state,
            )
        } else {
            summaryDao.replaceFrom(
                beginMillis = rebuildStart,
                summaries = rebuilt.posture.map(DailyPostureSummary::toEntity),
                appUsage = rebuilt.appUsage.map { it.toEntity() },
                innerSessions = rebuilt.innerSessions.map(InnerDisplaySession::toEntity),
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
            val currentCheckpoints = checkpointDao.load(chunkStart, chunkEnd)
                .map(PostureCheckpointEntity::toModel)
            val checkpoints = buildList {
                checkpointDao.latestBefore(chunkStart)?.let { add(it.toModel()) }
                addAll(currentCheckpoints)
            }
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
            summaries += analysis.dailySummaries
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
        return RebuiltDailySummaries(
            posture = summaries,
            appUsage = appUsage,
            innerSessions = sessionAnalyzer.sessionsAtEnd(),
        )
    }

    private fun Calibration.cacheKey(): String =
        "cover=${cover.cacheKeyPart()}|inner=${inner.cacheKeyPart()}"

    private fun DisplayConfiguration?.cacheKeyPart(): String = this?.let {
        listOf(
            it.screenWidthDp,
            it.screenHeightDp,
            it.smallestScreenWidthDp,
            it.orientation,
            it.densityDpi,
        ).joinToString(separator = ",")
    } ?: "none"

    private companion object {
        const val AGGREGATION_VERSION = 3
        const val AGGREGATION_CHUNK_DAYS = 31L
    }

    private data class RebuiltDailySummaries(
        val posture: List<DailyPostureSummary> = emptyList(),
        val appUsage: List<DailyAppUsageSummary> = emptyList(),
        val innerSessions: List<InnerDisplaySession> = emptyList(),
    )
}

internal fun chooseDailySummaryRebuildStart(
    fullRebuild: Boolean,
    earliestEvidenceMillis: Long?,
    previousAggregatedThroughMillis: Long?,
    syncedThroughMillis: Long,
    syncQueryBeginMillis: Long,
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
            syncQueryBeginMillis.takeIf { it < syncedThroughMillis },
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
