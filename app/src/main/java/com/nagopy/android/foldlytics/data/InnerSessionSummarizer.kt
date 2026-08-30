package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.InnerDisplaySession
import com.nagopy.android.foldlytics.model.InnerSessionAppUsage
import com.nagopy.android.foldlytics.model.InnerSessionDetail
import com.nagopy.android.foldlytics.model.InnerSessionSummary

class InnerSessionSummarizer(
    private val packageLabel: (String) -> String,
    private val isLauncherApp: (String) -> Boolean,
) {
    fun summarize(
        sessions: List<InnerDisplaySession>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        detectedOpenCount: Int,
    ): InnerSessionSummary {
        val completeSessions = sessions.filter { session ->
            val closedAtMillis = session.closedAtMillis
            session.openedAtMillis >= rangeStartMillis &&
                closedAtMillis != null &&
                closedAtMillis >= session.openedAtMillis &&
                closedAtMillis < rangeEndMillis
        }
        val durations = completeSessions
            .map { it.innerActiveMillis.coerceAtLeast(0L) }
            .sorted()
        val longSessions = completeSessions
            .asSequence()
            .filter { it.innerActiveMillis > 0L }
            .sortedWith(
                compareByDescending<InnerDisplaySession> { it.innerActiveMillis }
                    .thenByDescending { it.openedAtMillis }
                    // Sequence is a deterministic tie-breaker for same-time opens.
                    .thenBy { it.openedSequenceAtTimestamp },
            )
            .take(MAX_LONG_SESSIONS)
            .map(::toDetail)
            .toList()

        return InnerSessionSummary(
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            detectedOpenCount = detectedOpenCount,
            completeSessionCount = completeSessions.size,
            medianInnerActiveMillis = durations.medianOrNull(),
            averageInnerActiveMillis = durations.averageMillisOrNull(),
            longestInnerActiveMillis = durations.maxOrNull(),
            longSessions = longSessions,
        )
    }

    private fun toDetail(session: InnerDisplaySession): InnerSessionDetail {
        val sessionMillis = session.innerActiveMillis.coerceAtLeast(0L)
        val apps = session.appUsageMillis
            .asSequence()
            .filter { (_, millis) -> millis > 0L }
            .filter { (packageName, _) -> isLauncherApp(packageName) }
            .map { (packageName, millis) ->
                InnerSessionAppUsage(
                    packageName = packageName,
                    label = packageLabel(packageName),
                    innerActiveMillis = millis,
                )
            }
            .sortedWith(
                compareByDescending<InnerSessionAppUsage> { it.innerActiveMillis }
                    .thenBy { it.label }
                    .thenBy { it.packageName },
            )
            .take(MAX_APPS_PER_SESSION)
            .toList()
        val displayedAppMillis = apps.fold(0L) { total, app ->
            total + app.innerActiveMillis
        }
        return InnerSessionDetail(
            openedAtMillis = session.openedAtMillis,
            openedSequenceAtTimestamp = session.openedSequenceAtTimestamp,
            innerActiveMillis = sessionMillis,
            appUsages = apps,
            otherInnerActiveMillis = (sessionMillis - displayedAppMillis).coerceAtLeast(0L),
        )
    }

    private companion object {
        const val MAX_LONG_SESSIONS = 3
        const val MAX_APPS_PER_SESSION = 3
    }
}

/**
 * Calculates the floor of the arithmetic mean without summing the input values. All session
 * durations are non-negative, so quotient and remainder accumulation cannot overflow Long.
 */
internal fun List<Long>.averageMillisOrNull(): Long? {
    if (isEmpty()) return null
    val count = size.toLong()
    var quotient = 0L
    var remainder = 0L
    for (value in this) {
        require(value >= 0L) { "Session durations must not be negative" }
        quotient += value / count
        remainder += value % count
        if (remainder >= count) {
            quotient += remainder / count
            remainder %= count
        }
    }
    return quotient + remainder / count
}

internal fun List<Long>.medianOrNull(): Long? {
    if (isEmpty()) return null
    val middle = size / 2
    return if (size % 2 == 1) {
        this[middle]
    } else {
        val lower = this[middle - 1]
        val upper = this[middle]
        lower + (upper - lower) / 2L
    }
}
