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
        val longSessions = selectLongSessions(completeSessions).map(::toDetail)

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

    internal fun selectLongSessions(sessions: List<InnerDisplaySession>): List<InnerDisplaySession> =
        sessions.asSequence()
            .filter { it.isComplete && it.innerActiveMillis > 0L }
            .sortedWith(
                compareByDescending<InnerDisplaySession> { it.innerActiveMillis }
                    .thenByDescending { it.openedAtMillis }
                    .thenBy { it.openedSequenceAtTimestamp },
            )
            .take(MAX_LONG_SESSIONS)
            .toList()

    private fun toDetail(session: InnerDisplaySession): InnerSessionDetail {
        val sessionMillis = session.innerActiveMillis.coerceAtLeast(0L)
        val usage = session.appSetUsageMillis
            ?: session.appUsageMillis.mapKeys { setOf(it.key) }
        val apps = usage.asSequence()
            .filter { (packages, millis) ->
                millis > 0L && packages.isNotEmpty() && packages.all(isLauncherApp)
            }
            .map { (packages, millis) ->
                val names = packages.sorted()
                val labels = names.map(packageLabel)
                InnerSessionAppUsage(
                    packageName = names.joinToString("+"),
                    label = labels.joinToString(" + "),
                    innerActiveMillis = millis,
                    packageNames = names,
                    labels = labels,
                )
            }
            .sortedWith(
                compareByDescending<InnerSessionAppUsage> { it.innerActiveMillis }
                    .thenBy { it.label }
                    .thenBy { it.packageName },
            )
            .take(MAX_APPS_PER_SESSION)
            .toList()
        // Invalid or stale replay must never create a breakdown larger than its cached duration.
        var remainingMillis = sessionMillis
        val boundedApps = apps.mapNotNull { app ->
            val millis = minOf(app.innerActiveMillis, remainingMillis)
            remainingMillis -= millis
            app.copy(innerActiveMillis = millis).takeIf { millis > 0L }
        }
        val displayedAppMillis = boundedApps.fold(0L) { total, app ->
            total + app.innerActiveMillis
        }
        return InnerSessionDetail(
            openedAtMillis = session.openedAtMillis,
            openedSequenceAtTimestamp = session.openedSequenceAtTimestamp,
            innerActiveMillis = sessionMillis,
            appUsages = boundedApps,
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
