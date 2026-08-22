package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class SummaryAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noDataSummaryUsesMeaningfulEnglishAccessibilityTextWithoutZeroPercent() {
        val context = localizedContext(Locale.ENGLISH)
        val summary = summary(coverMillis = 0L, innerMillis = 0L, excludedMillis = 0L)
        val description = context.getString(
            R.string.content_desc_summary,
            context.getString(R.string.period_24_hours),
            context.getString(R.string.duration_seconds, 0),
            context.getString(R.string.duration_seconds, 0),
            context.getString(R.string.label_no_data),
        )

        setContent(context, summary)
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasContentDescription(description),
        )

        composeRule.onNodeWithContentDescription(description).assertExists()
        composeRule.onNodeWithText("—", useUnmergedTree = true).assertExists()
        composeRule.onAllNodes(
            hasContentDescription("0%", substring = true),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        composeRule.onAllNodes(
            hasText("0%", substring = true),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun noDataSummaryUsesMeaningfulJapaneseAccessibilityText() {
        val context = localizedContext(Locale.JAPANESE)
        val summary = summary(coverMillis = 0L, innerMillis = 0L, excludedMillis = 0L)
        val description = context.getString(
            R.string.content_desc_summary,
            context.getString(R.string.period_24_hours),
            context.getString(R.string.duration_seconds, 0),
            context.getString(R.string.duration_seconds, 0),
            context.getString(R.string.label_no_data),
        )

        setContent(context, summary)
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasContentDescription(description),
        )

        composeRule.onNodeWithContentDescription(description).assertExists()
    }

    @Test
    fun classifiedSummaryKeepsItsRatioAndCoveragePercentages() {
        val context = localizedContext(Locale.ENGLISH)
        val summary = summary(coverMillis = 3_000L, innerMillis = 1_000L, excludedMillis = 0L)
        val description = context.getString(
            R.string.content_desc_summary,
            context.getString(R.string.period_24_hours),
            context.getString(R.string.duration_seconds, 3),
            context.getString(R.string.duration_seconds, 1),
            context.getString(R.string.value_percent_0, 25f),
        )

        setContent(context, summary)
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasContentDescription(description),
        )

        composeRule.onNodeWithContentDescription(description).assertExists()
        composeRule.onNodeWithText("25%", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("100%", useUnmergedTree = true).assertExists()
    }

    private fun setContent(context: Context, summary: PeriodUsageSummary) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    FoldlyticsScreen(
                        state = MainUiState(
                            hasUsageAccess = true,
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
                    )
                }
            }
        }
    }

    private fun summary(
        coverMillis: Long,
        innerMillis: Long,
        excludedMillis: Long,
    ) = PeriodUsageSummary(
        period = AnalysisPeriod.HOURS_24,
        rangeStartMillis = 0L,
        rangeEndMillis = 86_400_000L,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = excludedMillis,
        openedCount = 0,
        closedCount = 0,
        apps = emptyList(),
    )

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
