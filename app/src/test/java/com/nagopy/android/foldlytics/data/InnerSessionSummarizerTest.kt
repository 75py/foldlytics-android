package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.InnerDisplaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InnerSessionSummarizerTest {
    @Test
    fun filtersIncompleteAndBoundarySessionsAndCalculatesOddAndEvenMedians() {
        val summarizer = summarizer()
        val baseSessions = listOf(
            session(openedAt = 9, closedAt = 20, duration = 99),
            session(openedAt = 10, closedAt = 20, duration = 1),
            session(openedAt = 20, closedAt = 30, duration = 3),
            session(openedAt = 30, closedAt = 40, duration = 9),
            session(openedAt = 40, closedAt = 100, duration = 11),
            session(openedAt = 50, closedAt = null, duration = 13),
        )

        val odd = summarizer.summarize(
            sessions = baseSessions,
            rangeStartMillis = 10,
            rangeEndMillis = 100,
            detectedOpenCount = 6,
        )
        val even = summarizer.summarize(
            sessions = baseSessions + session(openedAt = 40, closedAt = 99, duration = 11),
            rangeStartMillis = 10,
            rangeEndMillis = 100,
            detectedOpenCount = 7,
        )

        assertEquals(6, odd.detectedOpenCount)
        assertEquals(3, odd.completeSessionCount)
        assertEquals(3L, odd.medianInnerActiveMillis)
        assertEquals(9L, odd.longestInnerActiveMillis)
        assertEquals(4, even.completeSessionCount)
        assertEquals(6L, even.medianInnerActiveMillis)
        assertEquals(11L, even.longestInnerActiveMillis)
    }

    @Test
    fun keepsMeasuredZeroInsteadOfReturningAnEmptyStatistic() {
        val summary = summarizer().summarize(
            sessions = listOf(session(openedAt = 10, closedAt = 20, duration = 0)),
            rangeStartMillis = 0,
            rangeEndMillis = 30,
            detectedOpenCount = 1,
        )

        assertEquals(1, summary.completeSessionCount)
        assertEquals(0L, summary.medianInnerActiveMillis)
        assertEquals(0L, summary.longestInnerActiveMillis)
    }

    @Test
    fun returnsEmptyStatisticsWhenNoSessionIsComplete() {
        val summary = summarizer().summarize(
            sessions = listOf(session(openedAt = 10, closedAt = null, duration = 5)),
            rangeStartMillis = 0,
            rangeEndMillis = 30,
            detectedOpenCount = 1,
        )

        assertEquals(0, summary.completeSessionCount)
        assertNull(summary.medianInnerActiveMillis)
        assertNull(summary.longestInnerActiveMillis)
        assertEquals(emptyList<Any>(), summary.startApps)
    }

    @Test
    fun ranksLaunchableAppsByCountTimeLabelAndPackageAndKeepsUnclassifiedCount() {
        val labels = mapOf(
            "app.most" to "Most",
            "app.faster" to "Faster",
            "app.slower" to "Slower",
            "app.alpha" to "Alpha",
            "app.zeta" to "Zeta",
            "app.same.a" to "Same",
            "app.same.b" to "Same",
            "app.internal" to "Internal",
        )
        val summarizer = InnerSessionSummarizer(
            packageLabel = { labels.getValue(it) },
            isLauncherApp = { it != "app.internal" },
        )
        var openedAt = 0L
        fun sessions(packageName: String?, vararg durations: Long): List<InnerDisplaySession> =
            durations.map { duration ->
                openedAt += 2L
                session(
                    openedAt = openedAt,
                    closedAt = openedAt + 1L,
                    duration = duration,
                    packageName = packageName,
                )
            }
        val source = buildList {
            addAll(sessions("app.internal", 100, 100, 100, 100))
            addAll(sessions("app.most", 1, 1, 1))
            addAll(sessions("app.faster", 20, 20))
            addAll(sessions("app.slower", 10, 10))
            addAll(sessions("app.alpha", 10))
            addAll(sessions("app.zeta", 10))
            addAll(sessions("app.same.b", 5))
            addAll(sessions("app.same.a", 5))
            addAll(sessions(null, 7))
        }

        val summary = summarizer.summarize(
            sessions = source,
            rangeStartMillis = 0,
            rangeEndMillis = 100,
            detectedOpenCount = source.size,
        )

        assertEquals(
            listOf(
                "app.most",
                "app.faster",
                "app.slower",
                "app.alpha",
                "app.zeta",
                "app.same.a",
                "app.same.b",
            ),
            summary.startApps.map { it.packageName },
        )
        assertEquals(listOf(3, 2, 2), summary.startApps.take(3).map { it.completeSessionCount })
        assertEquals(listOf(3L, 40L, 20L), summary.startApps.take(3).map { it.totalInnerActiveMillis })
        assertFalse(summary.startApps.any { it.packageName == "app.internal" })
        assertEquals(1, summary.unclassifiedStartCount)
    }

    @Test
    fun evenMedianDoesNotOverflow() {
        assertEquals(
            Long.MAX_VALUE - 1L,
            listOf(Long.MAX_VALUE - 2L, Long.MAX_VALUE).medianOrNull(),
        )
    }

    private fun summarizer() = InnerSessionSummarizer(
        packageLabel = { it },
        isLauncherApp = { true },
    )

    private fun session(
        openedAt: Long,
        closedAt: Long?,
        duration: Long,
        packageName: String? = null,
    ) = InnerDisplaySession(
        openedAtMillis = openedAt,
        openedSequenceAtTimestamp = 0,
        closedAtMillis = closedAt,
        innerActiveMillis = duration,
        startPackageName = packageName,
    )
}
