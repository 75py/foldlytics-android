package com.nagopy.android.foldlytics.model

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPeriodAvailabilityTest {
    private val zoneId = ZoneOffset.UTC

    @Test
    fun keepsShortPeriodsAvailableBeforeHistoryIsRecorded() {
        val available = availableAnalysisPeriods(
            recordRangeStartMillis = null,
            recordRangeEndMillis = null,
            zoneId = zoneId,
        )

        assertTrue(AnalysisPeriod.HOURS_1 in available)
        assertTrue(AnalysisPeriod.HOURS_6 in available)
        assertTrue(AnalysisPeriod.HOURS_24 in available)
        assertFalse(AnalysisPeriod.DAYS_7 in available)
        assertFalse(AnalysisPeriod.CUSTOM in available)
    }

    @Test
    fun enablesOnlyFixedPeriodsReachedByFortyRecordedDays() {
        val start = dateMillis(LocalDate.of(2026, 1, 1))
        val end = dateMillis(LocalDate.of(2026, 2, 10))

        val available = availableAnalysisPeriods(start, end, zoneId)

        assertTrue(AnalysisPeriod.DAYS_7 in available)
        assertTrue(AnalysisPeriod.DAYS_30 in available)
        assertFalse(AnalysisPeriod.DAYS_90 in available)
        assertFalse(AnalysisPeriod.DAYS_365 in available)
        assertTrue(AnalysisPeriod.CUSTOM in available)
    }

    @Test
    fun enablesNinetyDaysAtTheExactCalendarBoundary() {
        val start = dateMillis(LocalDate.of(2026, 1, 1))
        val end = dateMillis(LocalDate.of(2026, 4, 1))

        val available = availableAnalysisPeriods(start, end, zoneId)

        assertTrue(AnalysisPeriod.DAYS_90 in available)
        assertFalse(AnalysisPeriod.DAYS_365 in available)
    }

    @Test
    fun acceptsAtMostOneThousandNinetyFiveSelectedDays() {
        val recordStart = dateMillis(LocalDate.of(2020, 1, 1))
        val recordEnd = dateMillis(LocalDate.of(2026, 1, 1))
        val validStart = dateMillis(LocalDate.of(2023, 1, 1))
        val validEnd = dateMillis(LocalDate.of(2025, 12, 31))
        val tooLongEnd = dateMillis(LocalDate.of(2026, 1, 1))

        assertTrue(
            isValidCustomAnalysisRange(
                CustomAnalysisRange(validStart, validEnd),
                recordStart,
                recordEnd,
                zoneId,
            ),
        )
        assertFalse(
            isValidCustomAnalysisRange(
                CustomAnalysisRange(validStart, tooLongEnd),
                recordStart,
                recordEnd,
                zoneId,
            ),
        )
    }

    @Test
    fun acceptsTheCurrentRecordedDateUntilItsEndOfDay() {
        val recordStart = dateMillis(LocalDate.of(2026, 1, 1))
        val recordEnd = dateMillis(LocalDate.of(2026, 1, 10)) + 12L * 60L * 60L * 1_000L
        val currentDateRange = CustomAnalysisRange(
            startMillis = dateMillis(LocalDate.of(2026, 1, 5)),
            endMillis = dateMillis(LocalDate.of(2026, 1, 11)),
        )
        val futureDateRange = currentDateRange.copy(
            endMillis = dateMillis(LocalDate.of(2026, 1, 12)),
        )

        assertTrue(
            isValidCustomAnalysisRange(
                currentDateRange,
                recordStart,
                recordEnd,
                zoneId,
            ),
        )
        assertFalse(
            isValidCustomAnalysisRange(
                futureDateRange,
                recordStart,
                recordEnd,
                zoneId,
            ),
        )
    }

    private fun dateMillis(date: LocalDate): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()
}
