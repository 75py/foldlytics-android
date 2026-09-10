package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordedCalendarDaysTest {
    private val zone = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 3, 1)

    @Test
    fun retainsInactiveDaysBeforeFirstUsageWithoutInventingUsage() {
        val active = DailyPostureSummary(
            dayStartMillis = millis(start.plusDays(9)),
            dayEndMillis = millis(start.plusDays(10)),
            zoneId = zone.id,
            coverMillis = 300,
            innerMillis = 100,
            excludedMillis = 0,
            openedCount = 2,
            closedCount = 1,
            evidenceGapCount = 0,
        )
        val values = includeRecordedZeroUsageDays(
            listOf(active), millis(start), millis(start.plusDays(10)), zone,
        )

        assertEquals(10, values.size)
        assertEquals(millis(start), values.first().dayStartMillis)
        assertEquals(active, values.last())
        assertTrue(values.dropLast(1).all { it.observedMillis == 0L && it.openedCount == 0 })
        assertEquals(23L * 3_600_000L, values[7].dayEndMillis - values[7].dayStartMillis)
    }

    @Test
    fun completelyInactiveRecordedHistoryStillContainsItsCalendarDays() {
        val values = includeRecordedZeroUsageDays(
            emptyList(), millis(start), millis(start.plusDays(10)), zone,
        )
        assertEquals(10, values.size)
        assertTrue(values.all { it.observedMillis == 0L })
    }

    @Test
    fun emptyRecordingIntervalDoesNotCreateADay() {
        assertTrue(includeRecordedZeroUsageDays(emptyList(), millis(start), millis(start), zone).isEmpty())
    }

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
