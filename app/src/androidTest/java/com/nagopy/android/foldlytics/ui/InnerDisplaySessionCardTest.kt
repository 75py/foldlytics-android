package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.toInnerSessionStartText
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.InnerSessionAppUsage
import com.nagopy.android.foldlytics.model.InnerSessionDetail
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InnerDisplaySessionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun includesYearAndStopsAtMinutesInSessionStartForJapaneseAndEnglish() {
        val timestampMillis = Instant.parse("2026-08-16T12:00:00Z").toEpochMilli()

        assertTrue(
            timestampMillis.toInnerSessionStartText(localizedContext(Locale.ENGLISH).resources)
                .matches(Regex("\\d{1,2}/\\d{1,2}/2026 \\d{2}:\\d{2}")),
        )
        assertTrue(
            timestampMillis.toInnerSessionStartText(localizedContext(Locale.JAPANESE).resources)
                .matches(Regex("2026/\\d{1,2}/\\d{1,2} \\d{2}:\\d{2}")),
        )
    }

    @Test
    fun showsEnglishMetricsLongSessionsTopThreeAppsAndOther() {
        val context = localizedContext(Locale.ENGLISH)
        val sessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 10_000L,
            detectedOpenCount = 5,
            completeSessionCount = 4,
            medianInnerActiveMillis = 2_000L,
            averageInnerActiveMillis = 2_500L,
            longestInnerActiveMillis = 4_000L,
            longSessions = listOf(
                detail(
                    openedAtMillis = 1_000L,
                    innerActiveMillis = 4_000L,
                    appUsages = listOf(
                        app("one", "One", 2_000L),
                        app("two", "Two", 1_000L),
                        app("three", "Three", 500L),
                    ),
                    otherInnerActiveMillis = 500L,
                ),
                detail(
                    openedAtMillis = 2_000L,
                    innerActiveMillis = 3_000L,
                    appUsages = listOf(app("four", "Four", 2_000L)),
                    otherInnerActiveMillis = 1_000L,
                ),
                detail(
                    openedAtMillis = 3_000L,
                    innerActiveMillis = 2_500L,
                    appUsages = emptyList(),
                    otherInnerActiveMillis = 2_500L,
                ),
            ),
        )

        setContent(context, sessionSummary)
        scrollToSessionCard()

        composeRule.onNodeWithText(context.getString(R.string.inner_sessions_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.inner_sessions_description))
            .assertExists()
        composeRule.onNodeWithTag(INNER_SESSION_COUNT_TAG).assertExists()
        composeRule.onNodeWithText(
            "Inner-display uses summarized: 4. Openings detected: 5.",
        ).assertExists()
        composeRule.onNodeWithTag(INNER_SESSION_METRICS_TAG).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.content_desc_inner_session_metrics,
                4,
                5,
                context.getString(R.string.duration_seconds, 2),
                context.getString(R.string.duration_seconds, 2),
                context.getString(R.string.duration_seconds, 4),
            ),
        ).assertExists()
        val firstDetailDescription = context.getString(
            R.string.content_desc_inner_session_detail,
            1_000L.toInnerSessionStartText(context.resources),
            context.getString(R.string.duration_seconds, 4),
            listOf(
                context.getString(
                    R.string.content_desc_inner_session_app,
                    "One",
                    context.getString(R.string.duration_seconds, 2),
                ),
                context.getString(
                    R.string.content_desc_inner_session_app,
                    "Two",
                    context.getString(R.string.duration_seconds, 1),
                ),
                context.getString(
                    R.string.content_desc_inner_session_app,
                    "Three",
                    context.getString(R.string.duration_seconds, 0),
                ),
            ).joinToString(
                context.getString(R.string.content_desc_inner_session_app_separator),
            ),
            context.getString(R.string.duration_seconds, 0),
        )
        composeRule.onAllNodes(
            hasContentDescription(firstDetailDescription),
            useUnmergedTree = false,
        ).assertCountEquals(1)
        composeRule.onAllNodes(
            hasText("One"),
            useUnmergedTree = false,
        ).assertCountEquals(0)
        composeRule.onAllNodes(
            hasText(context.getString(R.string.inner_session_other)),
            useUnmergedTree = false,
        ).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.long_inner_sessions_title))
            .assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.inner_session_other_description),
        ).assertExists()
        composeRule.onNodeWithTag(
            "${INNER_SESSION_APP_TAG_PREFIX}one",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            "${INNER_SESSION_APP_TAG_PREFIX}two",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            "${INNER_SESSION_APP_TAG_PREFIX}three",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            "${INNER_SESSION_APP_TAG_PREFIX}four",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            "${INNER_SESSION_OTHER_TAG_PREFIX}1000_0",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun explainsEnglishEmptyStateWhenNoOpenWasDetected() {
        val context = localizedContext(Locale.ENGLISH)

        setContent(context, emptySummary(detectedOpenCount = 0))
        scrollToSessionCard()

        composeRule.onNodeWithTag(INNER_SESSION_EMPTY_TAG).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.inner_sessions_empty_no_opens))
            .assertExists()
        composeRule.onNodeWithTag(INNER_SESSION_METRICS_TAG).assertDoesNotExist()
    }

    @Test
    fun explainsJapaneseEmptyStateWhenOpenHasNoCompleteSession() {
        val context = localizedContext(Locale.JAPANESE)

        setContent(context, emptySummary(detectedOpenCount = 2))
        scrollToSessionCard()

        composeRule.onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.inner_sessions_empty_no_complete,
                2,
                2,
            ),
        ).assertExists()
        composeRule.onNodeWithTag(INNER_SESSION_METRICS_TAG).assertDoesNotExist()
    }

    @Test
    fun showsJapaneseSummaryLabelsAndOtherTime() {
        val context = localizedContext(Locale.JAPANESE)
        val sessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 10_000L,
            detectedOpenCount = 2,
            completeSessionCount = 1,
            medianInnerActiveMillis = 1_000L,
            averageInnerActiveMillis = 1_000L,
            longestInnerActiveMillis = 1_000L,
            longSessions = listOf(
                detail(
                    openedAtMillis = 1_000L,
                    innerActiveMillis = 1_000L,
                    appUsages = emptyList(),
                    otherInnerActiveMillis = 1_000L,
                ),
            ),
        )

        setContent(context, sessionSummary)
        scrollToSessionCard()

        composeRule.onNodeWithText(context.getString(R.string.inner_sessions_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.inner_sessions_description))
            .assertExists()
        composeRule.onNodeWithTag(INNER_SESSION_METRICS_TAG).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.content_desc_inner_session_metrics,
                1,
                2,
                context.getString(R.string.duration_seconds, 1),
                context.getString(R.string.duration_seconds, 1),
                context.getString(R.string.duration_seconds, 1),
            ),
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.content_desc_inner_session_detail,
                1_000L.toInnerSessionStartText(context.resources),
                context.getString(R.string.duration_seconds, 1),
                context.getString(R.string.content_desc_inner_session_no_apps),
                context.getString(R.string.duration_seconds, 1),
            ),
            useUnmergedTree = false,
        ).assertExists()
        composeRule.onNodeWithTag(
            "${INNER_SESSION_OTHER_TAG_PREFIX}1000_0",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun showsZeroSecondsForACompleteZeroTimeSessionWithoutLongSession() {
        val context = localizedContext(Locale.ENGLISH)
        val sessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 10_000L,
            detectedOpenCount = 1,
            completeSessionCount = 1,
            medianInnerActiveMillis = 0L,
            averageInnerActiveMillis = 0L,
            longestInnerActiveMillis = 0L,
            longSessions = emptyList(),
        )

        setContent(context, sessionSummary)
        scrollToSessionCard()

        composeRule.onNodeWithTag(INNER_SESSION_METRICS_TAG).assertExists()
        composeRule.onNodeWithText(
            "Inner-display uses summarized: 1. Openings detected: 1.",
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            "Inner-display uses summarized: 1. Openings detected: 1. " +
                "Median ${context.getString(R.string.duration_seconds, 0)}. " +
                "Average ${context.getString(R.string.duration_seconds, 0)}. " +
                "Longest ${context.getString(R.string.duration_seconds, 0)}.",
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.long_inner_sessions_empty))
            .assertExists()
    }

    @Test
    fun hidesAndOmitsOtherWhenNoTimeRemainsAfterDisplayedApps() {
        val context = localizedContext(Locale.ENGLISH)
        val sessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 10_000L,
            detectedOpenCount = 1,
            completeSessionCount = 1,
            medianInnerActiveMillis = 1_000L,
            averageInnerActiveMillis = 1_000L,
            longestInnerActiveMillis = 1_000L,
            longSessions = listOf(
                detail(
                    openedAtMillis = 1_000L,
                    innerActiveMillis = 1_000L,
                    appUsages = listOf(app("one", "One", 1_000L)),
                    otherInnerActiveMillis = 0L,
                ),
            ),
        )

        setContent(context, sessionSummary)
        scrollToSessionCard()

        composeRule.onNodeWithTag(
            "${INNER_SESSION_OTHER_TAG_PREFIX}1000_0",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        composeRule.onNodeWithTag(
            INNER_SESSION_OTHER_DESCRIPTION_TAG,
            useUnmergedTree = true,
        ).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.content_desc_inner_session_detail_without_other,
                1_000L.toInnerSessionStartText(context.resources),
                context.getString(R.string.duration_seconds, 1),
                context.getString(
                    R.string.content_desc_inner_session_app,
                    "One",
                    context.getString(R.string.duration_seconds, 1),
                ),
            ),
            useUnmergedTree = false,
        ).assertExists()
    }

    @Test
    fun stacksSessionMetricsAtNarrowWidthWithoutDroppingBreakdown() {
        val context = localizedContext(Locale.ENGLISH)
        val sessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 10_000L,
            detectedOpenCount = 1,
            completeSessionCount = 1,
            medianInnerActiveMillis = 2_000L,
            averageInnerActiveMillis = 2_000L,
            longestInnerActiveMillis = 2_000L,
            longSessions = listOf(
                detail(
                    openedAtMillis = 1_000L,
                    innerActiveMillis = 2_000L,
                    appUsages = listOf(app("narrow.app", "Narrow app", 1_000L)),
                    otherInnerActiveMillis = 1_000L,
                ),
            ),
        )

        setContent(context, sessionSummary, widthDp = 320)
        scrollToSessionCard()

        composeRule.onNodeWithText(
            context.getString(R.string.label_median_inner_session_time),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            "${INNER_SESSION_APP_TAG_PREFIX}narrow.app",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.inner_session_other),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun scrollToSessionCard() {
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(INNER_SESSION_CARD_TAG),
        )
    }

    private fun setContent(
        context: Context,
        sessionSummary: InnerSessionSummary,
        widthDp: Int? = null,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    Box(
                        modifier = widthDp?.let { Modifier.width(it.dp) } ?: Modifier,
                    ) {
                        FoldlyticsScreen(
                            state = MainUiState(
                                hasUsageAccess = true,
                                periodSummary = PeriodUsageSummary(
                                    period = AnalysisPeriod.HOURS_24,
                                    rangeStartMillis = sessionSummary.rangeStartMillis,
                                    rangeEndMillis = sessionSummary.rangeEndMillis,
                                    coverMillis = 1_000L,
                                    innerMillis = 1_000L,
                                    excludedMillis = 0L,
                                    openedCount = sessionSummary.detectedOpenCount,
                                    closedCount = sessionSummary.completeSessionCount,
                                    apps = emptyList(),
                                ),
                                innerSessionSummary = sessionSummary,
                            ),
                            onOpenUsageAccess = {},
                            onSaveCover = {},
                            onSaveInner = {},
                            onClearCalibration = {},
                            onPeriodChanged = {},
                            onCustomPeriodChanged = { _, _ -> },
                            onRefresh = {},
                            onShare = {},
                            onExportCsv = {},
                            onOpenPrivacyPolicy = {},
                            onOpenOssLicenses = {},
                        )
                    }
                }
            }
        }
    }

    private fun emptySummary(detectedOpenCount: Int) = InnerSessionSummary(
        rangeStartMillis = 0L,
        rangeEndMillis = 10_000L,
        detectedOpenCount = detectedOpenCount,
        completeSessionCount = 0,
        medianInnerActiveMillis = null,
        averageInnerActiveMillis = null,
        longestInnerActiveMillis = null,
        longSessions = emptyList(),
    )

    private fun detail(
        openedAtMillis: Long,
        innerActiveMillis: Long,
        appUsages: List<InnerSessionAppUsage>,
        otherInnerActiveMillis: Long,
    ) = InnerSessionDetail(
        openedAtMillis = openedAtMillis,
        openedSequenceAtTimestamp = 0,
        innerActiveMillis = innerActiveMillis,
        appUsages = appUsages,
        otherInnerActiveMillis = otherInnerActiveMillis,
    )

    private fun app(
        packageName: String,
        label: String,
        innerActiveMillis: Long,
    ) = InnerSessionAppUsage(
        packageName = packageName,
        label = label,
        innerActiveMillis = innerActiveMillis,
    )

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
