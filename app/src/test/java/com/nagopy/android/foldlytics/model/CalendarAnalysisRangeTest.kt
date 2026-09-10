package com.nagopy.android.foldlytics.model

import com.nagopy.android.foldlytics.data.LongTermAnalyzer
import com.nagopy.android.foldlytics.widget.WidgetPeriod
import com.nagopy.android.foldlytics.widget.buildSummaryWidgetState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAnalysisRangeTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 3, 9)
    private val through = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun thirtyDaySelectionClipsToTenRecordedDaysIncludingInactiveStart() {
        val start = millis(today.minusDays(9))
        val range = resolveCalendarAnalysisRange(30, through, start, through, zone)

        assertEquals(millis(today.minusDays(29)), range.requestedStartMillis)
        assertEquals(start, range.dataRange?.startMillis)
        assertEquals(through, range.dataRange?.endMillis)
        assertEquals(10, range.recordedDayCount(zone))
    }

    @Test
    fun todayIsCalendarDayAndNotRollingTwentyFourHours() {
        val range = resolveCalendarAnalysisRange(1, through, millis(today.minusDays(9)), through, zone)

        assertEquals(millis(today), range.dataRange?.startMillis)
        assertEquals(through, range.dataRange?.endMillis)
        assertEquals(1, range.recordedDayCount(zone))
    }

    @Test
    fun calendarBoundariesFollowTimeZoneAndBothDaylightSavingChanges() {
        val spring = LocalDate.of(2026, 3, 8)
        val fall = LocalDate.of(2026, 11, 1)
        for ((date, hours) in listOf(spring to 23L, fall to 25L)) {
            val now = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val range = resolveCalendarAnalysisRange(1, now, millis(date.minusDays(1)), now, zone)
            assertEquals(hours * 3_600_000L, range.requestedEndMillis - range.requestedStartMillis)
            assertEquals(millis(date), range.requestedStartMillis)
        }
        val tokyo = ZoneId.of("Asia/Tokyo")
        val range = resolveCalendarAnalysisRange(1, through, null, null, tokyo)
        assertEquals(
            today.plusDays(1).atStartOfDay(tokyo).toInstant().toEpochMilli(),
            range.requestedStartMillis,
        )
    }

    @Test
    fun noHistoryAndStaleTodayHaveNoEffectiveDataRange() {
        assertNull(resolveCalendarAnalysisRange(30, through, null, through, zone).dataRange)
        assertNull(resolveCalendarAnalysisRange(30, through, millis(today), null, zone).dataRange)
        val staleRange = resolveCalendarAnalysisRange(
            1, through, millis(today.minusDays(3)), millis(today), zone,
        )
        assertNull(staleRange.dataRange)
        assertEquals(0, staleRange.recordedDayCount(zone))
        val insights = LongTermAnalyzer().analyzeCalendarRange(
            listOf(summary(today.minusDays(1))), staleRange, millis(today), zone,
        )
        assertEquals(0L, insights.innerMillis)
        assertEquals(insights.rangeStartMillis, insights.rangeEndMillis)
        assertTrue(insights.buckets.isEmpty())
    }

    @Test
    fun appAndWidgetUseIdenticalDailyTotalsAndEffectiveRange() {
        val summaries = (0L..9L).map { summary(today.minusDays(it)) }.mapIndexed { index, value ->
            if (index == 9) value.copy(innerMillis = 0L, coverMillis = 0L, openedCount = 0)
            else value
        }
        for (period in WidgetPeriod.entries) {
            val range = resolveCalendarAnalysisRange(
                period.days, through, summaries.minOf { it.dayStartMillis }, through, zone,
            )
            val app = LongTermAnalyzer().analyzeCalendarRange(summaries, range, through, zone)
            val widget = buildSummaryWidgetState(
                period, summaries, through, through, true, false, through, zone,
            )
            assertEquals(app.innerMillis, widget.innerMillis)
            assertEquals(app.coverMillis, widget.coverMillis)
            assertEquals(app.openedCount, widget.openedCount)
            assertEquals(app.calendarDayCount, widget.recordedDayCount)
            assertEquals(app.rangeStartMillis, widget.dataRange?.startMillis(zone))
            assertEquals(app.rangeEndMillis, widget.syncedThroughMillis)
        }
    }

    @Test
    fun staleSevenDaysEndsAtSyncAndExcludesUncollectedDays() {
        val syncEnd = millis(today.minusDays(1))
        val summaries = (0L..9L).map { summary(today.minusDays(it)) }
        val range = resolveCalendarAnalysisRange(
            7, through, millis(today.minusDays(9)), syncEnd, zone,
        )
        val app = LongTermAnalyzer().analyzeCalendarRange(summaries, range, syncEnd, zone)
        val widget = buildSummaryWidgetState(
            WidgetPeriod.DAYS_7, summaries, syncEnd, syncEnd, true, false, through, zone,
        )
        assertEquals(5, range.recordedDayCount(zone))
        assertEquals(500L, app.innerMillis)
        assertEquals(app.innerMillis, widget.innerMillis)
        assertEquals(syncEnd, app.rangeEndMillis)
    }

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun summary(date: LocalDate) = DailyPostureSummary(
        dayStartMillis = millis(date),
        dayEndMillis = millis(date.plusDays(1)),
        zoneId = zone.id,
        coverMillis = 300,
        innerMillis = 100,
        excludedMillis = 0,
        openedCount = 3,
        closedCount = 2,
        evidenceGapCount = 0,
    )
}
