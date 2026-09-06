package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.Calibration
import com.nagopy.android.foldlytics.model.InnerDisplaySession
import com.nagopy.android.foldlytics.model.PostureCheckpoint
import com.nagopy.android.foldlytics.model.UsageRecord

/** Reuses the session state machine; the cache remains authoritative for selection and totals. */
internal fun replaySelectedInnerSessions(
    selectedSessions: List<InnerDisplaySession>,
    records: List<UsageRecord>,
    checkpoints: List<PostureCheckpoint>,
    deviceStateCheckpoints: List<DeviceStateCheckpoint>,
    collectionGapStarts: List<Long>,
    calibration: Calibration,
): List<InnerDisplaySession> {
    if (selectedSessions.isEmpty()) return emptyList()
    val end = selectedSessions.mapNotNull { it.closedAtMillis }.maxOrNull()
        ?: return selectedSessions.map { it.copy(appSetUsageMillis = emptyMap()) }
    val analyzer = InnerDisplaySessionAnalyzer(
        calibration = calibration,
        analysisStartMillis = selectedSessions.minOf { it.openedAtMillis },
        captureAppSets = true,
        sessionKeysToInclude = selectedSessions.mapTo(mutableSetOf()) {
            it.openedAtMillis to it.openedSequenceAtTimestamp
        },
    )
    analyzer.processChunk(
        records = records,
        checkpoints = checkpoints,
        deviceStateCheckpoints = deviceStateCheckpoints,
        collectionGapStarts = collectionGapStarts,
        // Include the closing event, while preserving its same-time event order.
        chunkEndMillis = if (end == Long.MAX_VALUE) end else end + 1L,
    )
    val replayed = analyzer.sessionsAtEnd().associateBy {
        it.openedAtMillis to it.openedSequenceAtTimestamp
    }
    return selectedSessions.map { cached ->
        val replay = replayed[cached.openedAtMillis to cached.openedSequenceAtTimestamp]
        cached.copy(
            appSetUsageMillis = replay?.takeIf {
                it.closedAtMillis == cached.closedAtMillis &&
                    it.innerActiveMillis == cached.innerActiveMillis
            }?.appSetUsageMillis.orEmpty(),
        )
    }
}
