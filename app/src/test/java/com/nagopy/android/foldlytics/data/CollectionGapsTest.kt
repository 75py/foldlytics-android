package com.nagopy.android.foldlytics.data

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionGapsTest {
    @Test
    fun doesNotResetStateForNormalPeriodicSyncIntervals() {
        val attempts = listOf(
            success(endHours = 6, queryBeginHours = 0),
            success(endHours = 12, queryBeginHours = 5),
            success(endHours = 18, queryBeginHours = 11),
        )

        assertTrue(detectCollectionGaps(attempts).isEmpty())
    }

    @Test
    fun detectsLongIntervalFromSuccessHistory() {
        val attempts = listOf(
            success(endHours = 6, queryBeginHours = 0),
            success(endHours = 31, queryBeginHours = 5),
        )

        val gap = detectCollectionGaps(attempts).single()

        assertEquals(TimeUnit.HOURS.toMillis(6), gap.startMillis)
        assertEquals(TimeUnit.HOURS.toMillis(31), gap.endMillis)
    }

    @Test
    fun detectsUpgradeBoundaryFromFirstRecordedSuccessQuery() {
        val attempts = listOf(
            success(endHours = 72, queryBeginHours = 23),
        )

        val gap = detectCollectionGaps(attempts).single()

        assertEquals(TimeUnit.HOURS.toMillis(24), gap.startMillis)
        assertEquals(TimeUnit.HOURS.toMillis(72), gap.endMillis)
    }

    private fun success(endHours: Long, queryBeginHours: Long) = SyncAttempt(
        attemptedAtMillis = TimeUnit.HOURS.toMillis(endHours),
        queryBeginMillis = TimeUnit.HOURS.toMillis(queryBeginHours),
        queryEndMillis = TimeUnit.HOURS.toMillis(endHours),
        status = SyncAttemptStatus.SUCCESS,
        readEventCount = 0,
    )
}
