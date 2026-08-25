package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.InnerDisplaySession
import com.nagopy.android.foldlytics.model.InnerSessionAppSummary
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
            session.openedAtMillis >= rangeStartMillis &&
                session.closedAtMillis != null &&
                session.closedAtMillis < rangeEndMillis
        }
        val durations = completeSessions.map(InnerDisplaySession::innerActiveMillis).sorted()
        val appSummaries = completeSessions
            .mapNotNull { session ->
                session.startPackageName?.let { packageName -> packageName to session }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (packageName, values) ->
                InnerSessionAppSummary(
                    packageName = packageName,
                    label = packageLabel(packageName),
                    completeSessionCount = values.size,
                    totalInnerActiveMillis = values.sumOf(InnerDisplaySession::innerActiveMillis),
                    isLauncherApp = isLauncherApp(packageName),
                )
            }
            .asSequence()
            .filter(InnerSessionAppSummary::isLauncherApp)
            .sortedWith(
                compareByDescending<InnerSessionAppSummary> { it.completeSessionCount }
                    .thenByDescending { it.totalInnerActiveMillis }
                    .thenBy { it.label }
                    .thenBy { it.packageName },
            )
            .toList()

        return InnerSessionSummary(
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            detectedOpenCount = detectedOpenCount,
            completeSessionCount = completeSessions.size,
            medianInnerActiveMillis = durations.medianOrNull(),
            longestInnerActiveMillis = durations.maxOrNull(),
            startApps = appSummaries,
            unclassifiedStartCount = completeSessions.count { it.startPackageName == null },
        )
    }
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
