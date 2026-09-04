package com.nagopy.android.foldlytics.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.model.AnalysisPeriod
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SummaryShareDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summaryCannotBeSharedWhileAnalyzingOrWithoutClassifiedUsage() {
        val state = mutableStateOf(
            MainUiState(
                hasUsageAccess = true,
                isAnalysisLoading = true,
                periodSummary = summary(innerMillis = hours(4L)),
            ),
        )
        setScreen(state)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(SUMMARY_CARD_TAG))

        composeRule.onNodeWithTag(SUMMARY_SHARE_BUTTON_TAG).assertIsNotEnabled()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                isAnalysisLoading = false,
                periodSummary = summary(coverMillis = 0L, innerMillis = 0L),
            )
        }
        composeRule.onNodeWithTag(SUMMARY_SHARE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SUMMARY_SHARE_PREVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun previewKeepsTheSummarySnapshotCapturedWhenItWasOpened() {
        val original = summary(coverMillis = hours(18L), innerMillis = hours(12L))
        val updated = summary(coverMillis = hours(2L), innerMillis = hours(28L))
        val state = mutableStateOf(
            MainUiState(
                hasUsageAccess = true,
                periodSummary = original,
            ),
        )
        val generatedSummaries = mutableListOf<PeriodUsageSummary>()
        val generator = SummaryShareImageGenerator { _, value ->
            generatedSummaries += value
            testBitmap()
        }
        setScreen(state, generator)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(SUMMARY_CARD_TAG))

        composeRule.onNodeWithTag(SUMMARY_SHARE_BUTTON_TAG).performClick()
        waitForPreviewImage()
        composeRule.runOnIdle { state.value = state.value.copy(periodSummary = updated) }
        composeRule.waitForIdle()

        assertEquals(listOf(original), generatedSummaries)
        composeRule.onNodeWithTag(SUMMARY_SHARE_PREVIEW_IMAGE_TAG).assertExists()
    }

    @Test
    fun sharingDisablesBothActionsAndReportsFailureBeforeAllowingRetry() {
        val completion = CompletableDeferred<Boolean>()
        val shareCalls = AtomicInteger(0)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalSummaryShareImageGenerator provides SummaryShareImageGenerator { _, _ ->
                    testBitmap()
                },
            ) {
                FoldlyticsTheme {
                    SummarySharePreviewDialog(
                        summary = summary(),
                        canShare = true,
                        onDismiss = {},
                        onShare = {
                            shareCalls.incrementAndGet()
                            completion.await()
                        },
                    )
                }
            }
        }
        waitForPreviewImage()

        composeRule.onNodeWithTag(SUMMARY_SHARE_CONFIRM_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { shareCalls.get() == 1 }
        composeRule.onNodeWithTag(SUMMARY_SHARE_CONFIRM_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SUMMARY_SHARE_CANCEL_TAG).assertIsNotEnabled()
        assertEquals(1, shareCalls.get())

        composeRule.runOnIdle { completion.complete(false) }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag(SUMMARY_SHARE_ERROR_TAG))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(SUMMARY_SHARE_CONFIRM_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(SUMMARY_SHARE_CANCEL_TAG).assertIsEnabled()
        assertEquals(1, shareCalls.get())
    }

    @Test
    fun generationFailureIsShownAndCannotBeShared() {
        var shared = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalSummaryShareImageGenerator provides SummaryShareImageGenerator { _, _ ->
                    error("render failed")
                },
            ) {
                FoldlyticsTheme {
                    SummarySharePreviewDialog(
                        summary = summary(),
                        canShare = true,
                        onDismiss = {},
                        onShare = {
                            shared = true
                            true
                        },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag(SUMMARY_SHARE_ERROR_TAG))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(SUMMARY_SHARE_CONFIRM_TAG).assertIsNotEnabled()
        assertFalse(shared)
    }

    private fun setScreen(
        state: MutableState<MainUiState>,
        generator: SummaryShareImageGenerator = SummaryShareImageGenerator { _, _ ->
            testBitmap()
        },
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalSummaryShareImageGenerator provides generator) {
                FoldlyticsTheme {
                    FoldlyticsScreen(
                        state = state.value,
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

    private fun waitForPreviewImage() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag(SUMMARY_SHARE_PREVIEW_IMAGE_TAG))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun summary(
        coverMillis: Long = hours(18L),
        innerMillis: Long = hours(12L),
    ): PeriodUsageSummary = PeriodUsageSummary(
        period = AnalysisPeriod.DAYS_30,
        rangeStartMillis = 1_753_459_200_000L,
        rangeEndMillis = 1_756_051_200_000L,
        coverMillis = coverMillis,
        innerMillis = innerMillis,
        excludedMillis = hours(1L),
        openedCount = 84,
        closedCount = 80,
        apps = emptyList(),
    )

    private fun testBitmap(): Bitmap = Bitmap.createBitmap(
        SUMMARY_SHARE_IMAGE_WIDTH,
        SUMMARY_SHARE_IMAGE_HEIGHT,
        Bitmap.Config.ARGB_8888,
    ).apply {
        eraseColor(0xFFF4F7FB.toInt())
    }

    private fun hours(value: Long): Long = value * 60L * 60L * 1_000L
}
