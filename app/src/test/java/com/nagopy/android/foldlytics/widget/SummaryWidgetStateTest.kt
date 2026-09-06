package com.nagopy.android.foldlytics.widget

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryWidgetStateTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 3, 9)
    private val through = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun includesSevenCalendarDaysAcrossDaylightSavingBoundary() {
        val state = state(WidgetPeriod.DAYS_7, (0L..7L).map { day -> summary(today.minusDays(day)) })
        assertEquals(today.minusDays(6), state.dateRange.start)
        assertEquals(700L, state.innerMillis)
        assertEquals(21, state.openedCount)
    }

    @Test
    fun thirtyDaysExcludeThePreviousDay() {
        val state = state(WidgetPeriod.DAYS_30, (0L..30L).map { summary(today.minusDays(it)) })
        assertEquals(today.minusDays(29), state.dateRange.start)
        assertEquals(3_000L, state.innerMillis)
    }

    @Test
    fun midnightSyncEndsOnThePreviousCalendarDay() {
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val range = widgetDateRange(WidgetPeriod.TODAY, midnight, zone)
        assertEquals(today.minusDays(1), range.start)
        assertEquals(range.start, range.endInclusive)
    }

    @Test
    fun oldSyncKeepsItsAbsoluteDateAfterMidnight() {
        val state = buildSummaryWidgetState(
            WidgetPeriod.TODAY, listOf(summary(today)), through, through,
            hasPermission = true, updateFailed = false, nowMillis = through + 86_400_000L, zoneId = zone,
        )
        assertEquals(today, state.dateRange.endInclusive)
        assertEquals(100L, state.innerMillis)
    }

    @Test
    fun emptyAndUnclassifiedUsageDoNotBecomeZeroPercent() {
        assertNull(state(WidgetPeriod.TODAY, emptyList()).innerRatio)
        assertFalse(state(WidgetPeriod.TODAY, emptyList()).hasRecordedEvidence)
        val unclassified = summary(today).copy(innerMillis = 0, coverMillis = 0, excludedMillis = 500)
        assertEquals(WidgetStatus.NO_DATA, state(WidgetPeriod.TODAY, listOf(unclassified)).status)
        assertNull(state(WidgetPeriod.TODAY, listOf(unclassified)).innerRatio)
        assertTrue(state(WidgetPeriod.TODAY, listOf(unclassified)).hasRecordedEvidence)
    }

    @Test
    fun outerOnlyUsageIsARealZeroPercent() {
        val state = state(WidgetPeriod.TODAY, listOf(summary(today).copy(innerMillis = 0)))
        assertEquals(0f, state.innerRatio)
        assertEquals(WidgetStatus.READY, state.status)
    }

    @Test
    fun missingPermissionRedactsSavedMetrics() {
        val state = state(WidgetPeriod.DAYS_7, listOf(summary(today)), permission = false)
        assertNull(state.innerRatio)
        assertEquals(0, state.openedCount)
        assertEquals(0L, state.coverMillis)
        assertEquals(WidgetStatus.PERMISSION_REQUIRED, state.status)
    }

    @Test
    fun failedRefreshKeepsSavedMetricsAndLastSyncWithFailureState() {
        val state = state(WidgetPeriod.DAYS_7, listOf(summary(today)), failed = true)
        assertEquals(0.25f, state.innerRatio)
        assertEquals(through, state.lastSyncMillis)
        assertEquals(WidgetStatus.UPDATE_FAILED, state.status)
    }

    @Test
    fun invalidOrMissingPeriodDefaultsToSevenDays() {
        assertEquals(WidgetPeriod.DAYS_7, WidgetPeriod.fromName(null))
        assertEquals(WidgetPeriod.DAYS_7, WidgetPeriod.fromName("invalid"))
    }

    private fun state(
        period: WidgetPeriod,
        summaries: List<DailyPostureSummary>,
        permission: Boolean = true,
        failed: Boolean = false,
    ) = buildSummaryWidgetState(period, summaries, through, through, permission, failed, through, zone)

    private fun summary(date: LocalDate) = DailyPostureSummary(
        dayStartMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        dayEndMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        zoneId = zone.id,
        coverMillis = 300,
        innerMillis = 100,
        excludedMillis = 0,
        openedCount = 3,
        closedCount = 2,
        evidenceGapCount = 0,
    )
}
