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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test

/** Creates deterministic, user-data-free images for pull request review. */
class SummaryShareScreenshotCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun captureJapanesePreviewAndGeneratedImage() {
        val context = localizedContext(Locale.JAPANESE)
        val summary = representativeSummary()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    FoldlyticsScreen(
                        state = MainUiState(
                            hasUsageAccess = true,
                            selectedPeriod = summary.period,
                            availablePeriods = AnalysisPeriod.entries.toSet(),
                            periodSummary = summary,
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
                        appName = "Foldlytics",
                    )
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(SUMMARY_CARD_TAG))
        composeRule.onNodeWithTag(SUMMARY_SHARE_BUTTON_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag(SUMMARY_SHARE_PREVIEW_IMAGE_TAG))
                .fetchSemanticsNodes().isNotEmpty()
        }

        saveBitmap(
            name = "preview-ja.png",
            bitmap = composeRule.onNodeWithTag(SUMMARY_SHARE_PREVIEW_TAG)
                .captureToImage()
                .asAndroidBitmap(),
        )
        saveBitmap(
            name = "generated-ja.png",
            bitmap = SummaryShareImageRenderer.render(context.resources, summary),
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

    private fun representativeSummary(): PeriodUsageSummary {
        val zoneId = ZoneId.of("Asia/Tokyo")
        val rangeStartMillis = LocalDate.of(2026, 7, 24)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val rangeEndMillis = LocalDate.of(2026, 8, 23)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return PeriodUsageSummary(
            period = AnalysisPeriod.DAYS_30,
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            coverMillis = minutes(3_472L),
            innerMillis = minutes(5_918L),
            excludedMillis = minutes(97L),
            openedCount = 327,
            closedCount = 321,
            apps = emptyList(),
        )
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val directory = File(
            checkNotNull(targetContext.getExternalFilesDir(null)),
            OUTPUT_DIRECTORY,
        )
        check(directory.exists() || directory.mkdirs()) {
            "Could not create screenshot output directory: $directory"
        }
        val output = File(directory, name)
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Could not write screenshot: $output"
            }
        }
        check(output.length() > 0L) { "Screenshot is empty: $output" }
    }

    private fun minutes(value: Long): Long = value * 60_000L

    companion object {
        private const val OUTPUT_DIRECTORY = "summary-share-review"
    }
}
