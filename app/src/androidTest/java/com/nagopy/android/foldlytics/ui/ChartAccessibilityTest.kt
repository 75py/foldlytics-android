package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.LongTermBucket
import com.nagopy.android.foldlytics.toShortDateText
import java.time.Instant
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class ChartAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun englishChartsUseEnglishListFormattingFromAppResources() {
        val context = localizedContext(Locale.ENGLISH)
        withDefaultLocale(Locale.JAPANESE) {
            setContent(context, buckets())

            composeRule.onNodeWithContentDescription(
                englishList(innerRatioDescriptions(context)),
                useUnmergedTree = true,
            ).assertExists()
            composeRule.onNodeWithContentDescription(
                englishList(openCountDescriptions(context)),
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    @Test
    fun japaneseChartsUseJapaneseListFormattingFromAppResources() {
        val context = localizedContext(Locale.JAPANESE)
        withDefaultLocale(Locale.ENGLISH) {
            setContent(context, buckets())

            composeRule.onNodeWithContentDescription(
                japaneseList(innerRatioDescriptions(context)),
                useUnmergedTree = true,
            ).assertExists()
            composeRule.onNodeWithContentDescription(
                japaneseList(openCountDescriptions(context)),
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    @Test
    fun emptyChartsAnnounceLocalizedNoData() {
        val context = localizedContext(Locale.ENGLISH)

        setContent(context, emptyList())

        composeRule.onAllNodes(
            hasContentDescription(context.getString(R.string.label_no_data)),
            useUnmergedTree = true,
        ).assertCountEquals(2)
    }

    private fun setContent(context: Context, buckets: List<LongTermBucket>) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    val colors = postureColors()
                    Column {
                        InnerRatioTrendChart(buckets, colors)
                        OpenCountTrendChart(buckets, colors)
                    }
                }
            }
        }
    }

    private fun innerRatioDescriptions(context: Context): List<String> =
        buckets().map { bucket ->
            val date = bucket.startMillis.toShortDateText(context.resources)
            if (bucket.classifiedMillis == 0L) {
                context.getString(R.string.content_desc_chart_no_data, date)
            } else {
                context.getString(
                    R.string.content_desc_chart_inner_ratio,
                    date,
                    context.getString(R.string.value_percent_0, bucket.innerRatio * 100),
                )
            }
        }

    private fun openCountDescriptions(context: Context): List<String> =
        buckets().map { bucket ->
            val date = bucket.startMillis.toShortDateText(context.resources)
            if (bucket.observedDayCount == 0) {
                context.getString(R.string.content_desc_chart_no_data, date)
            } else {
                context.resources.getQuantityString(
                    R.plurals.content_desc_chart_opened,
                    bucket.openedCount,
                    date,
                    bucket.openedCount,
                )
            }
        }

    private fun englishList(items: List<String>): String =
        "${items[0]}, ${items[1]}, and ${items[2]}"

    private fun japaneseList(items: List<String>): String =
        "${items[0]}、${items[1]}、${items[2]}"

    private fun buckets(): List<LongTermBucket> {
        val firstDay = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
        val day = 86_400_000L
        return listOf(
            bucket(firstDay, coverMillis = 600L, innerMillis = 400L, openedCount = 1),
            bucket(firstDay + day, coverMillis = 200L, innerMillis = 800L, openedCount = 2),
            bucket(
                firstDay + day * 2,
                coverMillis = 0L,
                innerMillis = 0L,
                openedCount = 0,
                observedDayCount = 0,
            ),
        )
    }

    private fun bucket(
        startMillis: Long,
        coverMillis: Long,
        innerMillis: Long,
        openedCount: Int,
        observedDayCount: Int = 1,
    ) = LongTermBucket(
        startMillis = startMillis,
        endMillis = startMillis + 86_400_000L,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = 0L,
        openedCount = openedCount,
        closedCount = 0,
        observedDayCount = observedDayCount,
        evidenceGapDayCount = 0,
    )

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
