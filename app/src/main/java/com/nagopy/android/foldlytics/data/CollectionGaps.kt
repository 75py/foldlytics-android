package com.nagopy.android.foldlytics.data

import java.util.concurrent.TimeUnit

data class CollectionGap(
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long = (endMillis - startMillis).coerceAtLeast(0L)
}

internal val SYNC_OVERLAP_MILLIS: Long = TimeUnit.HOURS.toMillis(1)
internal val COLLECTION_INTERRUPTION_THRESHOLD_MILLIS: Long = TimeUnit.HOURS.toMillis(24)

/**
 * Finds intervals for which periodic collection was too far apart to safely assume that Android
 * still returned every requested event. The raw events remain untouched; analyzers use the start
 * of each interval as a conservative state-reset boundary.
 */
fun detectCollectionGaps(
    attempts: List<SyncAttempt>,
    interruptionThresholdMillis: Long = COLLECTION_INTERRUPTION_THRESHOLD_MILLIS,
): List<CollectionGap> {
    require(interruptionThresholdMillis >= 0L)
    val successes = attempts
        .asSequence()
        .filter { it.status == SyncAttemptStatus.SUCCESS }
        .sortedWith(
            compareBy<SyncAttempt> { it.queryEndMillis }
                .thenBy { it.attemptedAtMillis },
        )
        .toList()
    if (successes.isEmpty()) return emptyList()

    val candidates = buildList {
        val first = successes.first()
        if (first.queryBeginMillis > 0L) {
            val inferredPreviousEnd = (first.queryBeginMillis + SYNC_OVERLAP_MILLIS)
                .coerceAtMost(first.queryEndMillis)
            addGapIfNeeded(
                startMillis = inferredPreviousEnd,
                endMillis = first.queryEndMillis,
                thresholdMillis = interruptionThresholdMillis,
            )
        }

        successes.zipWithNext { previous, next ->
            addGapIfNeeded(
                startMillis = previous.queryEndMillis,
                endMillis = next.queryEndMillis,
                thresholdMillis = interruptionThresholdMillis,
            )
        }
    }
    return candidates.distinctBy { it.startMillis to it.endMillis }
}

private fun MutableList<CollectionGap>.addGapIfNeeded(
    startMillis: Long,
    endMillis: Long,
    thresholdMillis: Long,
) {
    val duration = endMillis - startMillis
    if (duration > thresholdMillis) {
        add(CollectionGap(startMillis = startMillis, endMillis = endMillis))
    }
}
