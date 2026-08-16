package com.nagopy.android.foldlytics.data

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAnalysisWindowTest {
    @Test
    fun endsAtLastSuccessfulSyncInsteadOfCurrentWallClock() {
        val syncedThrough = TimeUnit.DAYS.toMillis(10)

        val window = createUsageAnalysisWindow(
            periodHours = 24,
            syncedThroughMillis = syncedThrough,
        )

        assertEquals(syncedThrough, window.rangeEndMillis)
        assertEquals(syncedThrough - TimeUnit.HOURS.toMillis(24), window.rangeStartMillis)
        assertEquals(syncedThrough - TimeUnit.HOURS.toMillis(96), window.seedStartMillis)
    }

    @Test
    fun clampsWindowAtUnixEpoch() {
        val window = createUsageAnalysisWindow(
            periodHours = 24,
            syncedThroughMillis = TimeUnit.HOURS.toMillis(12),
        )

        assertEquals(0L, window.rangeStartMillis)
        assertEquals(0L, window.seedStartMillis)
    }
}
