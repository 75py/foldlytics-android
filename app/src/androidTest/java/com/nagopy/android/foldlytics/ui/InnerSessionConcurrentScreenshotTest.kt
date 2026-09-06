package com.nagopy.android.foldlytics.ui

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.model.InnerSessionAppUsage
import com.nagopy.android.foldlytics.model.InnerSessionDetail
import com.nagopy.android.foldlytics.model.InnerSessionSummary
import com.nagopy.android.foldlytics.toDurationText
import com.nagopy.android.foldlytics.toInnerSessionStartText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic screenshots and narrow-width localization/accessibility checks; no device history. */
class InnerSessionConcurrentScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun capturesEnglishExclusiveCombination() = capture(Locale.ENGLISH, false)
    @Test
    fun capturesJapaneseExclusiveCombination() = capture(Locale.JAPANESE, false)
    @Test
    fun capturesEnglishLegacyOtherBaseline() = capture(Locale.ENGLISH, true)
    @Test
    fun capturesJapaneseLegacyOtherBaseline() = capture(Locale.JAPANESE, true)

    private fun capture(locale: Locale, legacy: Boolean) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            setLocale(locale)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        val context = target.createConfigurationContext(configuration)
        val chrome = InnerSessionAppUsage("com.example.synthetic.chrome", "Chrome", 300_000)
        val youtube = InnerSessionAppUsage("com.example.synthetic.youtube", "YouTube", 300_000)
        val simultaneous = InnerSessionAppUsage(
            packageName = "com.example.synthetic.chrome+com.example.synthetic.youtube",
            label = "Chrome + YouTube",
            innerActiveMillis = 600_000,
            packageNames = listOf(chrome.packageName, youtube.packageName),
            labels = listOf(chrome.label, youtube.label),
        )
        val detail = InnerSessionDetail(
            openedAtMillis = 1_735_689_600_000,
            openedSequenceAtTimestamp = 0,
            innerActiveMillis = 1_200_000,
            appUsages = if (legacy) listOf(chrome, youtube) else listOf(simultaneous, chrome, youtube),
            otherInnerActiveMillis = if (legacy) 600_000 else 0,
        )
        val summary = InnerSessionSummary(
            rangeStartMillis = 0,
            rangeEndMillis = 1_735_776_000_000,
            detectedOpenCount = 1,
            completeSessionCount = 1,
            medianInnerActiveMillis = 1_200_000,
            averageInnerActiveMillis = 1_200_000,
            longestInnerActiveMillis = 1_200_000,
            longSessions = listOf(detail),
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(2f, 1f),
            ) {
                FoldlyticsTheme {
                    Surface {
                        Box(Modifier.width(320.dp).padding(12.dp).testTag("concurrent_capture")) {
                            InnerSessionLongSessionsCard(summary)
                        }
                    }
                }
            }
        }
        // Save before assertions so a failed layout check still has visual evidence.
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag("concurrent_capture").captureToImage().asAndroidBitmap()
        save(target, bitmap, "${if (legacy) "before" else "after"}-${locale.language}.png")
        if (!legacy) {
            val visibleName = if (locale.language == "ja") {
                "Chrome＋YouTube"
            } else {
                "Chrome + YouTube"
            }
            assertTextFits(visibleName)
            val subtitle = context.resources.getString(R.string.inner_session_simultaneous_subtitle)
            assertTextFits(subtitle)
            assertConcurrentRowLayout(simultaneous, subtitle)
            val resources = context.resources
            val description = resources.getString(
                R.string.content_desc_inner_session_detail_without_other,
                detail.openedAtMillis.toInnerSessionStartText(resources),
                detail.innerActiveMillis.toDurationText(resources),
                detail.appUsages.joinToString(
                    resources.getString(R.string.content_desc_inner_session_app_separator),
                ) {
                    resources.getString(
                        R.string.content_desc_inner_session_app,
                        it.displayLabel(resources),
                        it.innerActiveMillis.toDurationText(resources),
                    )
                },
            )
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        assertTextFits(detail.openedAtMillis.toInnerSessionStartText(context.resources))
    }

    private fun assertConcurrentRowLayout(app: InnerSessionAppUsage, subtitle: String) {
        val rowBounds = composeRule.onNodeWithTag(
            "$INNER_SESSION_APP_TAG_PREFIX${app.packageName}",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val stackBounds = composeRule.onNodeWithTag(
            "$INNER_SESSION_APP_ICONS_TAG_PREFIX${app.packageName}",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val firstIconBounds = composeRule.onNodeWithTag(
            "$INNER_SESSION_APP_ICON_TAG_PREFIX${app.packageName}_0",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val secondIconBounds = composeRule.onNodeWithTag(
            "$INNER_SESSION_APP_ICON_TAG_PREFIX${app.packageName}_1",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val subtitleBounds = composeRule.onNodeWithText(
            subtitle,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val durationBounds = composeRule.onNodeWithTag(
            "$INNER_SESSION_APP_DURATION_TAG_PREFIX${app.packageName}",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

        assertEquals("The icon column must remain the singleton width", 88f, stackBounds.width, 0.01f)
        assertEquals("The icon stack must fit its two icons", 136f, stackBounds.height, 0.01f)
        assertTrue("The two icons must be vertically stacked", firstIconBounds.bottom < secondIconBounds.top)
        assertEquals("The two icons must share a column", firstIconBounds.left, secondIconBounds.left, 0.01f)
        assertTrue("The subtitle must remain beside the icons", subtitleBounds.left > stackBounds.right)
        assertTrue("The duration must remain inside the row", durationBounds.right <= rowBounds.right)
    }

    private fun assertTextFits(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayoutResult ->
                assertTrue(getLayoutResult(results))
            }
        val layout = results.single()
        assertFalse(
            "Text must not be clipped or ellipsized: $text; size=${layout.size}, " +
                "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                "overflowWidth=${layout.didOverflowWidth}, overflowHeight=${layout.didOverflowHeight}, " +
                "lines=${layout.lineCount}",
            layout.hasVisualOverflow,
        )
    }

    private fun save(context: Context, bitmap: Bitmap, name: String) {
        val path = "Download/Foldlytics/inner-session-concurrent/"
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
}
