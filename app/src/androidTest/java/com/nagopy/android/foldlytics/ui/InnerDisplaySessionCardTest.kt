package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.InnerSessionAppSummary
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class InnerDisplaySessionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsMetricsTopThreeAppsAndUnclassifiedStartsInEnglish() {
        val context = localizedContext(Locale.ENGLISH)
        val sessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 10_000L,
            detectedOpenCount = 5,
            completeSessionCount = 4,
            medianInnerActiveMillis = 2_000L,
            longestInnerActiveMillis = 4_000L,
            startApps = listOf(
                app("one", "One", 2, 4_000L),
                app("two", "Two", 1, 2_000L),
                app("three", "Three", 1, 1_000L),
                app("four", "Four", 1, 500L),
            ),
            unclassifiedStartCount = 1,
        )

        setContent(context, sessionSummary)
        scrollToSessionCard()

        composeRule.onNodeWithTag(INNER_SESSION_METRICS_TAG).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.content_desc_inner_session_metrics,
                4,
                5,
                context.getString(R.string.duration_seconds, 2),
                context.getString(R.string.duration_seconds, 4),
            ),
        ).assertExists()
        composeRule.onNodeWithTag("${INNER_SESSION_APP_TAG_PREFIX}one").assertExists()
        composeRule.onNodeWithTag("${INNER_SESSION_APP_TAG_PREFIX}two").assertExists()
        composeRule.onNodeWithTag("${INNER_SESSION_APP_TAG_PREFIX}three").assertExists()
        composeRule.onNodeWithTag("${INNER_SESSION_APP_TAG_PREFIX}four").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.label_unclassified_session_starts))
            .assertExists()
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
    fun showsZeroSecondsForACompleteZeroTimeSession() {
        val context = localizedContext(Locale.ENGLISH)
        val sessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 10_000L,
            detectedOpenCount = 1,
            completeSessionCount = 1,
            medianInnerActiveMillis = 0L,
            longestInnerActiveMillis = 0L,
            startApps = emptyList(),
            unclassifiedStartCount = 1,
        )

        setContent(context, sessionSummary)
        scrollToSessionCard()

        composeRule.onNodeWithTag(INNER_SESSION_METRICS_TAG).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.content_desc_inner_session_metrics,
                1,
                1,
                context.getString(R.string.duration_seconds, 0),
                context.getString(R.string.duration_seconds, 0),
            ),
        ).assertExists()
        composeRule.onNodeWithTag(INNER_SESSION_EMPTY_TAG).assertDoesNotExist()
    }

    private fun scrollToSessionCard() {
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(INNER_SESSION_CARD_TAG),
        )
    }

    private fun setContent(context: Context, sessionSummary: InnerSessionSummary) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
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

    private fun emptySummary(detectedOpenCount: Int) = InnerSessionSummary(
        rangeStartMillis = 0L,
        rangeEndMillis = 10_000L,
        detectedOpenCount = detectedOpenCount,
        completeSessionCount = 0,
        medianInnerActiveMillis = null,
        longestInnerActiveMillis = null,
        startApps = emptyList(),
        unclassifiedStartCount = 0,
    )

    private fun app(
        packageName: String,
        label: String,
        sessionCount: Int,
        totalMillis: Long,
    ) = InnerSessionAppSummary(
        packageName = packageName,
        label = label,
        completeSessionCount = sessionCount,
        totalInnerActiveMillis = totalMillis,
        isLauncherApp = true,
    )

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
