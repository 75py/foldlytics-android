package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.DiagnosticHistoryState
import com.nagopy.android.foldlytics.DiagnosticHistoryStatus
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.DisplayConfiguration
import com.nagopy.android.foldlytics.model.DisplayPosture
import com.nagopy.android.foldlytics.model.PostureEvent
import com.nagopy.android.foldlytics.model.PostureEventSource
import com.nagopy.android.foldlytics.model.UsageAnalysis
import com.nagopy.android.foldlytics.toTimeText
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Synthetic before/after screenshots of the diagnostics posture history; no device history.
 * Exported only on request, to the app's external files directory under diagnostic-history/:
 * `-e exportDiagnosticScreenshots true`.
 */
class DiagnosticHistoryScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val cover = DisplayConfiguration(420, 890, 420, 1, 420)
    private val inner = DisplayConfiguration(730, 820, 730, 2, 420)
    private val complete = (39 downTo 0).map { index ->
        PostureEvent(
            timestampMillis = START_MILLIS + index * 45 * 60_000L,
            source = PostureEventSource.CONFIGURATION_CHANGE,
            posture = if (index % 2 == 0) DisplayPosture.INNER else DisplayPosture.COVER,
            configuration = if (index % 2 == 0) inner else cover,
            rawEventType = 5,
            isBeforeRange = index < 30,
        )
    }

    /** Before (main): the startup analysis itself read and listed the complete history. */
    @Test
    fun capturesBeforeFullHistoryAnalysis() = capture(
        "before-main",
        base().let { it.copy(analysis = it.analysis?.copy(postureEvents = complete)) },
        focus = Focus.EVENTS,
    )

    @Test
    fun capturesLoadingWithoutPreviousHistory() = capture(
        "after-loading",
        base().withHistory(DiagnosticHistoryStatus.LOADING),
        expectedTexts = { listOf(it.getString(R.string.diagnostic_history_loading)) },
    )

    @Test
    fun capturesRefreshingWithPreviousHistory() = capture(
        "after-refreshing",
        base().withHistory(DiagnosticHistoryStatus.LOADING, refreshing = complete),
        expectedTexts = { listOf(it.getString(R.string.diagnostic_history_refreshing)) },
    )

    @Test
    fun capturesReadyCompleteHistory() = capture(
        "after-ready",
        base().withHistory(DiagnosticHistoryStatus.READY, events = complete),
        focus = Focus.EVENTS,
    )

    @Test
    fun capturesFailedHistoryWithRetry() {
        var retries = 0
        capture(
            "after-failed-retry",
            base().withHistory(DiagnosticHistoryStatus.FAILED),
            onLoad = { retries += 1 },
            expectedTexts = {
                listOf(
                    it.getString(R.string.diagnostic_history_failed),
                    it.getString(R.string.action_retry),
                )
            },
        ) { context ->
            val before = retries
            composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
            assertEquals(before + 1, retries)
        }
    }

    @Test
    fun capturesJapaneseLoading() = capture(
        "after-loading",
        base().withHistory(DiagnosticHistoryStatus.LOADING),
        locale = Locale.JAPANESE,
        expectedTexts = { listOf("姿勢イベントの全履歴を読み込んでいます…") },
    )

    @Test
    fun capturesJapaneseRefreshing() = capture(
        "after-refreshing",
        base().withHistory(DiagnosticHistoryStatus.LOADING, refreshing = complete),
        locale = Locale.JAPANESE,
        expectedTexts = {
            listOf("更新中です。新しい全履歴の準備ができるまで、前回の全履歴を表示しています。")
        },
    )

    @Test
    fun capturesJapaneseFailedHistoryWithRetry() = capture(
        "after-failed-retry",
        base().withHistory(DiagnosticHistoryStatus.FAILED),
        locale = Locale.JAPANESE,
        expectedTexts = {
            listOf("姿勢イベントの全履歴を読み込めませんでした。もう一度お試しください。", "再試行")
        },
    )

    /**
     * Prepending a newer event, as a refresh does, must not move the rows the user is reading.
     */
    @Test
    fun prependedEventKeepsTheVisibleRowInPlace() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val events = (39 downTo 0).map { index -> event(START_MILLIS + index * 7 * 60_000L, index) }
        var state by mutableStateOf(base().withHistory(DiagnosticHistoryStatus.READY, events = events))
        composeRule.setContent {
            FoldlyticsTheme {
                Surface {
                    Box(Modifier.size(360.dp, 720.dp)) {
                        DiagnosticsContent(state = state, scaffoldPadding = PaddingValues(0.dp))
                    }
                }
            }
        }
        val anchor = events[20].timestampMillis.toTimeText(target.resources)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(anchor))
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithText(anchor).fetchSemanticsNode().boundsInRoot.top

        state = state.withHistory(
            DiagnosticHistoryStatus.READY,
            events = listOf(event(START_MILLIS + 40 * 7 * 60_000L, 40)) + events,
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText(anchor).assertIsDisplayed()
        assertEquals(
            before,
            composeRule.onNodeWithText(anchor).fetchSemanticsNode().boundsInRoot.top,
            0.5f,
        )
    }

    private fun event(timestampMillis: Long, index: Int) = PostureEvent(
        timestampMillis = timestampMillis,
        source = PostureEventSource.CONFIGURATION_CHANGE,
        posture = if (index % 2 == 0) DisplayPosture.INNER else DisplayPosture.COVER,
        configuration = if (index % 2 == 0) inner else cover,
        rawEventType = 5,
        isBeforeRange = index < 30,
    )

    private fun base() = MainUiState(
        hasUsageAccess = true,
        currentConfiguration = inner,
        analysis = UsageAnalysis(
            rangeStartMillis = START_MILLIS + 30 * 45 * 60_000L,
            rangeEndMillis = START_MILLIS + 40 * 45 * 60_000L,
            coverMillis = 3_600_000L,
            innerMillis = 5_400_000L,
            excludedPostureMillis = 0L,
            excludedPostureMillisByReason = emptyMap(),
            openedCount = 5,
            closedCount = 5,
            evidenceGapCount = 0,
            foldTransitions = emptyList(),
            dailySummaries = emptyList(),
            apps = emptyList(),
            // The compact startup seed keeps only a few lookback events.
            postureEvents = complete.take(13),
            eventCount = 120,
            multiResumeMillis = 0L,
        ),
    )

    private fun MainUiState.withHistory(
        status: DiagnosticHistoryStatus,
        events: List<PostureEvent>? = null,
        refreshing: List<PostureEvent>? = null,
    ) = copy(
        diagnosticHistory = DiagnosticHistoryState(
            generation = 2L,
            status = status,
            events = events,
            refreshingEvents = refreshing,
        ),
    )

    private enum class Focus { STATUS, EVENTS }

    private fun capture(
        name: String,
        state: MainUiState,
        locale: Locale = Locale.ENGLISH,
        focus: Focus = Focus.STATUS,
        onLoad: () -> Unit = {},
        expectedTexts: (Context) -> List<String> = { emptyList() },
        check: (Context) -> Unit = {},
    ) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            setLocale(locale)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_NO
        }
        val context = target.createConfigurationContext(configuration)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalResources provides context.resources,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    Surface {
                        Box(Modifier.size(360.dp, 720.dp).testTag(CAPTURE_TAG)) {
                            DiagnosticsContent(
                                state = state,
                                scaffoldPadding = PaddingValues(0.dp),
                                onLoadDiagnosticHistory = onLoad,
                            )
                        }
                    }
                }
            }
        }
        // Bring the part this capture is about into view, and prove it is visible.
        val list = composeRule.onNode(hasScrollAction())
        when (focus) {
            Focus.STATUS -> {
                list.performScrollToNode(hasTestTag(DIAGNOSTIC_HISTORY_STATUS_TAG))
                composeRule.onNodeWithTag(DIAGNOSTIC_HISTORY_STATUS_TAG).assertIsDisplayed()
            }
            Focus.EVENTS -> {
                val newest = complete.first().timestampMillis.toTimeText(context.resources)
                list.performScrollToNode(hasText(newest))
                composeRule.onNodeWithText(newest).assertIsDisplayed()
            }
        }
        composeRule.waitForIdle()
        expectedTexts(context).forEach { text ->
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
        exportIfRequested(
            composeRule.onNodeWithTag(CAPTURE_TAG).captureToImage().asAndroidBitmap(),
            "$name-${locale.language}.png",
        )
        check(context)
    }

    /**
     * Writes a PNG only when the run opts in with `-e exportDiagnosticScreenshots true`, into the
     * app's own external files directory, so routine runs leave shared storage untouched.
     */
    private fun exportIfRequested(bitmap: Bitmap, name: String) {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString(EXPORT_ARGUMENT) != "true") return
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(target.getExternalFilesDir(null)), EXPORT_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        File(directory, name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private companion object {
        const val CAPTURE_TAG = "diagnostic_history_capture"
        const val EXPORT_ARGUMENT = "exportDiagnosticScreenshots"
        const val EXPORT_DIRECTORY = "diagnostic-history"
        const val START_MILLIS = 1_735_689_600_000L
    }
}
