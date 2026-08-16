package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.LongTermPeriod
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class LongTermAnalyzerTest {
    private val analyzer = LongTermAnalyzer()
    private val zoneId = ZoneOffset.UTC

    @Test
    fun aggregatesSelectedDaysAndBuildsDailyBuckets() {
        val endDate = LocalDate.of(2026, 1, 8)
        val summaries = (1..7).map { index ->
            summary(
                date = LocalDate.of(2026, 1, index),
                coverMillis = 1_000L,
                innerMillis = 2_000L,
                openedCount = index,
            )
        }

        val result = analyzer.analyze(
            summaries = summaries,
            period = LongTermPeriod.DAYS_7,
            rangeEndMillis = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            zoneId = zoneId,
        )

        assertEquals(7, result.buckets.size)
        assertEquals(7_000L, result.coverMillis)
        assertEquals(14_000L, result.innerMillis)
        assertEquals(28, result.openedCount)
        assertEquals(7, result.observedDayCount)
        assertEquals(7, result.innerUsedDayCount)
    }

    @Test
    fun groupsNinetyDaysIntoWeeklyBuckets() {
        val endDate = LocalDate.of(2026, 4, 1)

        val result = analyzer.analyze(
            summaries = emptyList(),
            period = LongTermPeriod.DAYS_90,
            rangeEndMillis = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            zoneId = zoneId,
        )

        assertEquals(13, result.buckets.size)
    }

    @Test
    fun aggregatesThreeYearsWithoutDurationOverflow() {
        val firstDate = LocalDate.of(2023, 1, 1)
        val summaries = (0L until 1_095L).map { offset ->
            summary(
                date = firstDate.plusDays(offset),
                coverMillis = 20_000_000L,
                innerMillis = 10_000_000L,
                openedCount = 1,
            )
        }
        val rangeEnd = firstDate.plusDays(1_095L)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val result = analyzer.analyze(
            summaries = summaries,
            period = LongTermPeriod.DAYS_1095,
            rangeEndMillis = rangeEnd,
            zoneId = zoneId,
        )

        assertEquals(1_095, result.calendarDayCount)
        assertEquals(1_095, result.observedDayCount)
        assertEquals(21_900_000_000L, result.coverMillis)
        assertEquals(10_950_000_000L, result.innerMillis)
        assertEquals(36, result.buckets.size)
    }

    @Test
    fun comparesFirstAndRecentThirtyDayInnerRatiosAfterSixtyDays() {
        val firstDate = LocalDate.of(2026, 1, 1)
        val summaries = (0L until 60L).map { offset ->
            val recent = offset >= 30L
            summary(
                date = firstDate.plusDays(offset),
                coverMillis = if (recent) 1_000L else 3_000L,
                innerMillis = if (recent) 3_000L else 1_000L,
                openedCount = 1,
            )
        }
        val rangeEnd = firstDate.plusDays(60)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val result = analyzer.analyze(
            summaries = summaries,
            period = LongTermPeriod.DAYS_90,
            rangeEndMillis = rangeEnd,
            zoneId = zoneId,
        )

        assertEquals(0.25f, result.firstThirtyDayInnerRatio ?: 0f, 0f)
        assertEquals(0.75f, result.recentThirtyDayInnerRatio ?: 0f, 0f)
        assertEquals(0.5f, result.thirtyDayInnerRatioDelta ?: 0f, 0f)
    }

    @Test
    fun analyzesOnlyTheSelectedCustomDateRange() {
        val firstDate = LocalDate.of(2026, 1, 1)
        val summaries = (0L until 60L).map { offset ->
            summary(
                date = firstDate.plusDays(offset),
                coverMillis = 1_000L,
                innerMillis = 2_000L,
                openedCount = 1,
            )
        }
        val selectedStart = firstDate.plusDays(10L)
        val selectedEndExclusive = firstDate.plusDays(50L)

        val result = analyzer.analyzeRange(
            summaries = summaries,
            rangeStartMillis = selectedStart.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            rangeEndMillis = selectedEndExclusive
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli(),
            zoneId = zoneId,
        )

        assertEquals(40, result.calendarDayCount)
        assertEquals(40, result.observedDayCount)
        assertEquals(40_000L, result.coverMillis)
        assertEquals(80_000L, result.innerMillis)
        assertEquals(6, result.buckets.size)
    }

    @Test
    fun reportsUnsuccessfulAttemptsAndLongestSuccessGap() {
        val attempts = listOf(
            attempt(0L, SyncAttemptStatus.SUCCESS),
            attempt(TimeUnit.HOURS.toMillis(6), SyncAttemptStatus.PERMISSION_DENIED),
            attempt(TimeUnit.HOURS.toMillis(18), SyncAttemptStatus.SUCCESS),
        )

        val result = analyzer.collectionHealth(attempts)

        assertEquals(3, result.recordedAttemptCount)
        assertEquals(1, result.unsuccessfulAttemptCount)
        assertEquals(TimeUnit.HOURS.toMillis(18), result.longestSuccessfulSyncGapMillis)
        assertEquals(0, result.collectionInterruptionCount)
    }

    private fun summary(
        date: LocalDate,
        coverMillis: Long,
        innerMillis: Long,
        openedCount: Int,
    ): DailyPostureSummary {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return DailyPostureSummary(
            dayStartMillis = start,
            dayEndMillis = end,
            zoneId = zoneId.id,
            coverMillis = coverMillis,
            innerMillis = innerMillis,
            excludedMillis = 0L,
            openedCount = openedCount,
            closedCount = openedCount,
            evidenceGapCount = 0,
        )
    }

    private fun attempt(time: Long, status: SyncAttemptStatus) = SyncAttempt(
        attemptedAtMillis = time,
        queryBeginMillis = 0L,
        queryEndMillis = time,
        status = status,
        readEventCount = 0,
    )
}
