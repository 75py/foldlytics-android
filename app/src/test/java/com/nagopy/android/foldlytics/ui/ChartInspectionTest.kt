package com.nagopy.android.foldlytics.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.toShortDateText
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w412dp-h915dp", application = Application::class)
class ChartInspectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val buckets = listOf(bucket(1, 20), bucket(2, null), bucket(3, 80))

    @Test
    fun tapReportsMissingDataAndDraggingSelectsTheNearestBucket() {
        compose.setContent { FoldlyticsTheme { InnerRatioTrendChart(buckets, postureColors()) } }

        compose.onNodeWithTag(TREND_PLOT_TAG).performTouchInput { click(center) }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(description(buckets[1]))
        compose.onNodeWithTag(TREND_PLOT_TAG).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(description(buckets.first()))
    }

    @Test
    fun accessibleSelectionClampsToTheRangeAndRejectsNonFiniteValues() {
        compose.setContent { FoldlyticsTheme { InnerRatioTrendChart(buckets, postureColors()) } }

        compose.onNodeWithTag(TREND_PLOT_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(99f) }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(description(buckets.last()))
        compose.onNodeWithTag(TREND_PLOT_TAG).performSemanticsAction(SemanticsActions.SetProgress) {
            assertFalse(it(Float.NaN))
            assertFalse(it(Float.POSITIVE_INFINITY))
        }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(description(buckets.last()))
    }

    @Test
    fun changingThePeriodResetsSelectionAndSingleBucketRemainsInspectable() {
        val shown = mutableStateOf(buckets)
        compose.setContent { FoldlyticsTheme { InnerRatioTrendChart(shown.value, postureColors()) } }
        compose.onNodeWithTag(TREND_PLOT_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }

        compose.runOnIdle { shown.value = listOf(buckets.first()) }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(resources.getString(R.string.chart_inspection_hint))
        compose.onNodeWithTag(TREND_PLOT_TAG).performTouchInput { click(center) }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(description(buckets.first()))
    }

    @Test
    fun emptyChartDoesNotOfferAnInvalidSelectionAction() {
        compose.setContent { FoldlyticsTheme { InnerRatioTrendChart(emptyList(), postureColors()) } }
        assertFalse(compose.onNodeWithTag(TREND_PLOT_TAG).fetchSemanticsNode().config.contains(SemanticsActions.SetProgress))
    }

    @Test
    fun openCountDistinguishesObservedZeroFromAnUnobservedInterval() {
        val observedZero = buckets.first().copy(openedCount = 0)
        val shown = listOf(observedZero, buckets[1])
        compose.setContent { FoldlyticsTheme { OpenCountTrendChart(shown, postureColors()) } }
        compose.onNodeWithTag(TREND_PLOT_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(resources.getQuantityString(
            R.plurals.content_desc_chart_opened, 0, observedZero.startMillis.toShortDateText(resources), 0,
        ))
        compose.onNodeWithTag(TREND_PLOT_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag(TREND_INSPECTION_TAG).assertTextEquals(description(buckets[1]))
    }

    private fun description(bucket: LongTermBucket): String = if (bucket.classifiedMillis == 0L) {
        resources.getString(R.string.content_desc_chart_no_data, bucket.startMillis.toShortDateText(resources))
    } else {
        resources.getString(R.string.content_desc_chart_inner_ratio, bucket.startMillis.toShortDateText(resources),
            resources.getString(R.string.value_percent_0, bucket.innerRatio * 100))
    }

    private fun bucket(day: Int, percent: Int?): LongTermBucket = LongTermBucket(
        startMillis = day * 86_400_000L,
        endMillis = (day + 1) * 86_400_000L,
        coverMillis = percent?.let { 100L - it } ?: 0L,
        innerMillis = percent?.toLong() ?: 0L,
        excludedMillis = 0L,
        openedCount = percent ?: 0,
        closedCount = 0,
        observedDayCount = if (percent == null) 0 else 1,
        evidenceGapDayCount = 0,
    )
}
