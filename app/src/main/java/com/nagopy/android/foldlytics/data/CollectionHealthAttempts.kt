package com.nagopy.android.foldlytics.data

/**
 * Selects the attempts represented by collection-health diagnostics.
 *
 * Usage totals stop at the latest successful sync. For current predefined periods, health also
 * includes attempts made since that point so a currently failing background sync is visible.
 * A custom period is historical and therefore remains bounded to its selected end.
 */
internal fun collectionHealthAttemptsForRange(
    attempts: List<SyncAttempt>,
    rangeStartMillis: Long,
    rangeEndMillis: Long,
    currentMillis: Long,
    isCustomRange: Boolean,
): List<SyncAttempt> {
    val healthEndMillis = if (isCustomRange) {
        rangeEndMillis
    } else {
        maxOf(rangeEndMillis, currentMillis)
    }
    return attempts.filter { attempt ->
        attempt.attemptedAtMillis in rangeStartMillis..healthEndMillis
    }
}
