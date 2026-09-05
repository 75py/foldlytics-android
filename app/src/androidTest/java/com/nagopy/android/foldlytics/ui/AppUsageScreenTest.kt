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
import androidx.compose.ui.test.hasContentDescription
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
import com.nagopy.android.foldlytics.toDurationText
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class AppUsageScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTargetPeriodAndStartsWithTotalRankingBeforeSwitchingDisplayBasis() {
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
        composeRule.onNodeWithTag(APP_USAGE_VIEW_SELECTOR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_TIME_VIEW_TAG).assertIsSelected()
        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).assertIsNotSelected()
        composeRule.onNodeWithTag(APP_USAGE_RANKING_SELECTOR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_TOTAL_SEGMENT_TAG).assertIsSelected()
        composeRule.onNodeWithTag(APP_USAGE_COVER_SEGMENT_TAG).assertIsNotSelected()
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).assertIsNotSelected()
        composeRule.onNodeWithText(
            context.getString(R.string.app_ranking_total_order),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_COVER_SEGMENT_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_TOTAL_SEGMENT_TAG).assertIsNotSelected()
        composeRule.onNodeWithTag(APP_USAGE_COVER_SEGMENT_TAG).assertIsSelected()
        composeRule.onNodeWithText(
            context.getString(
                R.string.app_ranking_order,
                context.getString(R.string.app_usage_outer_display),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).performClick()

        composeRule.onNodeWithTag(APP_USAGE_COVER_SEGMENT_TAG).assertIsNotSelected()
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).assertIsSelected()
        composeRule.onNodeWithText(
            context.getString(
                R.string.app_ranking_order,
                context.getString(R.string.app_usage_inner_display),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_TOTAL_SEGMENT_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_TOTAL_SEGMENT_TAG).assertIsSelected()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}inner-first"),
        )
        assertRankedFirst(context, "inner-first")
        val totalCardDescription = context.getString(
            R.string.content_desc_app_usage_card,
            context.getString(R.string.value_rank, 1),
            "Inner first",
            context.getString(
                R.string.app_usage_total,
                510_000L.toDurationText(context.resources),
            ),
            context.getString(
                R.string.app_usage_display_split,
                10_000L.toDurationText(context.resources),
                context.getString(R.string.value_percent_1, 2.0),
                500_000L.toDurationText(context.resources),
                context.getString(R.string.value_percent_1, 98.0),
            ),
        )
        composeRule.onNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}inner-first") and
                hasContentDescription(totalCardDescription),
        ).assertExists()

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(APP_USAGE_RANKING_SELECTOR_TAG),
        )
        composeRule.onNodeWithTag(APP_USAGE_COVER_SEGMENT_TAG).performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}outer-first"),
        )
        assertRankedFirst(context, "outer-first")

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(APP_USAGE_RANKING_SELECTOR_TAG),
        )
        composeRule.onNodeWithTag(APP_USAGE_INNER_SEGMENT_TAG).performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}inner-first"),
        )
        assertRankedFirst(context, "inner-first")
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}zero").assertDoesNotExist()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}system").assertDoesNotExist()
    }

    @Test
    fun displayShareViewRanksMajoritiesByMeasuredTimeAndAnnouncesKnownAndUnknownContext() {
        val context = localizedContext(Locale.ENGLISH)
        setContent(
            context,
            summaryWithApps(
                apps = listOf(
                    app(
                        "inner-long",
                        "Inner long",
                        coverMillis = minutes(2),
                        innerMillis = minutes(4),
                        excludedMillis = 30_000L,
                    ),
                    app(
                        "inner-brief",
                        "Inner brief",
                        coverMillis = 0L,
                        innerMillis = 5_000L,
                    ),
                    app(
                        "outer-long",
                        "Outer long",
                        coverMillis = minutes(10),
                        innerMillis = minutes(2),
                    ),
                    app(
                        "outer-near-tie",
                        "Outer near tie",
                        coverMillis = 60_001L,
                        innerMillis = 60_000L,
                    ),
                    app(
                        "even",
                        "Even split",
                        coverMillis = minutes(3),
                        innerMillis = minutes(3),
                    ),
                    app(
                        "unknown-only",
                        "Unknown only",
                        coverMillis = 0L,
                        innerMillis = 0L,
                        excludedMillis = minutes(20),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).performClick()

        composeRule.onNodeWithTag(APP_USAGE_TIME_VIEW_TAG).assertIsNotSelected()
        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).assertIsSelected()
        composeRule.onNodeWithTag(APP_USAGE_RANKING_SELECTOR_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(APP_USAGE_MAJORITY_SELECTOR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(APP_USAGE_INNER_MAJORITY_TAG).assertIsSelected()
        composeRule.onNodeWithText(
            context.getString(
                R.string.app_ranking_majority_order,
                context.getString(R.string.app_usage_inner_display),
                context.getString(R.string.app_usage_outer_display),
            ),
        ).assertIsDisplayed()

        val rank = context.getString(R.string.value_rank, 1)
        val primary = context.getString(
            R.string.app_usage_selected,
            context.getString(R.string.posture_inner),
            minutes(4).toDurationText(context.resources),
        )
        val contextText = context.getString(
            R.string.app_usage_display_split,
            minutes(2).toDurationText(context.resources),
            context.getString(R.string.value_percent_1, 33.3),
            minutes(4).toDurationText(context.resources),
            context.getString(R.string.value_percent_1, 66.7),
        )
        val undetermined = context.getString(
            R.string.app_usage_undetermined,
            30_000L.toDurationText(context.resources),
        )
        val description = context.getString(
            R.string.content_desc_app_usage_card_with_undetermined,
            rank,
            "Inner long",
            primary,
            contextText,
            undetermined,
        )
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}inner-long"),
        )
        composeRule.onNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}inner-long") and
                hasContentDescription(description),
        ).assertExists()
        assertRank(context, "inner-brief", 2)
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}outer-long").assertDoesNotExist()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}even").assertDoesNotExist()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}unknown-only").assertDoesNotExist()

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(APP_USAGE_MAJORITY_SELECTOR_TAG),
        )
        composeRule.onNodeWithTag(APP_USAGE_COVER_MAJORITY_TAG).performClick()
        composeRule.onNodeWithTag(APP_USAGE_COVER_MAJORITY_TAG).assertIsSelected()
        composeRule.onNodeWithTag(APP_USAGE_INNER_MAJORITY_TAG).assertIsNotSelected()
        composeRule.onNodeWithText(
            "Apps used longer on the outer display than on the inner display. " +
                "Sorted by time on the outer display, longest first.",
        ).assertIsDisplayed()
        assertRankedFirst(context, "outer-long")
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}outer-near-tie"),
        )
        composeRule.onNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}outer-near-tie") and
                hasContentDescription(
                    context.getString(
                        R.string.app_usage_display_split,
                        60_001L.toDurationText(context.resources),
                        context.getString(R.string.value_share_more_than_half),
                        60_000L.toDurationText(context.resources),
                        context.getString(R.string.value_share_less_than_half),
                    ),
                    substring = true,
                ),
        ).assertExists()
    }

    @Test
    fun japaneseDisplayShareCardHasLocalizedAccessibleDescriptionAndSharedRanks() {
        val context = localizedContext(Locale.JAPANESE)
        setContent(
            context,
            summaryWithApps(
                apps = listOf(
                    app("alpha", "アルファ", minutes(1), minutes(3)),
                    app("beta", "ベータ", minutes(2), minutes(3)),
                ),
            ),
        )

        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}alpha"),
        )

        assertRank(context, "alpha", 1)
        assertRank(context, "beta", 1)
        composeRule.onNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}alpha") and
                hasContentDescription(
                    context.getString(R.string.value_rank, 1),
                    substring = true,
                ) and
                hasContentDescription("内側 3分0秒（75.0%）", substring = true),
        ).assertExists()
    }

    @Test
    fun japaneseCopyExplainsTheComparisonOrderAndUnknownTime() {
        val context = localizedContext(Locale.JAPANESE)
        setContent(
            context,
            summaryWithApps(
                apps = listOf(
                    app("reading", "読書", minutes(2), minutes(3), minutes(1)),
                ),
            ),
        )

        composeRule.onNodeWithText("よく使ったアプリ").assertIsDisplayed()
        composeRule.onNodeWithText("利用時間の合計が長い順に表示しています。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("よく使う画面").performClick()
        composeRule.onNodeWithText("内側中心").assertIsDisplayed()
        composeRule.onNodeWithText(
            "外側より内側で長く使ったアプリを、内側での利用時間が長い順に表示します。",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "割合は、アプリごとの外側・内側の利用時間から計算します。" +
                "使用した画面が不明な時間は別に表示し、合計と割合には含めません。",
        ).assertExists()
        composeRule.onNodeWithText(
            "外側と内側の利用時間が同じアプリは、どちらの一覧にも表示しません。",
        ).assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}reading"),
        )
        composeRule.onNode(
            hasTestTag("${APP_USAGE_CARD_TAG_PREFIX}reading") and
                hasContentDescription("内側で 3分0秒", substring = true) and
                hasContentDescription("使用した画面が不明：1分0秒", substring = true),
        ).assertExists()

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(APP_USAGE_MAJORITY_SELECTOR_TAG),
        )
        composeRule.onNodeWithText("外側中心").performClick()
        composeRule.onNodeWithText(
            "内側より外側で長く使ったアプリを、外側での利用時間が長い順に表示します。",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "この期間に、内側より外側で長く使ったアプリはありません。",
        ).assertExists()
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
            context.getString(R.string.no_ranked_apps_total),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}zero").assertDoesNotExist()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}system").assertDoesNotExist()

        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.no_ranked_apps_total),
        ).assertIsDisplayed()
    }

    @Test
    fun evenSplitExclusionGuidanceOnlyAppearsInDisplayShareView() {
        val context = localizedContext(Locale.ENGLISH)
        val exclusionNote = context.getString(R.string.app_ranking_even_split_note)
        val measurementNote = context.getString(R.string.app_ranking_measurement_note)
        setContent(
            context,
            summaryWithApps(
                apps = listOf(
                    app("even", "Even split", coverMillis = minutes(3), innerMillis = minutes(3)),
                ),
            ),
        )

        composeRule.onNodeWithTag(APP_USAGE_TIME_VIEW_TAG).assertIsSelected()
        composeRule.onNodeWithText(measurementNote).assertExists()
        composeRule.onNodeWithText(exclusionNote, substring = true).assertDoesNotExist()
        assertRankedFirst(context, "even")

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag(APP_USAGE_VIEW_SELECTOR_TAG),
        )
        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).performClick()
        composeRule.onNodeWithText(measurementNote).assertExists()
        composeRule.onNodeWithText(exclusionNote, substring = true).assertExists()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}even").assertDoesNotExist()

        composeRule.onNodeWithTag(APP_USAGE_COVER_MAJORITY_TAG).performClick()
        composeRule.onNodeWithText(exclusionNote, substring = true).assertExists()
        composeRule.onNodeWithTag("${APP_USAGE_CARD_TAG_PREFIX}even").assertDoesNotExist()

        composeRule.onNodeWithTag(APP_USAGE_TIME_VIEW_TAG).performClick()
        composeRule.onNodeWithText(measurementNote).assertExists()
        composeRule.onNodeWithText(exclusionNote, substring = true).assertDoesNotExist()
        assertRankedFirst(context, "even")
    }

    @Test
    fun displayShareViewExplainsWhenMeasurableAppsHaveNoMajority() {
        val context = localizedContext(Locale.ENGLISH)
        setContent(
            context,
            summaryWithApps(
                apps = listOf(
                    app(
                        "even",
                        "Even split",
                        coverMillis = minutes(3),
                        innerMillis = minutes(3),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(APP_USAGE_DISPLAY_SHARE_VIEW_TAG).performClick()

        composeRule.onNodeWithText(
            context.getString(
                R.string.no_display_majority_apps,
                context.getString(R.string.app_usage_inner_display),
                context.getString(R.string.app_usage_outer_display),
            ),
        ).assertIsDisplayed()
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

    private fun assertRankedFirst(context: Context, packageName: String) {
        assertRank(context, packageName, 1)
    }

    private fun assertRank(context: Context, packageName: String, rank: Int) {
        val sentinel = "APP_LABEL_SENTINEL"
        val rankPrefix = context.getString(
            R.string.content_desc_app_usage_card,
            context.getString(R.string.value_rank, rank),
            sentinel,
            "",
            "",
        ).substringBefore(sentinel)
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasTestTag("$APP_USAGE_CARD_TAG_PREFIX$packageName"),
        )
        composeRule.onNode(
            hasTestTag("$APP_USAGE_CARD_TAG_PREFIX$packageName") and
                hasContentDescription(
                    rankPrefix,
                    substring = true,
                ),
        ).assertExists()
    }

    private fun summaryWithApps(
        apps: List<AppUsage> = listOf(
            app("outer-first", "Outer first", coverMillis = 500_000L, innerMillis = 10_000L),
            app("cover-second", "Cover second", coverMillis = 200_000L, innerMillis = 0L),
            app("inner-first", "Inner first", coverMillis = 10_000L, innerMillis = 500_000L),
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
        excludedMillis: Long = 0L,
        isLauncherApp: Boolean = true,
    ) = AppUsage(
        packageName = packageName,
        label = label,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = excludedMillis,
        isLauncherApp = isLauncherApp,
    )

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    private fun minutes(value: Int): Long = value * 60_000L
}
