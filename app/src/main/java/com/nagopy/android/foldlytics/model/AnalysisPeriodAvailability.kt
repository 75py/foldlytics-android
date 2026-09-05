package com.nagopy.android.foldlytics.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

const val MAX_CUSTOM_RANGE_DAYS = 1_095L

/** Periods that can be analyzed regardless of how much history has been recorded. */
val DEFAULT_ANALYSIS_PERIODS: Set<AnalysisPeriod> =
    AnalysisPeriod.entries.filterTo(mutableSetOf()) { it.hours != null }

fun availableAnalysisPeriods(
    recordRangeStartMillis: Long?,
    recordRangeEndMillis: Long?,
    zoneId: ZoneId,
): Set<AnalysisPeriod> = AnalysisPeriod.entries.filterTo(mutableSetOf()) { period ->
    when {
        period.hours != null -> true
        recordRangeStartMillis == null || recordRangeEndMillis == null -> false
        recordRangeStartMillis >= recordRangeEndMillis -> false
        period == AnalysisPeriod.CUSTOM -> true
        else -> {
            val days = requireNotNull(period.longTermPeriod).days
            val recordedStartDate = recordRangeStartMillis.toLocalDate(zoneId)
            val recordedEndDate = (recordRangeEndMillis - 1L).toLocalDate(zoneId)
            val requiredStartDate = recordedEndDate.minusDays(days - 1L)
            !recordedStartDate.isAfter(requiredStartDate)
        }
    }
}

fun customAnalysisRangeDayCount(
    startMillis: Long,
    endMillis: Long,
    zoneId: ZoneId,
): Long {
    if (startMillis >= endMillis) return 0L
    val startDate = startMillis.toLocalDate(zoneId)
    val endDate = (endMillis - 1L).toLocalDate(zoneId)
    return ChronoUnit.DAYS.between(startDate, endDate) + 1L
}

fun isValidCustomAnalysisRange(
    range: CustomAnalysisRange,
    recordRangeStartMillis: Long,
    recordRangeEndMillis: Long,
    zoneId: ZoneId,
): Boolean {
    if (range.startMillis >= range.endMillis || recordRangeStartMillis >= recordRangeEndMillis) {
        return false
    }
    val rangeStartDate = range.startMillis.toLocalDate(zoneId)
    val rangeEndDate = (range.endMillis - 1L).toLocalDate(zoneId)
    val recordedStartDate = recordRangeStartMillis.toLocalDate(zoneId)
    val recordedEndDate = (recordRangeEndMillis - 1L).toLocalDate(zoneId)
    return !rangeStartDate.isBefore(recordedStartDate) &&
        !rangeEndDate.isAfter(recordedEndDate) &&
        customAnalysisRangeDayCount(range.startMillis, range.endMillis, zoneId) in
        1L..MAX_CUSTOM_RANGE_DAYS
}

fun recordedCalendarDayCount(
    startMillis: Long,
    endMillis: Long,
    zoneId: ZoneId,
): Long = customAnalysisRangeDayCount(startMillis, endMillis, zoneId)

private fun Long.toLocalDate(zoneId: ZoneId) =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
