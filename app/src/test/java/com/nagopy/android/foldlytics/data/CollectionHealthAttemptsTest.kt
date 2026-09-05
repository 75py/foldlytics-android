package com.nagopy.android.foldlytics.data

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionHealthAttemptsTest {
    private val analyzer = LongTermAnalyzer()

    @Test
    fun currentPeriodIncludesFailureAfterLatestSuccessfulSync() {
        val successAt = TimeUnit.MINUTES.toMillis(30)
        val failureAt = TimeUnit.HOURS.toMillis(12) + TimeUnit.MINUTES.toMillis(30)
        val attempts = listOf(
            attempt(successAt, SyncAttemptStatus.SUCCESS),
            attempt(failureAt, SyncAttemptStatus.FAILED),
        )

        val health = analyzer.collectionHealth(
            collectionHealthAttemptsForRange(
                attempts = attempts,
                rangeStartMillis = 0L,
                rangeEndMillis = successAt,
                currentMillis = failureAt,
                isCustomRange = false,
            ),
        )

        assertEquals(2, health.recordedAttemptCount)
        assertEquals(1, health.unsuccessfulAttemptCount)
    }

    @Test
    fun historicalCustomPeriodExcludesLaterFailure() {
        val successAt = TimeUnit.MINUTES.toMillis(30)
        val failureAt = TimeUnit.HOURS.toMillis(12) + TimeUnit.MINUTES.toMillis(30)
        val attempts = listOf(
            attempt(successAt, SyncAttemptStatus.SUCCESS),
            attempt(failureAt, SyncAttemptStatus.FAILED),
        )

        val health = analyzer.collectionHealth(
            collectionHealthAttemptsForRange(
                attempts = attempts,
                rangeStartMillis = 0L,
                rangeEndMillis = successAt,
                currentMillis = failureAt,
                isCustomRange = true,
            ),
        )

        assertEquals(1, health.recordedAttemptCount)
        assertEquals(0, health.unsuccessfulAttemptCount)
    }

    @Test
    fun firstFailureIsRepresentedWithoutSuccessfulSync() {
        val health = analyzer.collectionHealth(
            collectionHealthAttemptsForRange(
                attempts = listOf(attempt(1L, SyncAttemptStatus.USER_LOCKED)),
                rangeStartMillis = 0L,
                rangeEndMillis = 0L,
                currentMillis = 1L,
                isCustomRange = false,
            ),
        )

        assertEquals(1, health.recordedAttemptCount)
        assertEquals(1, health.unsuccessfulAttemptCount)
        assertNull(health.longestSuccessfulSyncGapMillis)
    }

    private fun attempt(timeMillis: Long, status: SyncAttemptStatus) = SyncAttempt(
        attemptedAtMillis = timeMillis,
        queryBeginMillis = 0L,
        queryEndMillis = timeMillis,
        status = status,
        readEventCount = 0,
    )
}
