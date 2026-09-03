package com.nagopy.android.foldlytics.ui

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
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
    private var screenshotHomeItemIndex: Int? by mutableStateOf(null)

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
        clearOutputDirectory(outputDirectory)
        screenshotHomeItemIndex = null
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
                        screenshotSectionEndSpacing = SCREENSHOT_SECTION_END_SPACING,
                        screenshotHomeItemIndex = screenshotHomeItemIndex,
                    )
                }
            }
        }

        scrollHomeToItem(HOME_RESULT_HEADER_ITEM_INDEX)
        capture("01-home-summary", outputDirectory)

        scrollTo(HOME_INNER_SESSIONS_LINK_TAG)
        composeRule.onNodeWithTag(HOME_INNER_SESSIONS_LINK_TAG).performClick()
        composeRule.onNodeWithTag(INNER_DISPLAY_SESSION_SCREEN_TAG).assertExists()
        scrollTo(INNER_SESSION_LONG_SESSIONS_CARD_TAG)
        capture("02-session-details", outputDirectory)

        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()
        scrollHomeToItem(HOME_USAGE_TREND_ITEM_INDEX)
        capture("03-inner-ratio-trend", outputDirectory)

        composeRule.onNodeWithTag(USAGE_TREND_OPEN_COUNT_TAG).performClick()
        scrollHomeToItem(HOME_USAGE_TREND_ITEM_INDEX)
        capture("04-open-count-trend", outputDirectory)

        scrollTo(HOME_APP_USAGE_LINK_TAG)
        composeRule.onNodeWithTag(HOME_APP_USAGE_LINK_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_SCREEN_TAG).assertExists()
        scrollTo("${APP_USAGE_CARD_TAG_PREFIX}demo.reader")
        capture("05-total-app-ranking", outputDirectory)

        composeRule.onNodeWithTag(DETAIL_BACK_BUTTON_TAG).performClick()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.content_desc_open_menu),
        ).performClick()
        capture("06-drawer", outputDirectory)
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun scrollHomeToItem(index: Int) {
        // Reset first so an unchanged index re-runs the capture-only scroll anchor after a tab
        // changes the card height.
        composeRule.runOnIdle { screenshotHomeItemIndex = null }
        composeRule.waitForIdle()
        composeRule.runOnIdle { screenshotHomeItemIndex = index }
        composeRule.waitForIdle()
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
        val resolver = targetContext.contentResolver
        val relativePath = relativeOutputPath(outputDirectory)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val outputUri = checkNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) {
            "Could not create screenshot media entry: $relativePath/$name.png"
        }
        try {
            val stream = checkNotNull(resolver.openOutputStream(outputUri)) {
                "Could not open screenshot output: $relativePath/$name.png"
            }
            stream.use { output ->
                check(
                    composeRule.onRoot()
                        .captureToImage()
                        .asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, output),
                ) { "Could not write screenshot: $relativePath/$name.png" }
            }
            check(
                resolver.update(
                    outputUri,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    },
                    null,
                    null,
                ) == 1,
            ) { "Could not publish screenshot: $relativePath/$name.png" }
        } catch (error: Throwable) {
            resolver.delete(outputUri, null, null)
            throw error
        }
    }

    private fun clearOutputDirectory(outputDirectory: String) {
        targetContext.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(relativeOutputPath(outputDirectory)),
        )
    }

    private fun relativeOutputPath(outputDirectory: String): String =
        "${Environment.DIRECTORY_DOWNLOADS}/Foldlytics/$outputDirectory/"

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
        private const val HOME_RESULT_HEADER_ITEM_INDEX = 1
        private const val HOME_USAGE_TREND_ITEM_INDEX = 3
        // Small capture-only trailing space that prevents scrollToItem from clamping.
        private val SCREENSHOT_SECTION_END_SPACING = 96.dp
        private const val JAPANESE_OUTPUT_DIRECTORY = "store-screenshots"
        private const val ENGLISH_OUTPUT_DIRECTORY = "store-screenshots-en"
    }
}
