package com.nagopy.android.foldlytics.ui

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
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
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Synthetic before/after screenshots of the diagnostics posture history; no device history.
 * Saved under Download/Foldlytics/diagnostic-history/ on the test device.
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
    )

    @Test
    fun capturesLoadingWithoutPreviousHistory() = capture(
        "after-loading",
        base().withHistory(DiagnosticHistoryStatus.LOADING),
    ) { context ->
        composeRule.onNodeWithText(context.getString(R.string.diagnostic_history_loading))
            .assertIsDisplayed()
    }

    @Test
    fun capturesRefreshingWithPreviousHistory() = capture(
        "after-refreshing",
        base().withHistory(DiagnosticHistoryStatus.LOADING, refreshing = complete),
    ) { context ->
        composeRule.onNodeWithText(context.getString(R.string.diagnostic_history_refreshing))
            .assertIsDisplayed()
    }

    @Test
    fun capturesReadyCompleteHistory() = capture(
        "after-ready",
        base().withHistory(DiagnosticHistoryStatus.READY, events = complete),
    )

    @Test
    fun capturesFailedHistoryWithRetry() {
        var retries = 0
        capture(
            "after-failed-retry",
            base().withHistory(DiagnosticHistoryStatus.FAILED),
            onLoad = { retries += 1 },
        ) { context ->
            val before = retries
            composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
            assertEquals(before + 1, retries)
        }
    }

    @Test
    fun capturesJapaneseRefreshing() = capture(
        "after-refreshing",
        base().withHistory(DiagnosticHistoryStatus.LOADING, refreshing = complete),
        locale = Locale.JAPANESE,
    )

    @Test
    fun capturesJapaneseFailedHistoryWithRetry() = capture(
        "after-failed-retry",
        base().withHistory(DiagnosticHistoryStatus.FAILED),
        locale = Locale.JAPANESE,
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

    private fun capture(
        name: String,
        state: MainUiState,
        locale: Locale = Locale.ENGLISH,
        onLoad: () -> Unit = {},
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
                LocalConfiguration provides configuration,
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
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(context.getString(R.string.posture_events_title)))
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(CAPTURE_TAG).captureToImage().asAndroidBitmap()
        save(target, bitmap, "$name-${locale.language}.png")
        check(context)
    }

    private fun save(context: Context, bitmap: Bitmap, name: String) {
        val path = "Download/Foldlytics/diagnostic-history/"
        val resolver = context.contentResolver
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(path, name),
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        checkNotNull(resolver.openOutputStream(uri)).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
    }

    private companion object {
        const val CAPTURE_TAG = "diagnostic_history_capture"
        const val START_MILLIS = 1_735_689_600_000L
    }
}
