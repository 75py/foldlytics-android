package com.nagopy.android.foldlytics.data

import java.util.concurrent.TimeUnit

data class UsageAnalysisWindow(
    val seedStartMillis: Long,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
)

fun createUsageAnalysisWindow(
    periodHours: Int,
    syncedThroughMillis: Long,
): UsageAnalysisWindow {
    require(periodHours > 0)
    val rangeEnd = syncedThroughMillis.coerceAtLeast(0L)
    val rangeStart = (rangeEnd - TimeUnit.HOURS.toMillis(periodHours.toLong()))
        .coerceAtLeast(0L)
    val seedStart = (rangeStart - SEED_LOOKBACK_MILLIS).coerceAtLeast(0L)
    return UsageAnalysisWindow(
        seedStartMillis = seedStart,
        rangeStartMillis = rangeStart,
        rangeEndMillis = rangeEnd,
    )
}

private val SEED_LOOKBACK_MILLIS: Long = TimeUnit.HOURS.toMillis(72)
