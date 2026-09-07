package com.nagopy.android.foldlytics.model

import java.time.Instant
import java.time.ZoneId

/** Selected calendar period and the recorded interval available inside it, both end-exclusive. */
data class CalendarAnalysisRange(
    val requestedStartMillis: Long,
    val requestedEndMillis: Long,
    val dataRange: CustomAnalysisRange?,
) {
    fun recordedDayCount(zoneId: ZoneId): Int = dataRange?.let {
        recordedCalendarDayCount(it.startMillis, it.endMillis, zoneId).toInt()
    } ?: 0
}

fun resolveCalendarAnalysisRange(
    days: Long,
    nowMillis: Long,
    recordRangeStartMillis: Long?,
    syncedThroughMillis: Long?,
    zoneId: ZoneId,
): CalendarAnalysisRange {
    require(days > 0L)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val requestedStart = today.minusDays(days - 1L)
        .atStartOfDay(zoneId).toInstant().toEpochMilli()
    val requestedEnd = today.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val dataRange = if (recordRangeStartMillis != null && syncedThroughMillis != null) {
        val start = maxOf(requestedStart, recordRangeStartMillis)
        val end = minOf(requestedEnd, syncedThroughMillis)
        if (start < end) CustomAnalysisRange(start, end) else null
    } else {
        null
    }
    return CalendarAnalysisRange(requestedStart, requestedEnd, dataRange)
}

/** Daily caches must have been built through the same sync endpoint used to resolve [range]. */
fun selectCalendarSummaries(
    summaries: List<DailyPostureSummary>,
    range: CalendarAnalysisRange,
): List<DailyPostureSummary> {
    val dataRange = range.dataRange ?: return emptyList()
    return summaries.filter {
        it.dayStartMillis >= dataRange.startMillis && it.dayStartMillis < dataRange.endMillis
    }
}
