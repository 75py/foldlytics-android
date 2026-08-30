package com.nagopy.android.foldlytics.data

import com.nagopy.android.foldlytics.model.InnerDisplaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerSessionSummarizerTest {
    @Test
    fun filtersIncompleteAndBoundarySessionsAndCalculatesMedianAverageAndLongest() {
        val source = listOf(
            session(openedAt = 9, closedAt = 20, duration = 99),
            session(openedAt = 10, closedAt = 20, duration = 1),
            session(openedAt = 20, closedAt = 30, duration = 3),
            session(openedAt = 30, closedAt = 40, duration = 9),
            session(openedAt = 40, closedAt = 100, duration = 11),
            session(openedAt = 50, closedAt = null, duration = 13),
        )

        val odd = summarizer().summarize(
            sessions = source,
            rangeStartMillis = 10,
            rangeEndMillis = 100,
            detectedOpenCount = 6,
        )
        val even = summarizer().summarize(
            sessions = source + session(openedAt = 40, closedAt = 99, duration = 11),
            rangeStartMillis = 10,
            rangeEndMillis = 100,
            detectedOpenCount = 7,
        )

        assertEquals(6, odd.detectedOpenCount)
        assertEquals(3, odd.completeSessionCount)
        assertEquals(3L, odd.medianInnerActiveMillis)
        assertEquals(4L, odd.averageInnerActiveMillis)
        assertEquals(9L, odd.longestInnerActiveMillis)
        assertEquals(4, even.completeSessionCount)
        assertEquals(6L, even.medianInnerActiveMillis)
        assertEquals(6L, even.averageInnerActiveMillis)
        assertEquals(11L, even.longestInnerActiveMillis)
    }

    @Test
    fun includesZeroTimeCompleteSessionsInStatisticsButNotLongSessions() {
        val summary = summarizer().summarize(
            sessions = listOf(
                session(openedAt = 10, closedAt = 20, duration = 0),
                session(openedAt = 30, closedAt = 40, duration = 2_000),
            ),
            rangeStartMillis = 0,
            rangeEndMillis = 50,
            detectedOpenCount = 2,
        )

        assertEquals(2, summary.completeSessionCount)
        assertEquals(1_000L, summary.medianInnerActiveMillis)
        assertEquals(1_000L, summary.averageInnerActiveMillis)
        assertEquals(2_000L, summary.longestInnerActiveMillis)
        assertEquals(listOf(30L), summary.longSessions.map { it.openedAtMillis })
    }

    @Test
    fun ranksThreeLongSessionsByDurationThenNewestStartThenSequence() {
        val source = listOf(
            session(openedAt = 10, sequence = 2, closedAt = 11, duration = 9),
            session(openedAt = 20, sequence = 1, closedAt = 21, duration = 9),
            session(openedAt = 20, sequence = 0, closedAt = 22, duration = 9),
            session(openedAt = 30, sequence = 0, closedAt = 31, duration = 8),
            session(openedAt = 40, sequence = 0, closedAt = 41, duration = 7),
            session(openedAt = 50, sequence = 0, closedAt = 51, duration = 0),
        )

        val summary = summarizer().summarize(
            sessions = source,
            rangeStartMillis = 0,
            rangeEndMillis = 100,
            detectedOpenCount = source.size,
        )

        assertEquals(
            listOf(20L to 0, 20L to 1, 10L to 2),
            summary.longSessions.map { it.openedAtMillis to it.openedSequenceAtTimestamp },
        )
    }

    @Test
    fun showsTopThreeLaunchableAppsAndPutsEverythingElseInOther() {
        val labels = mapOf(
            "app.a" to "A",
            "app.b" to "B",
            "app.c" to "C",
            "app.d" to "D",
            "app.internal" to "Internal",
        )
        val summary = InnerSessionSummarizer(
            packageLabel = { labels.getValue(it) },
            isLauncherApp = { it != "app.internal" },
        ).summarize(
            sessions = listOf(
                session(
                    openedAt = 10,
                    closedAt = 100,
                    duration = 100,
                    appUsageMillis = mapOf(
                        "app.a" to 30,
                        "app.b" to 20,
                        "app.c" to 10,
                        "app.d" to 5,
                        "app.internal" to 10,
                    ),
                ),
            ),
            rangeStartMillis = 0,
            rangeEndMillis = 101,
            detectedOpenCount = 1,
        )

        val detail = summary.longSessions.single()
        assertEquals(listOf("app.a", "app.b", "app.c"), detail.appUsages.map { it.packageName })
        assertEquals(listOf(30L, 20L, 10L), detail.appUsages.map { it.innerActiveMillis })
        assertEquals(40L, detail.otherInnerActiveMillis)
        assertTrue(detail.appUsages.all { it.isLauncherApp })
    }

    @Test
    fun excludesSessionWhenEitherBoundaryIsOutsideSelectedPeriod() {
        val summary = summarizer().summarize(
            sessions = listOf(
                session(openedAt = 10, closedAt = 20, duration = 1),
                session(openedAt = 20, closedAt = 30, duration = 2),
                session(openedAt = 30, closedAt = 40, duration = 3),
            ),
            rangeStartMillis = 20,
            rangeEndMillis = 40,
            detectedOpenCount = 3,
        )

        assertEquals(1, summary.completeSessionCount)
        assertEquals(2L, summary.medianInnerActiveMillis)
    }

    @Test
    fun calculatesAverageWithoutLongOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            listOf(Long.MAX_VALUE, Long.MAX_VALUE).averageMillisOrNull(),
        )
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
        sequence: Int = 0,
        appUsageMillis: Map<String, Long> = emptyMap(),
    ) = InnerDisplaySession(
        openedAtMillis = openedAt,
        openedSequenceAtTimestamp = sequence,
        closedAtMillis = closedAt,
        innerActiveMillis = duration,
        appUsageMillis = appUsageMillis,
    )
}
