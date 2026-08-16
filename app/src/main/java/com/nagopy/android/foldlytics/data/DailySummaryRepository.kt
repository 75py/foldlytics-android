package com.nagopy.android.foldlytics.data

import android.app.usage.UsageEvents
import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.DailyAppUsageSummary
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayConfiguration
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
            DAILY_SUMMARY_EVENT_TYPES,
        )
        val earliestCheckpoint = checkpointDao.earliestTimestamp()
        val earliestGap = collectionGapStarts.asSequence()
            .filter { it < rangeEnd }
            .minOrNull()
        val earliestEvidence = listOfNotNull(
            earliestEvent,
            earliestCheckpoint,
            earliestGap,
        ).minOrNull()
        val checkpointChanged = existingState?.checkpointRevision != checkpointRevision
        val latestCheckpoint = if (checkpointChanged) checkpointDao.latestTimestamp() else null
        val rebuildStart = chooseDailySummaryRebuildStart(
            fullRebuild = fullRebuild,
            earliestEvidenceMillis = earliestEvidence,
            previousAggregatedThroughMillis = existingState?.lastAggregatedThroughMillis,
            syncedThroughMillis = rangeEnd,
            syncQueryBeginMillis = syncQueryBeginMillis,
            checkpointChanged = checkpointChanged,
            latestCheckpointMillis = latestCheckpoint,
            zoneId = zoneId,
        )
        val state = DailySummaryStateEntity(
            lastAggregatedThroughMillis = rangeEnd,
            calibrationKey = calibrationKey,
            zoneId = zoneId.id,
            checkpointRevision = checkpointRevision,
            aggregationVersion = AGGREGATION_VERSION,
        )

        if (rebuildStart == null) {
            if (fullRebuild) {
                summaryDao.replaceAll(emptyList(), emptyList(), state)
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
                state = state,
            )
        } else {
            summaryDao.replaceFrom(
                beginMillis = rebuildStart,
                summaries = rebuilt.posture.map(DailyPostureSummary::toEntity),
                appUsage = rebuilt.appUsage.map { it.toEntity() },
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
        var chunkStart = rangeStartMillis
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

            val seedRecords = mutableListOf<UsageEventEntity>()
            DEVICE_STATE_EVENT_GROUPS.forEach { eventTypes ->
                seedRecords += usageEventDao.loadLatestDeviceEventsBefore(
                    endMillis = chunkStart,
                    rawEventTypes = eventTypes,
                )
            }
            seedRecords += usageEventDao.loadLatestActivityEventsBefore(
                endMillis = chunkStart,
                rawEventTypes = ACTIVITY_EVENT_TYPES,
            )
            val records = (
                seedRecords.distinctBy(UsageEventEntity::eventKey) +
                    usageEventDao.loadDeviceEvents(
                        beginMillis = chunkStart,
                        endMillis = chunkEnd,
                        rawEventTypes = DAILY_SUMMARY_EVENT_TYPES,
                    )
                ).map(UsageEventEntity::toModel)
            val checkpoints = buildList {
                checkpointDao.latestBefore(chunkStart)?.let { add(it.toModel()) }
                addAll(
                    checkpointDao.load(chunkStart, chunkEnd)
                        .map(PostureCheckpointEntity::toModel),
                )
            }
            val gaps = buildList {
                orderedGapStarts.lastOrNull { it < chunkStart }?.let(::add)
                addAll(orderedGapStarts.filter { it in chunkStart until chunkEnd })
            }
            val analysis = analyzer.analyze(
                records = records,
                rangeStartMillis = chunkStart,
                rangeEndMillis = chunkEnd,
                calibration = calibration,
                checkpoints = checkpoints,
                zoneId = zoneId,
                collectionGapStarts = gaps,
            )
            summaries += analysis.dailySummaries
            appUsage += analysis.dailyAppSummaries
            chunkStart = chunkEnd
        }
        return RebuiltDailySummaries(posture = summaries, appUsage = appUsage)
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
        const val AGGREGATION_VERSION = 1
        const val AGGREGATION_CHUNK_DAYS = 31L

        val SCREEN_STATE_EVENT_TYPES = listOf(
            UsageEvents.Event.SCREEN_INTERACTIVE,
            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        )
        val KEYGUARD_STATE_EVENT_TYPES = listOf(
            UsageEvents.Event.KEYGUARD_SHOWN,
            UsageEvents.Event.KEYGUARD_HIDDEN,
        )
        val POSTURE_STATE_EVENT_TYPES = listOf(
            UsageEvents.Event.CONFIGURATION_CHANGE,
            UsageEvents.Event.DEVICE_STARTUP,
            UsageEvents.Event.DEVICE_SHUTDOWN,
        )
        val ACTIVITY_EVENT_TYPES = listOf(
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED,
        )
        val DEVICE_STATE_EVENT_GROUPS = listOf(
            SCREEN_STATE_EVENT_TYPES,
            KEYGUARD_STATE_EVENT_TYPES,
            POSTURE_STATE_EVENT_TYPES,
        )
        val DAILY_SUMMARY_EVENT_TYPES =
            (DEVICE_STATE_EVENT_GROUPS.flatten() + ACTIVITY_EVENT_TYPES).distinct()
    }

    private data class RebuiltDailySummaries(
        val posture: List<DailyPostureSummary> = emptyList(),
        val appUsage: List<DailyAppUsageSummary> = emptyList(),
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
