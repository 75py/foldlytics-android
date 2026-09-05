package com.nagopy.android.foldlytics.ui

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import java.util.Locale
import org.junit.Rule
import org.junit.Test

/**
 * Captures the navigation drawer in constrained windows for pull request review.
 *
 * The captures use an empty, synthetic [MainUiState] and a fixed density so the images stay
 * comparable between runs and never contain a user's usage history.
 */
class DrawerOverflowScreenshotCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val englishContext: Context by lazy {
        val context = targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.US)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                Configuration.UI_MODE_NIGHT_NO
        }
        context.createConfigurationContext(configuration)
    }

    @Test
    fun capturesTheDrawerInAShortWindow() {
        openDrawer(widthDp = 520, heightDp = 320, fontScale = 1f)

        capture("short-window.png")
    }

    @Test
    fun capturesTheDrawerAtLargeFontScale() {
        openDrawer(widthDp = 400, heightDp = 560, fontScale = 2f)

        capture("large-font.png")
    }

    @Test
    fun capturesTheScrolledDrawerInAShortWindow() {
        openDrawer(widthDp = 520, heightDp = 320, fontScale = 1f)

        composeRule.onNodeWithText(text(R.string.action_open_source_licenses)).performScrollTo()

        capture("short-window-scrolled.png")
    }

    @Test
    fun capturesTheScrolledDrawerAtLargeFontScale() {
        openDrawer(widthDp = 400, heightDp = 560, fontScale = 2f)

        composeRule.onNodeWithText(text(R.string.action_open_source_licenses)).performScrollTo()

        capture("large-font-scrolled.png")
    }

    private fun openDrawer(widthDp: Int, heightDp: Int, fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides englishContext,
                LocalConfiguration provides englishContext.resources.configuration,
                LocalDensity provides Density(density = CAPTURE_DENSITY, fontScale = fontScale),
            ) {
                Box(
                    Modifier
                        .size(width = widthDp.dp, height = heightDp.dp)
                        .testTag(CAPTURE_WINDOW_TAG),
                ) {
                    FoldlyticsTheme {
                        FoldlyticsScreen(
                            state = MainUiState(hasUsageAccess = true),
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
        }

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu))
            .performClick()
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap: Bitmap = composeRule.onNodeWithTag(CAPTURE_WINDOW_TAG)
            .captureToImage()
            .asAndroidBitmap()
        val resolver = targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, OUTPUT_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        resolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(OUTPUT_PATH, name),
        )
        val outputUri = checkNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "Could not create screenshot media entry: $OUTPUT_PATH$name" }
        try {
            val stream = checkNotNull(resolver.openOutputStream(outputUri)) {
                "Could not open screenshot output: $OUTPUT_PATH$name"
            }
            stream.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not write screenshot: $OUTPUT_PATH$name"
                }
            }
            check(
                resolver.update(
                    outputUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                ) == 1,
            ) { "Could not publish screenshot: $OUTPUT_PATH$name" }
        } catch (error: Throwable) {
            resolver.delete(outputUri, null, null)
            throw error
        }
    }

    private fun text(resourceId: Int): String = englishContext.getString(resourceId)

    companion object {
        private val OUTPUT_PATH =
            "${Environment.DIRECTORY_DOWNLOADS}/Foldlytics/drawer-overflow-review/"
        private const val CAPTURE_DENSITY = 2f
        private const val CAPTURE_WINDOW_TAG = "drawer_capture_window"
    }
}
