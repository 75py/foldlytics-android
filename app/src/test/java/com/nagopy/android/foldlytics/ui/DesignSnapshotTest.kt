package com.nagopy.android.foldlytics.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.nagopy.android.foldlytics.MainUiError
import com.nagopy.android.foldlytics.MainUiErrorKind
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.data.LongTermAnalyzer
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.DailyPostureSummary
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.InnerSessionAppUsage
import com.nagopy.android.foldlytics.model.InnerSessionDetail
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.model.LongTermPeriod
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w412dp-h915dp-420dpi"
private const val INNER_DISPLAY = "w841dp-h900dp-420dpi"

/**
 * Renders every screen with deterministic data so a design change can be reviewed without a
 * device. Images are written to app/build/design-snapshots only when recording is requested:
 * `./gradlew testDebugUnitTest -Proborazzi.test.record=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class DesignSnapshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneLight() = captureAllScreens("phone-light")

    @Test
    @Config(qualifiers = "w412dp-h915dp-night-420dpi")
    fun phoneDark() = captureAllScreens("phone-dark")

    @Test
    @Config(qualifiers = "ja-$PHONE")
    fun phoneJapanese() = captureAllScreens("phone-ja")

    @Test
    @Config(qualifiers = INNER_DISPLAY)
    fun innerDisplayLight() = captureAllScreens("inner-light")

    @Test
    @Config(qualifiers = "w320dp-h740dp-420dpi")
    fun compactLargeText() {
        setContent(representativeState(), fontScale = 1.6f)
        capture("compact-large-text", "01-home")
        scrollTo(SUMMARY_CARD_TAG)
        capture("compact-large-text", "02-summary")
        scrollTo(USAGE_TREND_CARD_TAG)
        capture("compact-large-text", "04-trend")
        scrollTo(HOME_APP_USAGE_LINK_TAG)
        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).performClick()
        capture("compact-large-text", "03-app-usage")
        scrollTo(APP_USAGE_INNER_SEGMENT_TAG)
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).performClick().assertIsSelected()
        composeRule.onNodeWithTag(APP_USAGE_TOTAL_SEGMENT_TAG).assertIsNotSelected()
        capture("compact-large-text", "05-inner-sort")
        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()
        scrollTo(HOME_INNER_SESSIONS_LINK_TAG)
        composeRule.onNodeWithTag(HOME_INNER_SESSIONS_LINK_TAG).performClick()
        capture("compact-large-text", "06-inner-sessions")
    }

    @Test
    fun selectionMotion() {
        setContent(representativeState())
        scrollTo(USAGE_TREND_CARD_TAG)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(USAGE_TREND_OPEN_COUNT_TAG).performClick()
        composeRule.mainClock.advanceTimeBy(80)
        capture("motion", "01-selection-80ms")
        composeRule.mainClock.advanceTimeBy(800)
        capture("motion", "02-selection-settled")
        composeRule.onNodeWithTag(USAGE_TREND_OPEN_COUNT_TAG).assertIsSelected()
    }

    @Test
    fun extremeDisplayShare() {
        val state = representativeState()
        setContent(state.copy(periodSummary = state.periodSummary!!.copy(
            coverMillis = minutes(1),
            innerMillis = minutes(999),
        )))
        scrollTo(SUMMARY_CARD_TAG)
        capture("edge-cases", "01-one-tenth-percent-cover")
    }

    @Test
    fun emptySummary() {
        val state = representativeState()
        setContent(state.copy(periodSummary = state.periodSummary!!.copy(
            coverMillis = 0,
            innerMillis = 0,
            excludedMillis = 0,
            openedCount = 0,
            closedCount = 0,
            apps = emptyList(),
        ), longTermInsights = null, innerSessionSummary = null))
        scrollTo(SUMMARY_CARD_TAG)
        capture("edge-cases", "02-empty")
    }

    @Test
    fun phoneStates() {
        setContent(
            representativeState().copy(
                hasUsageAccess = false,
                currentPosture = DisplayPosture.COVER,
                isAnalysisLoading = true,
                error = MainUiError(MainUiErrorKind.SYNC, "Usage events could not be read."),
            ),
        )
        // The progress indicator appears only after its anti-flicker delay.
        composeRule.mainClock.advanceTimeBy(1_000L)
        capture("phone-states", "01-home-permission-error")
    }


    @Test
    fun firstLaunch() {
        setContent(MainUiState())
        capture("phone-states", "02-first-launch")
    }

    @Test
    fun recordDetailsAreAvailableOnDemand() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContent(representativeState())
        scrollTo(RECORD_DETAILS_TAG)
        composeRule.onNodeWithTag(LIVE_STATE_CARD_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(RECORD_DETAILS_TAG).performClick()
        scrollTo(LIVE_STATE_CARD_TAG)
        composeRule.onNodeWithTag(LIVE_STATE_CARD_TAG).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.display_classification_rate_note)).assertExists()
        capture("phone-states", "03-record-details")
        scrollTo(RECORD_DETAILS_TAG)
        composeRule.onNodeWithTag(RECORD_DETAILS_TAG).performClick()
        composeRule.onNodeWithTag(LIVE_STATE_CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun chartInspectionShowsTheSelectedPeriod() {
        setContent(representativeState())
        scrollTo(USAGE_TREND_CARD_TAG)
        composeRule.onNodeWithTag(TREND_PLOT_TAG).performTouchInput { click(center) }
        capture("phone-states", "04-chart-inspection")
    }

    @Test
    fun sharedImageFitsAllText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = SummaryShareImageRenderer.renderWithDiagnostics(context.resources, representativeState().periodSummary!!)
        assertTrue(result.textMeasurements.filterNot { it.fits }.toString(), result.textMeasurements.all { it.fits })
        if (System.getProperty("roborazzi.test.record") == "true") {
            val output = File("build/design-snapshots/shared/summary.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        result.bitmap.recycle()
    }

    private fun captureAllScreens(group: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setContent(representativeState())
        capture(group, "01-home")

        scrollTo(USAGE_TREND_CARD_TAG)
        capture(group, "02-home-trend")
        composeRule.onNodeWithTag(USAGE_TREND_OPEN_COUNT_TAG).performClick()
        scrollTo(HOME_INNER_SESSIONS_LINK_TAG)
        capture(group, "03-home-links")

        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).performClick()
        capture(group, "04-app-usage")
        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).performClick()
        scrollTo("${APP_USAGE_CARD_TAG_PREFIX}demo.photos")
        capture(group, "05-app-usage-share")
        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()

        scrollTo(HOME_INNER_SESSIONS_LINK_TAG)
        composeRule.onNodeWithTag(HOME_INNER_SESSIONS_LINK_TAG).performClick()
        capture(group, "06-inner-sessions")
        scrollTo(INNER_SESSION_LONG_SESSIONS_CARD_TAG)
        capture(group, "07-inner-sessions-long")
        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()

        val openMenu = context.getString(R.string.content_desc_open_menu)
        composeRule.onNodeWithContentDescription(openMenu).performClick()
        capture(group, "08-drawer")
        composeRule.onNodeWithText(context.getString(R.string.screen_calibration)).performClick()
        capture(group, "09-calibration")
        composeRule.onNodeWithContentDescription(openMenu).performClick()
        composeRule.onNodeWithText(context.getString(R.string.screen_diagnostics)).performClick()
        capture(group, "10-diagnostics")
    }

    private fun setContent(state: MainUiState, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FoldlyticsTheme {
                    FoldlyticsScreen(
                        state = state,
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
                        appName = "Foldlytics",
                    )
                }
            }
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun capture(group: String, name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/design-snapshots/$group/$name.png")
    }

    private fun representativeState(): MainUiState {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val recordEndMillis = RECORD_END.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dailySummaries = representativeDailySummaries(zoneId)
        val insights = LongTermAnalyzer().analyze(
            summaries = dailySummaries,
            period = LongTermPeriod.DAYS_90,
            rangeEndMillis = recordEndMillis,
            zoneId = zoneId,
        )
        val apps = listOf(
            app("demo.browser", "Browser", insights.coverMillis, 24, insights.innerMillis, 16),
            app("demo.photos", "Photos", insights.coverMillis, 11, insights.innerMillis, 14),
            app("demo.reader", "Reading", insights.coverMillis, 7, insights.innerMillis, 27),
            app("demo.messages", "Messages", insights.coverMillis, 31, insights.innerMillis, 6),
            app("demo.maps", "Maps", insights.coverMillis, 9, insights.innerMillis, 3),
        )
        return MainUiState(
            hasUsageAccess = true,
            currentPosture = DisplayPosture.INNER,
            selectedPeriod = AnalysisPeriod.DAYS_90,
            availablePeriods = AnalysisPeriod.entries.toSet(),
            recordRangeStartMillis = dailySummaries.first().dayStartMillis,
            recordRangeEndMillis = recordEndMillis,
            periodSummary = PeriodUsageSummary(
                period = AnalysisPeriod.DAYS_90,
                rangeStartMillis = insights.rangeStartMillis,
                rangeEndMillis = insights.rangeEndMillis,
                coverMillis = insights.coverMillis,
                innerMillis = insights.innerMillis,
                excludedMillis = insights.excludedMillis,
                openedCount = insights.openedCount,
                closedCount = insights.closedCount,
                apps = apps,
            ),
            innerSessionSummary = representativeInnerSessionSummary(
                rangeStartMillis = insights.rangeStartMillis,
                rangeEndMillis = insights.rangeEndMillis,
                detectedOpenCount = insights.openedCount,
            ),
            longTermInsights = insights,
            lastSuccessfulSyncMillis = recordEndMillis - minutes(2L),
        )
    }

    private fun representativeDailySummaries(zoneId: ZoneId): List<DailyPostureSummary> =
        (0L until RECORD_DAY_COUNT).map { dayOffset ->
            val date = RECORD_END.minusDays(RECORD_DAY_COUNT - dayOffset)
            val weekendBoost = if (
                date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            ) {
                18L
            } else {
                0L
            }
            val recentDayOffset = dayOffset - (RECORD_DAY_COUNT - TREND_DAY_COUNT)
            val recentInnerTrendAdjustment = if (recentDayOffset >= 0L) {
                recentDayOffset - TREND_DAY_COUNT / 2L
            } else {
                0L
            }
            DailyPostureSummary(
                dayStartMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                dayEndMillis = date.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli(),
                zoneId = zoneId.id,
                coverMillis = minutes(72L + (dayOffset * 17L % 41L) + weekendBoost),
                innerMillis = minutes(
                    118L + (dayOffset * 29L % 67L) + weekendBoost + dayOffset / 18L +
                        recentInnerTrendAdjustment,
                ),
                excludedMillis = minutes(3L + (dayOffset * 7L % 8L)),
                openedCount = 8 + (dayOffset * 5L % 10L).toInt(),
                closedCount = 8 + (dayOffset * 5L % 10L).toInt(),
                evidenceGapCount = if (dayOffset % 29L == 0L) 1 else 0,
            )
        }

    private fun representativeInnerSessionSummary(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        detectedOpenCount: Int,
    ): InnerSessionSummary {
        val dayMillis = minutes(24L * 60L)
        return InnerSessionSummary(
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            detectedOpenCount = detectedOpenCount,
            completeSessionCount = detectedOpenCount - 15,
            medianInnerActiveMillis = minutes(18L),
            averageInnerActiveMillis = minutes(22L),
            longestInnerActiveMillis = minutes(42L),
            longSessions = listOf(
                InnerSessionDetail(
                    openedAtMillis = rangeStartMillis + dayMillis * 5L + minutes(9L * 60L + 15L),
                    openedSequenceAtTimestamp = 0,
                    innerActiveMillis = minutes(42L),
                    appUsages = listOf(
                        InnerSessionAppUsage("demo.reader", "Reading", minutes(21L)),
                        InnerSessionAppUsage("demo.browser", "Browser", minutes(12L)),
                        InnerSessionAppUsage("demo.photos", "Photos", minutes(5L)),
                    ),
                    otherInnerActiveMillis = minutes(4L),
                ),
                InnerSessionDetail(
                    openedAtMillis = rangeStartMillis + dayMillis * 22L + minutes(14L * 60L + 40L),
                    openedSequenceAtTimestamp = 0,
                    innerActiveMillis = minutes(34L),
                    appUsages = listOf(
                        InnerSessionAppUsage("demo.browser", "Browser", minutes(14L)),
                        InnerSessionAppUsage("demo.messages", "Messages", minutes(9L)),
                    ),
                    otherInnerActiveMillis = minutes(11L),
                ),
            ),
        )
    }

    private fun app(
        packageName: String,
        label: String,
        coverTotal: Long,
        coverPercent: Int,
        innerTotal: Long,
        innerPercent: Int,
    ): AppUsage = AppUsage(
        packageName = packageName,
        label = label,
        coverMillis = coverTotal * coverPercent / 100L,
        innerMillis = innerTotal * innerPercent / 100L,
        excludedMillis = 0L,
    )

    private fun minutes(value: Long): Long = value * 60_000L

    private companion object {
        const val RECORD_DAY_COUNT = 365L
        const val TREND_DAY_COUNT = 90L
        val RECORD_END: LocalDate = LocalDate.of(2026, 8, 16)
    }
}
