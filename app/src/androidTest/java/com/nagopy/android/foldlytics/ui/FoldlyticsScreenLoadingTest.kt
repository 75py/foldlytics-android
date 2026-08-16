package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.availableAnalysisPeriods
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FoldlyticsScreenLoadingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val englishContext: Context by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        context.createConfigurationContext(configuration)
    }

    @Test
    fun showsGlobalProgressOnlyAfterAnalysisTakesLongerThanDelay() {
        var isLoading by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        setContent { isLoading }

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_analysis_progress))
            .assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(ANALYSIS_PROGRESS_DELAY_MILLIS + 100L)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_analysis_progress))
            .assertIsDisplayed()

        composeRule.runOnIdle { isLoading = false }
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_analysis_progress))
            .assertDoesNotExist()
    }

    @Test
    fun doesNotFlashProgressForShortAnalysis() {
        var isLoading by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        setContent { isLoading }

        composeRule.mainClock.advanceTimeBy(ANALYSIS_PROGRESS_DELAY_MILLIS / 2L)
        composeRule.runOnIdle { isLoading = false }
        composeRule.mainClock.advanceTimeBy(ANALYSIS_PROGRESS_DELAY_MILLIS + 100L)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_analysis_progress))
            .assertDoesNotExist()
    }

    @Test
    fun keepsTheAppBarConciseAndMovesSecondaryActionsIntoTheDrawer() {
        setContent { false }

        composeRule.onNodeWithText("Pixel Fold 開閉利用を端末内で分析")
            .assertDoesNotExist()
        composeRule.onNodeWithText("現在状態から計測開始").assertDoesNotExist()

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu)).performClick()

        composeRule.onNodeWithText(text(R.string.action_save_all_csv)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_share_diagnostic_report)).assertIsDisplayed()
    }

    @Test
    fun opensTheLazyDiagnosticsDestinationFromTheDrawer() {
        setContent { false }

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu)).performClick()
        composeRule.onNodeWithText(text(R.string.screen_diagnostics)).performClick()

        composeRule.onNodeWithText(text(R.string.diagnostics_overview_title)).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(text(R.string.posture_events_title)),
        )
        composeRule.onNodeWithText(text(R.string.posture_events_title)).assertIsDisplayed()
    }

    @Test
    fun opensPrivacyPolicyFromTheDrawer() {
        var opened = false
        setContentWithStateProvider(
            stateProvider = { MainUiState() },
            onOpenPrivacyPolicy = { opened = true },
        )

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu)).performClick()
        composeRule.onNodeWithText(text(R.string.action_privacy_policy)).performClick()

        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun opensOssLicensesFromTheDrawer() {
        var opened = false
        setContentWithStateProvider(
            stateProvider = { MainUiState() },
            onOpenOssLicenses = { opened = true },
        )

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu)).performClick()
        composeRule.onNodeWithText(text(R.string.action_open_source_licenses)).performClick()

        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun explainsThatDeviceSpecificCalibrationIsOptional() {
        setContent { false }

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu)).performClick()
        composeRule.onNodeWithText(text(R.string.screen_calibration)).performClick()

        composeRule.onNodeWithText(text(R.string.calibration_intro_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.calibration_values_title)).assertIsDisplayed()
    }

    @Test
    fun usesOnePeriodSelectorForShortAndLongRanges() {
        setContent { false }

        composeRule.onNodeWithText(text(R.string.section_analysis_period)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.period_24_hours)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.period_7_days)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.period_custom)).assertIsDisplayed()
        composeRule.onNodeWithText("3 years").assertDoesNotExist()
        composeRule.onNodeWithText("Recent usage").assertDoesNotExist()
        composeRule.onNodeWithText("Long-term trends").assertDoesNotExist()
    }

    @Test
    fun enablesOnlyFixedPeriodsCoveredByTheRecordedRange() {
        val zoneId = ZoneOffset.UTC
        val rangeStart = LocalDate.of(2026, 1, 1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val rangeEnd = LocalDate.of(2026, 2, 10)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        setContent(
            MainUiState(
                recordRangeStartMillis = rangeStart,
                recordRangeEndMillis = rangeEnd,
                availablePeriods = availableAnalysisPeriods(rangeStart, rangeEnd, zoneId),
            ),
        )

        composeRule.onNodeWithText(text(R.string.period_7_days)).assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.period_30_days)).assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.period_90_days)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.period_1_year)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.period_custom)).assertIsEnabled().performClick()
        composeRule.waitForIdle()
        val dialogTitle = composeRule.onNodeWithTag(
            CUSTOM_PERIOD_DIALOG_TITLE_TAG,
            useUnmergedTree = true,
        )
        val dialogGuidance = composeRule.onNodeWithTag(
            CUSTOM_PERIOD_DIALOG_GUIDANCE_TAG,
            useUnmergedTree = true,
        )
        val cancelButton = composeRule.onNodeWithTag(CUSTOM_PERIOD_DIALOG_CANCEL_TAG)
        val applyButton = composeRule.onNodeWithTag(CUSTOM_PERIOD_DIALOG_APPLY_TAG)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            dialogTitle.isDisplayed() &&
                dialogGuidance.isDisplayed() &&
                cancelButton.isDisplayed() &&
                applyButton.isDisplayed()
        }
        dialogTitle.assertIsDisplayed()
        dialogGuidance.assertIsDisplayed()
        cancelButton.assertIsDisplayed()
        applyButton.assertIsDisplayed()
        assertTrue(
            "Custom period guidance overlaps the dialog title",
            dialogGuidance.fetchSemanticsNode().boundsInRoot.top >=
                dialogTitle.fetchSemanticsNode().boundsInRoot.bottom,
        )
    }

    @Test
    fun scopesTheDisplayRankingToTheAppSectionAfterUsageTrends() {
        setContent(rankingState())

        val scrollable = composeRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasText(text(R.string.section_usage_trends)))
        composeRule.onNodeWithText(text(R.string.section_usage_trends)).assertIsDisplayed()

        scrollable.performScrollToNode(hasText(text(R.string.app_ranking_title)))
        composeRule.onNodeWithText(text(R.string.app_ranking_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Share by app").assertDoesNotExist()
        scrollable.performScrollToNode(
            hasContentDescription(text(R.string.content_desc_cover_app_ranking)),
        )
        composeRule.onNodeWithContentDescription(
            text(R.string.content_desc_cover_app_ranking),
        )
            .assertIsDisplayed()
        val outerOrder = text(
            R.string.app_ranking_order,
            text(R.string.posture_cover),
        )
        scrollable.performScrollToNode(hasText(outerOrder))
        composeRule.onNodeWithText(outerOrder).assertIsDisplayed()

        scrollable.performScrollToNode(
            hasContentDescription(text(R.string.content_desc_inner_app_ranking)),
        )
        composeRule.onNodeWithContentDescription(
            text(R.string.content_desc_inner_app_ranking),
        ).performClick()

        val innerOrder = text(
            R.string.app_ranking_order,
            text(R.string.posture_inner),
        )
        scrollable.performScrollToNode(hasText(innerOrder))
        composeRule.onNodeWithText(innerOrder).assertIsDisplayed()
    }

    private fun setContent(isLoading: () -> Boolean) {
        setContentWithStateProvider(
            stateProvider = { MainUiState(isAnalysisLoading = isLoading()) },
        )
    }

    private fun setContent(state: MainUiState) {
        setContentWithStateProvider(stateProvider = { state })
    }

    private fun setContentWithStateProvider(
        stateProvider: () -> MainUiState,
        onOpenPrivacyPolicy: () -> Unit = {},
        onOpenOssLicenses: () -> Unit = {},
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides englishContext,
                LocalConfiguration provides englishContext.resources.configuration,
            ) {
                FoldlyticsTheme {
                    FoldlyticsScreen(
                        state = stateProvider(),
                        onOpenUsageAccess = {},
                        onSaveCover = {},
                        onSaveInner = {},
                        onClearCalibration = {},
                        onPeriodChanged = {},
                        onCustomPeriodChanged = { _, _ -> },
                        onRefresh = {},
                        onShare = {},
                        onExportCsv = {},
                        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                        onOpenOssLicenses = onOpenOssLicenses,
                    )
                }
            }
        }
    }

    private fun text(resourceId: Int, vararg formatArgs: Any): String =
        englishContext.getString(resourceId, *formatArgs)

    private fun rankingState(): MainUiState {
        val bucket = LongTermBucket(
            startMillis = 0L,
            endMillis = 1_000L,
            coverMillis = 600L,
            innerMillis = 400L,
            excludedMillis = 0L,
            openedCount = 1,
            closedCount = 0,
            observedDayCount = 1,
            evidenceGapDayCount = 0,
        )
        val apps = listOf(
            AppUsage(
                packageName = "cover.app",
                label = "外側アプリ",
                coverMillis = 600L,
                innerMillis = 100L,
                excludedMillis = 0L,
            ),
            AppUsage(
                packageName = "inner.app",
                label = "内側アプリ",
                coverMillis = 100L,
                innerMillis = 300L,
                excludedMillis = 0L,
            ),
        )
        return MainUiState(
            hasUsageAccess = true,
            selectedPeriod = AnalysisPeriod.DAYS_7,
            periodSummary = PeriodUsageSummary(
                period = AnalysisPeriod.DAYS_7,
                rangeStartMillis = 0L,
                rangeEndMillis = 1_000L,
                coverMillis = 600L,
                innerMillis = 400L,
                excludedMillis = 0L,
                openedCount = 1,
                closedCount = 0,
                apps = apps,
            ),
            longTermInsights = LongTermInsights(
                rangeStartMillis = 0L,
                rangeEndMillis = 1_000L,
                coverMillis = 600L,
                innerMillis = 400L,
                excludedMillis = 0L,
                openedCount = 1,
                closedCount = 0,
                calendarDayCount = 1,
                observedDayCount = 1,
                innerUsedDayCount = 1,
                evidenceGapDayCount = 0,
                buckets = listOf(bucket),
                firstThirtyDayInnerRatio = null,
                recentThirtyDayInnerRatio = null,
            ),
        )
    }
}
