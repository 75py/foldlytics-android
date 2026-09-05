package com.nagopy.android.foldlytics

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.InnerSessionAppUsage
import com.nagopy.android.foldlytics.model.InnerSessionDetail
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.UsageAnalysis
import java.time.Instant
import java.time.ZoneOffset
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
    fun reportIdentifiesDisplayedSessionsWithExactMillisecondsAndOtherTime() {
        val report = format(
            MainUiState(
                innerSessionSummary = InnerSessionSummary(
                    rangeStartMillis = 0,
                    rangeEndMillis = 20_000,
                    detectedOpenCount = 2,
                    completeSessionCount = 1,
                    medianInnerActiveMillis = 9_001,
                    averageInnerActiveMillis = 9_001,
                    longestInnerActiveMillis = 9_001,
                    longSessions = listOf(
                        InnerSessionDetail(
                            openedAtMillis = 1_234,
                            openedSequenceAtTimestamp = 3,
                            innerActiveMillis = 9_001,
                            otherInnerActiveMillis = 8_000,
                            appUsages = listOf(
                                InnerSessionAppUsage("app.reader", "Reader", 1_001),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(report.contains(context.getString(R.string.report_inner_sessions_heading)))
        assertTrue(report.contains(context.getString(R.string.report_session_duration, 9_001L, 8_000L)))
        assertTrue(report.contains(context.getString(R.string.report_session_app, "Reader", "app.reader", 1_001L)))
        assertTrue(report.contains("1234 ms"))
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
