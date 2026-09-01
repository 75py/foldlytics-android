package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test

/**
 * Renders deterministic representative data for Google Play screenshots.
 *
 * The fixture is confined to androidTest. It is never packaged into the release application and
 * does not alter a user's usage history.
 */
class StoreScreenshotCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val targetContext: Context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }
    private val japaneseContext: Context by lazy {
        localizedContext(Locale.JAPANESE)
    }
    private val englishContext: Context by lazy {
        localizedContext(Locale.US)
    }

    @Test
    fun captureJapanesePhoneScreenshots() {
        capturePhoneScreenshots(
            context = japaneseContext,
            state = representativeState(
                AppLabels(
                    browser = "ブラウザ",
                    messages = "メッセージ",
                    maps = "地図",
                    photos = "写真",
                    reading = "読書",
                ),
            ),
            outputDirectory = JAPANESE_OUTPUT_DIRECTORY,
        )
    }

    @Test
    fun captureEnglishPhoneScreenshots() {
        capturePhoneScreenshots(
            context = englishContext,
            state = representativeState(
                AppLabels(
                    browser = "Browser",
                    messages = "Messages",
                    maps = "Maps",
                    photos = "Photos",
                    reading = "Reading",
                ),
            ),
            outputDirectory = ENGLISH_OUTPUT_DIRECTORY,
        )
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(locale)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_NO
        }
        return targetContext.createConfigurationContext(configuration)
    }

    private fun capturePhoneScreenshots(
        context: Context,
        state: MainUiState,
        outputDirectory: String,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
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

        scrollTo(SUMMARY_CARD_TAG)
        capture("01-home-summary", outputDirectory)

        scrollTo(HOME_INNER_SESSIONS_LINK_TAG)
        composeRule.onNodeWithTag(HOME_INNER_SESSIONS_LINK_TAG).performClick()
        composeRule.onNodeWithTag(INNER_DISPLAY_SESSION_SCREEN_TAG).assertExists()
        scrollTo(INNER_SESSION_LONG_SESSIONS_CARD_TAG)
        capture("02-session-details", outputDirectory)

        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()
        scrollTo(USAGE_TREND_CARD_TAG)
        capture("03-inner-ratio-trend", outputDirectory)

        composeRule.onNodeWithTag(USAGE_TREND_OPEN_COUNT_TAG).performClick()
        capture("04-open-count-trend", outputDirectory)

        scrollTo(HOME_APP_USAGE_LINK_TAG)
        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_SCREEN_TAG).assertExists()
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).performClick()
        scrollTo("${APP_USAGE_CARD_TAG_PREFIX}demo.reader")
        capture("05-inner-app-ranking", outputDirectory)

        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.content_desc_open_menu),
        ).performClick()
        capture("06-drawer", outputDirectory)
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun representativeState(appLabels: AppLabels): MainUiState {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val recordEndDate = LocalDate.of(2026, 8, 16)
        val recordEndMillis = recordEndDate
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val dailySummaries = (0L until RECORD_DAY_COUNT).map { dayOffset ->
            val date = recordEndDate.minusDays(RECORD_DAY_COUNT - dayOffset)
            val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val weekendBoost = if (
                date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            ) {
                18L
            } else {
                0L
            }
            val longTermInnerBoost = dayOffset / 18L
            val recentDayOffset = dayOffset - (RECORD_DAY_COUNT - TREND_DAY_COUNT)
            val recentInnerTrendAdjustment = if (recentDayOffset >= 0L) {
                recentDayOffset - TREND_DAY_COUNT / 2L
            } else {
                0L
            }
            DailyPostureSummary(
                dayStartMillis = dayStart,
                dayEndMillis = dayEnd,
                zoneId = zoneId.id,
                coverMillis = minutes(72L + (dayOffset * 17L % 41L) + weekendBoost),
                innerMillis = minutes(
                    118L + (dayOffset * 29L % 67L) + weekendBoost + longTermInnerBoost +
                        recentInnerTrendAdjustment,
                ),
                excludedMillis = minutes(3L + (dayOffset * 7L % 8L)),
                openedCount = 8 + (dayOffset * 5L % 10L).toInt(),
                closedCount = 8 + (dayOffset * 5L % 10L).toInt(),
                evidenceGapCount = if (dayOffset % 29L == 0L) 1 else 0,
            )
        }
        val insights = LongTermAnalyzer().analyze(
            summaries = dailySummaries,
            period = LongTermPeriod.DAYS_90,
            rangeEndMillis = recordEndMillis,
            zoneId = zoneId,
        )
        val apps = representativeApps(insights.coverMillis, insights.innerMillis, appLabels)
        val innerSessionSummary = representativeInnerSessionSummary(
            rangeStartMillis = insights.rangeStartMillis,
            rangeEndMillis = insights.rangeEndMillis,
            detectedOpenCount = insights.openedCount,
            labels = appLabels,
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
            innerSessionSummary = innerSessionSummary,
            longTermInsights = insights,
            lastSuccessfulSyncMillis = recordEndMillis - minutes(2L),
        )
    }

    private fun representativeApps(
        coverMillis: Long,
        innerMillis: Long,
        labels: AppLabels,
    ): List<AppUsage> = listOf(
        app("demo.browser", labels.browser, coverMillis, 24, innerMillis, 16),
        app("demo.messages", labels.messages, coverMillis, 21, innerMillis, 8),
        app("demo.maps", labels.maps, coverMillis, 14, innerMillis, 4),
        app("demo.photos", labels.photos, coverMillis, 11, innerMillis, 14),
        app("demo.reader", labels.reading, coverMillis, 7, innerMillis, 27),
    )

    private fun representativeInnerSessionSummary(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        detectedOpenCount: Int,
        labels: AppLabels,
    ): InnerSessionSummary {
        val dayMillis = minutes(24L * 60L)
        val completeSessionCount = detectedOpenCount - 15
        return InnerSessionSummary(
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            detectedOpenCount = detectedOpenCount,
            completeSessionCount = completeSessionCount,
            medianInnerActiveMillis = minutes(18L),
            averageInnerActiveMillis = minutes(22L),
            longestInnerActiveMillis = minutes(42L),
            longSessions = listOf(
                InnerSessionDetail(
                    openedAtMillis = rangeStartMillis + dayMillis * 5L + minutes(9L * 60L + 15L),
                    openedSequenceAtTimestamp = 0,
                    innerActiveMillis = minutes(42L),
                    appUsages = listOf(
                        InnerSessionAppUsage(
                            packageName = "demo.reader",
                            label = labels.reading,
                            innerActiveMillis = minutes(21L),
                        ),
                        InnerSessionAppUsage(
                            packageName = "demo.browser",
                            label = labels.browser,
                            innerActiveMillis = minutes(12L),
                        ),
                        InnerSessionAppUsage(
                            packageName = "demo.photos",
                            label = labels.photos,
                            innerActiveMillis = minutes(5L),
                        ),
                    ),
                    otherInnerActiveMillis = minutes(4L),
                ),
                InnerSessionDetail(
                    openedAtMillis = rangeStartMillis + dayMillis * 22L + minutes(14L * 60L + 40L),
                    openedSequenceAtTimestamp = 0,
                    innerActiveMillis = minutes(34L),
                    appUsages = listOf(
                        InnerSessionAppUsage(
                            packageName = "demo.browser",
                            label = labels.browser,
                            innerActiveMillis = minutes(14L),
                        ),
                        InnerSessionAppUsage(
                            packageName = "demo.messages",
                            label = labels.messages,
                            innerActiveMillis = minutes(9L),
                        ),
                    ),
                    otherInnerActiveMillis = minutes(11L),
                ),
                InnerSessionDetail(
                    openedAtMillis = rangeStartMillis + dayMillis * 47L + minutes(18L * 60L + 5L),
                    openedSequenceAtTimestamp = 0,
                    innerActiveMillis = minutes(27L),
                    appUsages = listOf(
                        InnerSessionAppUsage(
                            packageName = "demo.maps",
                            label = labels.maps,
                            innerActiveMillis = minutes(12L),
                        ),
                        InnerSessionAppUsage(
                            packageName = "demo.reader",
                            label = labels.reading,
                            innerActiveMillis = minutes(7L),
                        ),
                    ),
                    otherInnerActiveMillis = minutes(8L),
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

    private fun capture(name: String, outputDirectory: String) {
        composeRule.waitForIdle()
        val directory = File(
            checkNotNull(targetContext.getExternalFilesDir(null)),
            outputDirectory,
        )
        check(directory.exists() || directory.mkdirs()) {
            "Could not create screenshot output directory: $directory"
        }
        val output = File(directory, "$name.png")
        FileOutputStream(output).use { stream ->
            check(
                composeRule.onRoot()
                    .captureToImage()
                    .asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream),
            ) { "Could not write screenshot: $output" }
        }
        check(output.length() > 0L) { "Screenshot is empty: $output" }
    }

    private fun minutes(value: Long): Long = value * 60_000L

    private data class AppLabels(
        val browser: String,
        val messages: String,
        val maps: String,
        val photos: String,
        val reading: String,
    )

    companion object {
        private const val RECORD_DAY_COUNT = 365L
        private const val TREND_DAY_COUNT = 90L
        private const val JAPANESE_OUTPUT_DIRECTORY = "store-screenshots"
        private const val ENGLISH_OUTPUT_DIRECTORY = "store-screenshots-en"
    }
}
