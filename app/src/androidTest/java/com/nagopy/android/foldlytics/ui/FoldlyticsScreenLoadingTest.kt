package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import android.icu.text.ListFormatter
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.toShortDateText
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.model.LongTermInsights
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.model.availableAnalysisPeriods
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
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

        composeRule.onNodeWithTag(ANALYSIS_PERIOD_SELECTOR_TAG).assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.section_analysis_period)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.period_24_hours)).assertIsDisplayed()
        composeRule.onNodeWithText("3 years").assertDoesNotExist()
        composeRule.onNodeWithText("Recent usage").assertDoesNotExist()
        composeRule.onNodeWithText("Long-term trends").assertDoesNotExist()
    }

    @Test
    fun opensOnePeriodDropdownWithEnabledAndDisabledCandidates() {
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

        composeRule.onNodeWithTag(ANALYSIS_PERIOD_SELECTOR_TAG).performClick()

        option(AnalysisPeriod.HOURS_1).assertIsEnabled()
        option(AnalysisPeriod.HOURS_6).assertIsEnabled()
        option(AnalysisPeriod.HOURS_24).assertIsEnabled()
        option(AnalysisPeriod.DAYS_7).assertIsEnabled()
        option(AnalysisPeriod.DAYS_30).assertIsEnabled()
        option(AnalysisPeriod.DAYS_90).assertIsNotEnabled()
        option(AnalysisPeriod.DAYS_365).assertIsNotEnabled()
        option(AnalysisPeriod.CUSTOM).assertIsEnabled()
    }

    @Test
    fun opensCustomPeriodDialogFromThePeriodDropdown() {
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

        composeRule.onNodeWithTag(ANALYSIS_PERIOD_SELECTOR_TAG).performClick()
        option(AnalysisPeriod.CUSTOM).performClick()
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
    fun keepsHomeToSummaryAndOneSwitchableTrendCardWithoutDetailBreakdowns() {
        setContent(rankingState())

        val scrollable = composeRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag(USAGE_TREND_CARD_TAG))
        composeRule.onAllNodes(hasTestTag(USAGE_TREND_CARD_TAG)).assertCountEquals(1)
        composeRule.onNodeWithTag(APP_USAGE_RANKING_SELECTOR_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(INNER_SESSION_CARD_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(INNER_SESSION_LONG_SESSIONS_CARD_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.label_observed_days)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.label_period_total)).assertDoesNotExist()

        composeRule.onNodeWithTag(USAGE_TREND_OPEN_COUNT_TAG).performClick()
        composeRule.onNodeWithText(text(R.string.label_period_total)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.label_observed_days)).assertDoesNotExist()
        composeRule.onNodeWithTag(USAGE_TREND_INNER_RATIO_TAG).assertIsDisplayed()
    }

    @Test
    fun liveStateCardExposesOneCombinedAccessibilityNodeWithoutDuplicateChildAnnouncements() {
        val postureLabel = text(R.string.posture_inner)
        val recordingStatus = text(R.string.recording_status_available)
        val combinedDescription = text(R.string.content_desc_live_state_inner_recording)
        setContent(
            MainUiState(
                hasUsageAccess = true,
                currentPosture = DisplayPosture.INNER,
            ),
        )

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(LIVE_STATE_CARD_TAG),
        )
        composeRule.onNode(
            hasTestTag(LIVE_STATE_CARD_TAG) and hasContentDescription(combinedDescription),
        ).assertIsDisplayed()
        composeRule.onAllNodes(
            hasContentDescription(combinedDescription),
            useUnmergedTree = false,
        ).assertCountEquals(1)

        composeRule.onNodeWithText(postureLabel, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(recordingStatus, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodes(
            hasText(postureLabel, substring = false),
            useUnmergedTree = false,
        ).assertCountEquals(0)
        composeRule.onAllNodes(
            hasText(recordingStatus, substring = false),
            useUnmergedTree = false,
        ).assertCountEquals(0)
    }

    @Test
    fun liveStateCardUsesContextualDescriptionsForEveryPostureAndRecordingState() {
        var state by mutableStateOf(MainUiState())
        setContentWithStateProvider(stateProvider = { state })
        val cases = listOf(
            DisplayPosture.COVER to true,
            DisplayPosture.COVER to false,
            DisplayPosture.INNER to true,
            DisplayPosture.INNER to false,
            DisplayPosture.UNKNOWN to true,
            DisplayPosture.UNKNOWN to false,
        )

        cases.forEach { (posture, hasUsageAccess) ->
            composeRule.runOnIdle {
                state = MainUiState(
                    hasUsageAccess = hasUsageAccess,
                    currentPosture = posture,
                )
            }
            composeRule.waitForIdle()
            val descriptionRes = when (posture) {
                DisplayPosture.COVER -> if (hasUsageAccess) {
                    R.string.content_desc_live_state_cover_recording
                } else {
                    R.string.content_desc_live_state_cover_access_required
                }
                DisplayPosture.INNER -> if (hasUsageAccess) {
                    R.string.content_desc_live_state_inner_recording
                } else {
                    R.string.content_desc_live_state_inner_access_required
                }
                DisplayPosture.UNKNOWN -> if (hasUsageAccess) {
                    R.string.content_desc_live_state_unknown_recording
                } else {
                    R.string.content_desc_live_state_unknown_access_required
                }
            }

            composeRule.onNode(
                hasTestTag(LIVE_STATE_CARD_TAG) and hasContentDescription(text(descriptionRes)),
            ).assertIsDisplayed()
        }
    }

    @Test
    fun showsTheRenderedSummaryRangeInsteadOfThePendingCustomSelection() {
        val summaryStart = 0L
        val summaryEnd = 24L * 60L * 60L * 1_000L
        val pendingCustomStart = summaryEnd
        val pendingCustomEnd = pendingCustomStart + summaryEnd
        val summary = rankingState().periodSummary!!.copy(
            period = AnalysisPeriod.DAYS_90,
            rangeStartMillis = summaryStart,
            rangeEndMillis = summaryEnd,
        )
        setContent(
            rankingState().copy(
                selectedPeriod = AnalysisPeriod.CUSTOM,
                customRangeStartMillis = pendingCustomStart,
                customRangeEndMillis = pendingCustomEnd,
                periodSummary = summary,
                isAnalysisLoading = true,
            ),
        )

        composeRule.onNodeWithText(text(R.string.period_custom)).assertIsDisplayed()
        composeRule.onNodeWithText(
            text(
                R.string.analysis_range,
                text(R.string.period_90_days),
                summaryStart.toShortDateText(englishContext.resources),
                (summaryEnd - 1L).toShortDateText(englishContext.resources),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            text(
                R.string.selected_date_range,
                pendingCustomStart.toShortDateText(englishContext.resources),
                (pendingCustomEnd - 1L).toShortDateText(englishContext.resources),
            ),
        ).assertDoesNotExist()
    }

    @Test
    fun homeTotalPreviewMatchesTheInitialTotalRankingAfterNavigation() {
        val apps = listOf(
            AppUsage("outer-leader", "Outer leader", 800L, 0L, 0L),
            AppUsage("total-leader", "Total leader", 300L, 600L, 0L),
            AppUsage("inner-leader", "Inner leader", 0L, 700L, 0L),
        )
        setContent(
            rankingState().copy(
                periodSummary = rankingState().periodSummary!!.copy(apps = apps),
            ),
        )
        val preview = ListFormatter.getInstance(englishContext.resources.configuration.locales[0])
            .format(listOf("Total leader", "Outer leader", "Inner leader"))
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(HOME_APP_USAGE_LINK_TAG),
        )
        composeRule.onNode(
            hasTestTag(HOME_APP_USAGE_LINK_TAG) and hasContentDescription(
                "${text(R.string.content_desc_home_app_usage_link)} ${text(R.string.home_app_usage_preview, preview)}",
            ),
        ).assertExists()

        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_TOTAL_SEGMENT_TAG).assertIsSelected()
        composeRule.onNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}total-leader") and
                hasAnyDescendant(hasText("#1", substring = false)),
        ).assertExists()
    }

    @Test
    fun homeDetailLinksNavigateBackAndRestoreTheHomeScrollPosition() {
        setContent(navigationState())

        val scrollable = composeRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag(HOME_APP_USAGE_LINK_TAG))
        val appLinkTop = composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG)
            .fetchSemanticsNode().boundsInRoot.top

        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_SCREEN_TAG).assertIsDisplayed()
        composeRule.onAllNodes(
            hasText(text(R.string.app_usage_screen_title), substring = false),
        ).assertCountEquals(1)

        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).assertIsDisplayed()
        val restoredAppLinkTop = composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG)
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(appLinkTop, restoredAppLinkTop, 1f)

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(HOME_INNER_SESSIONS_LINK_TAG),
        )
        composeRule.onNodeWithTag(HOME_INNER_SESSIONS_LINK_TAG).performClick()
        composeRule.onNodeWithTag(INNER_DISPLAY_SESSION_SCREEN_TAG).assertIsDisplayed()
        composeRule.onAllNodes(
            hasText(text(R.string.inner_sessions_screen_title), substring = false),
        ).assertCountEquals(1)
        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(HOME_INNER_SESSIONS_LINK_TAG).assertIsDisplayed()
    }

    @Test
    fun systemBackReturnsFromAnAppDetailToHome() {
        setContent(navigationState())
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(HOME_APP_USAGE_LINK_TAG),
        )
        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_SCREEN_TAG).assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).assertIsDisplayed()
    }

    @Test
    fun disablesHomeDetailLinksWhileAnalysisIsLoading() {
        setContent(navigationState().copy(isAnalysisLoading = true))
        val scrollable = composeRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag(HOME_APP_USAGE_LINK_TAG))

        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).assertIsNotEnabled()
        scrollable.performScrollToNode(hasTestTag(HOME_INNER_SESSIONS_LINK_TAG))
        composeRule.onNodeWithTag(HOME_INNER_SESSIONS_LINK_TAG).assertIsNotEnabled()
    }

    private fun option(period: AnalysisPeriod) = composeRule.onNodeWithTag(
        "$ANALYSIS_PERIOD_OPTION_TAG_PREFIX${period.name}",
        useUnmergedTree = true,
    )

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

    private fun navigationState(): MainUiState = rankingState().copy(
        innerSessionSummary = InnerSessionSummary(
            rangeStartMillis = 0L,
            rangeEndMillis = 1_000L,
            detectedOpenCount = 1,
            completeSessionCount = 1,
            medianInnerActiveMillis = 500L,
            averageInnerActiveMillis = 500L,
            longestInnerActiveMillis = 500L,
            longSessions = emptyList(),
        ),
    )
}
