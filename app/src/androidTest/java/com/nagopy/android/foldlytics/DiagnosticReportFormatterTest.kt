package com.nagopy.android.foldlytics

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.UsageAnalysis
import java.time.Instant
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.PostureEventSource
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticReportFormatterTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun reportKeepsTheRetained24HourSnapshotLabelsWhileOneHourAnalysisLoads() {
        assertRetained24HourLabels(
            MainUiState(
                selectedPeriod = AnalysisPeriod.HOURS_1,
                isAnalysisLoading = true,
                analysis = analysis(),
                periodSummary = summary(AnalysisPeriod.HOURS_24),
            ),
        )
    }

    @Test
    fun reportKeepsTheRetained24HourSnapshotLabelsAfterOneHourAnalysisFails() {
        assertRetained24HourLabels(
            MainUiState(
                selectedPeriod = AnalysisPeriod.HOURS_1,
                error = MainUiError(MainUiErrorKind.ANALYSIS, "analysis failed"),
                analysis = analysis(),
                periodSummary = summary(AnalysisPeriod.HOURS_24),
            ),
        )
    }

    @Test
    fun reportKeepsTheRetainedPresetLabelsWhileCustomAnalysisLoadsOrFails() {
        val report = format(
            MainUiState(
                selectedPeriod = AnalysisPeriod.CUSTOM,
                isAnalysisLoading = true,
                analysis = analysis(),
                periodSummary = summary(AnalysisPeriod.DAYS_7),
                longTermInsights = insights(),
            ),
        )

        assertTrue(report.contains(field(R.string.label_screen_period, periodName(AnalysisPeriod.DAYS_7))))
        assertTrue(
            report.contains(
                context.getString(
                    R.string.report_usage_trends_heading,
                    periodName(AnalysisPeriod.DAYS_7),
                ),
            ),
        )
        assertFalse(report.contains(field(R.string.label_screen_period, periodName(AnalysisPeriod.CUSTOM))))
        assertFalse(
            report.contains(
                context.getString(
                    R.string.report_usage_trends_heading,
                    periodName(AnalysisPeriod.CUSTOM),
                ),
            ),
        )
    }

    @Test
    fun reportListsTheNewestFiftyOfTheCompleteHistoryAndCountsAllOfIt() {
        // 60 events, newest first as the analyzer lists them: 10 in range, 50 lookback.
        val complete = (59 downTo 0).map { index ->
            PostureEvent(
                timestampMillis = index * 60_000L,
                source = PostureEventSource.CONFIGURATION_CHANGE,
                posture = DisplayPosture.COVER,
                configuration = null,
                isBeforeRange = index < 50,
            )
        }
        val report = format(
            MainUiState(
                // The startup analysis lists only its compact lookback.
                analysis = analysis().copy(postureEvents = complete.take(12)),
                periodSummary = summary(AnalysisPeriod.HOURS_24),
                diagnosticHistory = DiagnosticHistoryState(
                    generation = 1L,
                    status = DiagnosticHistoryStatus.READY,
                    events = complete,
                ),
            ),
        )
        val time = DateTimeFormatter.ofPattern(
            context.getString(R.string.report_date_time_pattern),
            context.resources.configuration.locales[0],
        ).withZone(ZoneOffset.UTC)
        fun line(event: PostureEvent) = time.format(Instant.ofEpochMilli(event.timestampMillis))

        assertTrue(report.contains(context.getString(R.string.report_posture_log_heading, 10, 50, 50)))
        val listed = report.lines().filter { reportLine ->
            complete.any { reportLine.startsWith(line(it)) }
        }
        assertEquals(complete.take(50).map(::line), listed.map { it.take(line(complete[0]).length) })
        assertFalse(complete.drop(50).any { event -> listed.any { it.startsWith(line(event)) } })
    }

    private fun format(state: MainUiState): String = DiagnosticReportFormatter.format(
        state = state,
        resources = context.resources,
        zoneId = ZoneOffset.UTC,
        createdAt = Instant.EPOCH,
    )

    private fun assertRetained24HourLabels(state: MainUiState) {
        val report = format(state)

        assertTrue(report.contains(field(R.string.label_screen_period, periodName(AnalysisPeriod.HOURS_24))))
        assertTrue(report.contains(diagnosticPeriodField(AnalysisPeriod.HOURS_24)))
        assertFalse(report.contains(field(R.string.label_screen_period, periodName(AnalysisPeriod.HOURS_1))))
        assertFalse(report.contains(diagnosticPeriodField(AnalysisPeriod.HOURS_1)))
    }

    private fun field(labelRes: Int, value: String): String = context.getString(
        R.string.report_field,
        context.getString(labelRes),
        value,
    )

    private fun periodName(period: AnalysisPeriod): String = context.getString(period.labelRes)

    private fun diagnosticPeriodField(period: AnalysisPeriod): String = field(
        R.string.label_diagnostic_period,
        context.getString(R.string.duration_hours_only, period.diagnosticHours),
    )

    private fun analysis() = UsageAnalysis(
        rangeStartMillis = 0L,
        rangeEndMillis = 86_400_000L,
        coverMillis = 43_200_000L,
        innerMillis = 43_200_000L,
        excludedPostureMillis = 0L,
        excludedPostureMillisByReason = emptyMap(),
        openedCount = 1,
        closedCount = 1,
        evidenceGapCount = 0,
        foldTransitions = emptyList(),
        dailySummaries = emptyList(),
        apps = emptyList(),
        postureEvents = emptyList(),
        eventCount = 2,
        multiResumeMillis = 0L,
    )

    private fun summary(period: AnalysisPeriod) = PeriodUsageSummary(
        period = period,
        rangeStartMillis = 0L,
        rangeEndMillis = 86_400_000L,
        coverMillis = 43_200_000L,
        innerMillis = 43_200_000L,
        excludedMillis = 0L,
        openedCount = 1,
        closedCount = 1,
        apps = emptyList(),
    )

    private fun insights() = LongTermInsights(
        rangeStartMillis = 0L,
        rangeEndMillis = 7 * 86_400_000L,
        coverMillis = 3 * 86_400_000L,
        innerMillis = 4 * 86_400_000L,
        excludedMillis = 0L,
        openedCount = 7,
        closedCount = 7,
        calendarDayCount = 7,
        observedDayCount = 7,
        innerUsedDayCount = 7,
        evidenceGapDayCount = 0,
        buckets = listOf(
            LongTermBucket(
                startMillis = 0L,
                endMillis = 7 * 86_400_000L,
                coverMillis = 3 * 86_400_000L,
                innerMillis = 4 * 86_400_000L,
                excludedMillis = 0L,
                openedCount = 7,
                closedCount = 7,
                observedDayCount = 7,
                evidenceGapDayCount = 0,
            ),
        ),
        firstThirtyDayInnerRatio = null,
        recentThirtyDayInnerRatio = null,
    )
}
