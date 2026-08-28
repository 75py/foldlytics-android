package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseModelMappingTest {
    @Test
    fun dailySummaryRoundTripsThroughRoomEntity() {
        val summary = DailyPostureSummary(
            dayStartMillis = 1_000L,
            dayEndMillis = 2_000L,
            zoneId = "Asia/Tokyo",
            coverMillis = 100L,
            innerMillis = 200L,
            excludedMillis = 30L,
            openedCount = 4,
            closedCount = 3,
            evidenceGapCount = 1,
        )

        assertEquals(summary, summary.toEntity().toModel())
    }

    @Test
    fun syncAttemptRoundTripsThroughRoomEntity() {
        val attempt = SyncAttempt(
            attemptedAtMillis = 3_000L,
            queryBeginMillis = 1_000L,
            queryEndMillis = 3_000L,
            status = SyncAttemptStatus.SUCCESS,
            readEventCount = 12,
            insertedEventCount = 5,
            deviceStateCheckpoint = DeviceStateCheckpoint(
                observedAtMillis = 2_900L,
                screenInteractive = true,
                keyguardHidden = false,
            ),
        )

        assertEquals(attempt, attempt.toEntity().toModel())
    }

    @Test
    fun legacySyncAttemptWithoutDeviceStateStillRoundTrips() {
        val entity = SyncHistoryEntity(
            attemptedAtMillis = 3_000L,
            queryBeginMillis = 1_000L,
            queryEndMillis = 3_000L,
            status = SyncAttemptStatus.SUCCESS.name,
            readEventCount = 12,
            insertedEventCount = 5,
        )

        assertEquals(null, entity.toModel().deviceStateCheckpoint)
    }
}
