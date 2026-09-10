package com.nagopy.android.foldlytics.widget

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.resolveCalendarAnalysisRange
import com.nagopy.android.foldlytics.model.selectCalendarSummaries
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Calendar periods including the current local day, as in the app's daily analysis. */
enum class WidgetPeriod(val days: Long) {
    TODAY(1),
    DAYS_7(7),
    DAYS_30(30),
    ;

    companion object {
        fun fromName(value: String?): WidgetPeriod =
            entries.firstOrNull { it.name == value } ?: DAYS_7
    }
}

data class WidgetDateRange(val start: LocalDate, val endInclusive: LocalDate) {
    fun startMillis(zoneId: ZoneId): Long = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

fun widgetDateRange(period: WidgetPeriod, nowMillis: Long, zoneId: ZoneId): WidgetDateRange {
    val lastDate = Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId).toLocalDate()
    return WidgetDateRange(lastDate.minusDays(period.days - 1L), lastDate)
}

enum class WidgetStatus { READY, NO_DATA, PERMISSION_REQUIRED, UPDATE_FAILED }

data class SummaryWidgetState(
    val period: WidgetPeriod,
    val dateRange: WidgetDateRange,
    val innerMillis: Long = 0L,
    val coverMillis: Long = 0L,
    val openedCount: Int = 0,
    val hasRecordedEvidence: Boolean = false,
    val lastSyncMillis: Long? = null,
    val status: WidgetStatus = WidgetStatus.NO_DATA,
    val dataRange: WidgetDateRange? = null,
    val syncedThroughMillis: Long? = null,
    val recordedDayCount: Int = 0,
    val isStale: Boolean = false,
) {
    val innerRatio: Float?
        get() = if (innerMillis + coverMillis > 0L && status != WidgetStatus.PERMISSION_REQUIRED) {
            innerMillis.toFloat() / (innerMillis + coverMillis)
        } else {
            null
        }
}

fun buildSummaryWidgetState(
    period: WidgetPeriod,
    summaries: List<DailyPostureSummary>,
    syncedThroughMillis: Long?,
    lastSyncMillis: Long?,
    hasPermission: Boolean,
    updateFailed: Boolean,
    nowMillis: Long,
    zoneId: ZoneId,
): SummaryWidgetState {
    val range = widgetDateRange(period, nowMillis, zoneId)
    val calendarRange = resolveCalendarAnalysisRange(
        days = period.days,
        nowMillis = nowMillis,
        recordRangeStartMillis = summaries.minOfOrNull(DailyPostureSummary::dayStartMillis),
        syncedThroughMillis = syncedThroughMillis,
        zoneId = zoneId,
    )
    val selected = if (hasPermission) {
        selectCalendarSummaries(summaries, calendarRange)
    } else {
        emptyList()
    }
    val dataRange = calendarRange.dataRange?.takeIf { hasPermission }
    val inner = selected.sumOf { it.innerMillis }
    val cover = selected.sumOf { it.coverMillis }
    return SummaryWidgetState(
        period = period,
        dateRange = range,
        innerMillis = inner,
        coverMillis = cover,
        openedCount = selected.sumOf { it.openedCount },
        hasRecordedEvidence = selected.any { it.observedMillis > 0L || it.openedCount > 0 || it.closedCount > 0 },
        lastSyncMillis = lastSyncMillis,
        dataRange = dataRange?.let {
            WidgetDateRange(
                start = Instant.ofEpochMilli(it.startMillis).atZone(zoneId).toLocalDate(),
                endInclusive = Instant.ofEpochMilli(it.endMillis - 1L).atZone(zoneId).toLocalDate(),
            )
        },
        syncedThroughMillis = syncedThroughMillis,
        recordedDayCount = if (hasPermission) calendarRange.recordedDayCount(zoneId) else 0,
        isStale = syncedThroughMillis != null && syncedThroughMillis <=
            Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
                .atStartOfDay(zoneId).toInstant().toEpochMilli(),
        status = when {
            !hasPermission -> WidgetStatus.PERMISSION_REQUIRED
            updateFailed -> WidgetStatus.UPDATE_FAILED
            inner + cover == 0L -> WidgetStatus.NO_DATA
            else -> WidgetStatus.READY
        },
    )
}
