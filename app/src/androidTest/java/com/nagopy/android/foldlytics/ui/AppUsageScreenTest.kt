package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.AppUsage
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class AppUsageScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTargetPeriodAndSwitchesTheSingleRankingBasis() {
        val context = localizedContext(Locale.ENGLISH)
        setContent(context, summaryWithApps())

        composeRule.onNodeWithTag(APP_USAGE_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_PERIOD_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.selected_period,
                context.getString(R.string.period_30_days),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_RANKING_SELECTOR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_COVER_SEGMENT_TAG).assertIsSelected()
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).assertIsNotSelected()
        composeRule.onNodeWithText(
            context.getString(
                R.string.app_ranking_order,
                context.getString(R.string.posture_cover),
            ),
        ).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}outer-first"),
        )
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}outer-first").assertExists()

        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).performClick()

        composeRule.onNodeWithTag(APP_USAGE_COVER_SEGMENT_TAG).assertIsNotSelected()
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).assertIsSelected()
        composeRule.onNodeWithText(
            context.getString(
                R.string.app_ranking_order,
                context.getString(R.string.posture_inner),
            ),
        ).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}inner-first"),
        )
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}inner-first").assertExists()
        composeRule.onNodeWithText("Zero time").assertDoesNotExist()
        composeRule.onNodeWithText("System process").assertDoesNotExist()
    }

    @Test
    fun showsAnEmptyStateWhenNoLauncherAppHasTimeForTheSelectedBasis() {
        val context = localizedContext(Locale.ENGLISH)
        setContent(
            context,
            summaryWithApps(
                apps = listOf(
                    app("zero", "Zero time", coverMillis = 0L, innerMillis = 0L),
                    app(
                        "system",
                        "System process",
                        coverMillis = 1_000L,
                        innerMillis = 1_000L,
                        isLauncherApp = false,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText(
            context.getString(
                R.string.no_ranked_apps,
                context.getString(R.string.posture_cover),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}zero").assertDoesNotExist()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}system").assertDoesNotExist()
    }

    @Test
    fun showsAnEmptyStateWhenTheSelectedPeriodHasNoSummary() {
        val context = localizedContext(Locale.ENGLISH)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    AppUsageScreen(
                        state = MainUiState(
                            selectedPeriod = AnalysisPeriod.DAYS_30,
                        ),
                        scaffoldPadding = PaddingValues(),
                    )
                }
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.app_usage_detail_empty),
        ).assertIsDisplayed()
    }

    private fun setContent(context: Context, summary: PeriodUsageSummary) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    AppUsageScreen(
                        state = MainUiState(
                            hasUsageAccess = true,
                            selectedPeriod = summary.period,
                            periodSummary = summary,
                        ),
                        scaffoldPadding = PaddingValues(),
                    )
                }
            }
        }
    }

    private fun summaryWithApps(
        apps: List<AppUsage> = listOf(
            app("outer-first", "Outer first", coverMillis = 500L, innerMillis = 10L),
            app("cover-second", "Cover second", coverMillis = 200L, innerMillis = 0L),
            app("inner-first", "Inner first", coverMillis = 10L, innerMillis = 500L),
            app("zero", "Zero time", coverMillis = 0L, innerMillis = 0L),
            app(
                "system",
                "System process",
                coverMillis = 10_000L,
                innerMillis = 10_000L,
                isLauncherApp = false,
            ),
        ),
    ) = PeriodUsageSummary(
        period = AnalysisPeriod.DAYS_30,
        rangeStartMillis = 1_753_459_200_000L,
        rangeEndMillis = 1_756_051_200_000L,
        coverMillis = 10_000L,
        innerMillis = 10_000L,
        excludedMillis = 0L,
        openedCount = 10,
        closedCount = 10,
        apps = apps,
    )

    private fun app(
        packageName: String,
        label: String,
        coverMillis: Long,
        innerMillis: Long,
        isLauncherApp: Boolean = true,
    ) = AppUsage(
        packageName = packageName,
        label = label,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = 0L,
        isLauncherApp = isLauncherApp,
    )

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
