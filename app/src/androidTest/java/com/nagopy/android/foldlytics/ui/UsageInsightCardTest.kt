package com.nagopy.android.foldlytics.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.insight.InsightStatus
import com.nagopy.android.foldlytics.insight.UsageInsightFact
import com.nagopy.android.foldlytics.insight.UsageInsightFacts
import com.nagopy.android.foldlytics.insight.UsageInsightRange
import com.nagopy.android.foldlytics.insight.UsageInsightUiState
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.io.File
import org.junit.Rule
import org.junit.Test

class UsageInsightCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsExplanationAndEvidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = UsageInsightUiState(
            InsightStatus.READY,
            listOf("The inner display accounts for 70% of classified usage.", "Inner use was recorded on 20 days."),
            UsageInsightFacts(
                UsageInsightRange(1786492800000, 1789084800000, 1783900800000, "UTC"), "en",
                listOf(UsageInsightFact("display_share", "inner=70", "Measured inner share: 70%")),
                emptyList(), true, "screenshot-fixture",
            ),
        )
        compose.setContent { FoldlyticsTheme { UsageInsightCard(state) } }
        compose.onNodeWithTag("usage_insight_card").assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), "insight-card.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText(context.getString(com.nagopy.android.foldlytics.R.string.insight_evidence)).performClick()
        compose.onNodeWithText("Measured inner share: 70%").assertIsDisplayed()
    }

    @Test fun capturesBeforeAndAfterHomeWithSyntheticHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val insight = mutableStateOf(UsageInsightUiState())
        val main = MainUiState(
            hasUsageAccess = true,
            periodSummary = PeriodUsageSummary(AnalysisPeriod.HOURS_24,
                1788998400000, 1789084800000, 1080000, 2520000, 0, 2, 2, emptyList()),
        )
        compose.setContent {
            FoldlyticsTheme {
                HomeScreen(main, PaddingValues(16.dp), rememberLazyListState(),
                    {}, {}, { _, _ -> }, {}, {}, {}, {}, 0.dp, insight.value)
            }
        }
        fun capture(name: String) {
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.getExternalFilesDir(null), name).outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        capture("insight-home-before.png")
        compose.runOnIdle {
            insight.value = UsageInsightUiState(InsightStatus.READY,
                listOf("The inner display accounts for 70% of classified usage.",
                    "Inner use was recorded on 20 days."),
                UsageInsightFacts(UsageInsightRange(1786492800000, 1789084800000, 1783900800000, "UTC"),
                    "en", listOf(UsageInsightFact("display_share", "70", "Inner share: 70%")),
                    emptyList(), true, "fixture"))
        }
        compose.onNodeWithTag("usage_insight_card").assertIsDisplayed()
        capture("insight-home-after.png")
    }
}
