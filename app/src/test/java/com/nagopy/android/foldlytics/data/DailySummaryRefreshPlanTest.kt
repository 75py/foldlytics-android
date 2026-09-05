package com.nagopy.android.foldlytics.data

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DailySummaryRefreshPlanTest {
    private val zoneId = ZoneOffset.UTC

    @Test
    fun normalSyncRebuildsOnlyTheOverlapDayInsteadOfThreeYears() {
        val earliest = Instant.parse("2023-01-01T12:00:00Z").toEpochMilli()
        val previousEnd = Instant.parse("2026-01-02T00:30:00Z").toEpochMilli()
        val queryBegin = Instant.parse("2026-01-01T23:30:00Z").toEpochMilli()
        val newEnd = Instant.parse("2026-01-02T06:30:00Z").toEpochMilli()

        val result = chooseDailySummaryRebuildStart(
            fullRebuild = false,
            earliestEvidenceMillis = earliest,
            previousAggregatedThroughMillis = previousEnd,
            syncedThroughMillis = newEnd,
            earliestDirtySourceMillis = queryBegin,
            checkpointChanged = false,
            latestCheckpointMillis = null,
            zoneId = zoneId,
        )

        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), result)
    }

    @Test
    fun changedHistoricalCheckpointRebuildsFromItsDayForward() {
        val result = chooseDailySummaryRebuildStart(
            fullRebuild = false,
            earliestEvidenceMillis = Instant.parse("2023-01-01T00:00:00Z").toEpochMilli(),
            previousAggregatedThroughMillis = Instant.parse("2026-01-03T00:00:00Z").toEpochMilli(),
            syncedThroughMillis = Instant.parse("2026-01-03T06:00:00Z").toEpochMilli(),
            earliestDirtySourceMillis = Instant.parse("2026-01-02T23:00:00Z").toEpochMilli(),
            checkpointChanged = true,
            latestCheckpointMillis = Instant.parse("2025-12-15T12:00:00Z").toEpochMilli(),
            zoneId = zoneId,
        )

        assertEquals(Instant.parse("2025-12-15T00:00:00Z").toEpochMilli(), result)
    }

    @Test
    fun calibrationChangeRebuildsFromEarliestEvidence() {
        val result = chooseDailySummaryRebuildStart(
            fullRebuild = true,
            earliestEvidenceMillis = Instant.parse("2023-01-01T12:00:00Z").toEpochMilli(),
            previousAggregatedThroughMillis = Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(),
            syncedThroughMillis = Instant.parse("2026-01-02T06:00:00Z").toEpochMilli(),
            earliestDirtySourceMillis = Instant.parse("2026-01-01T23:00:00Z").toEpochMilli(),
            checkpointChanged = false,
            latestCheckpointMillis = null,
            zoneId = zoneId,
        )

        assertEquals(Instant.parse("2023-01-01T00:00:00Z").toEpochMilli(), result)
    }

    @Test
    fun multipleInterveningSyncsRebuildFromTheEarliestOverlap() {
        val result = chooseDailySummaryRebuildStart(
            fullRebuild = false,
            earliestEvidenceMillis = Instant.parse("2023-01-01T00:00:00Z").toEpochMilli(),
            previousAggregatedThroughMillis =
                Instant.parse("2026-01-02T00:30:00Z").toEpochMilli(),
            syncedThroughMillis = Instant.parse("2026-01-02T12:30:00Z").toEpochMilli(),
            earliestDirtySourceMillis =
                Instant.parse("2026-01-01T23:30:00Z").toEpochMilli(),
            checkpointChanged = false,
            latestCheckpointMillis = null,
            zoneId = zoneId,
        )

        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), result)
    }
}
