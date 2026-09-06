package com.nagopy.android.foldlytics.widget

import com.nagopy.android.foldlytics.model.DailyPostureSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Calendar periods ending on the last collected day, as in the app's daily analysis. */
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

fun widgetDateRange(period: WidgetPeriod, throughMillis: Long, zoneId: ZoneId): WidgetDateRange {
    // Collection intervals are end-exclusive: a sync at midnight still describes yesterday.
    val lastDate = Instant.ofEpochMilli((throughMillis - 1L).coerceAtLeast(0L))
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
    val range = widgetDateRange(period, syncedThroughMillis ?: nowMillis, zoneId)
    val selected = if (hasPermission) summaries.filter {
        val date = Instant.ofEpochMilli(it.dayStartMillis).atZone(zoneId).toLocalDate()
        date in range.start..range.endInclusive
    } else emptyList()
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
        status = when {
            !hasPermission -> WidgetStatus.PERMISSION_REQUIRED
            updateFailed -> WidgetStatus.UPDATE_FAILED
            inner + cover == 0L -> WidgetStatus.NO_DATA
            else -> WidgetStatus.READY
        },
    )
}
