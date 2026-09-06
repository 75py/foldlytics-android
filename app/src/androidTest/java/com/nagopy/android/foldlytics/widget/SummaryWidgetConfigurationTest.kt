package com.nagopy.android.foldlytics.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.ui.FoldlyticsTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SummaryWidgetConfigurationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectsAndSavesThePeriodInAShortLandscapeWindow() {
        assertCanSave(Locale.ENGLISH, fontScale = 1f)
    }

    @Test
    fun selectsAndSavesThePeriodWithLargeJapaneseText() {
        assertCanSave(Locale.JAPANESE, fontScale = 2f)
    }

    private fun assertCanSave(locale: Locale, fontScale: Float) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            setLocale(locale)
            this.fontScale = fontScale
        }
        val context = target.createConfigurationContext(configuration)
        var saved: WidgetPeriod? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalResources provides context.resources,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density = 2f, fontScale = fontScale),
            ) {
                Box(
                    Modifier
                        .size(width = 520.dp, height = 240.dp)
                        .testTag(CONFIGURATION_WINDOW_TAG),
                ) {
                    FoldlyticsTheme {
                        SummaryWidgetConfigurationContent(
                            initialPeriod = WidgetPeriod.DAYS_7,
                            onSave = { saved = it },
                        )
                    }
                }
            }
        }
        val viewport = compose.onNodeWithTag(WIDGET_CONFIGURATION_SCROLL_TAG)
        viewport.assert(hasScrollAction())
        val save = compose.onNodeWithTag(WIDGET_CONFIGURATION_SAVE_TAG)
        save.assertIsNotDisplayed()
        compose.onNodeWithText(context.getString(R.string.widget_days_30))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
            .assertIsSelected()
        save.performScrollTo().assertIsDisplayed()
        val viewportBounds = viewport.getUnclippedBoundsInRoot()
        val saveBounds = save.getUnclippedBoundsInRoot()
        assertTrue(saveBounds.top >= viewportBounds.top)
        assertTrue(saveBounds.bottom <= viewportBounds.bottom)
        val bitmap = compose.onNodeWithTag(CONFIGURATION_WINDOW_TAG)
            .captureToImage()
            .asAndroidBitmap()
        val directory = File(
            checkNotNull(target.getExternalFilesDir(null)),
            "widget-configuration-review",
        )
        check(directory.isDirectory || directory.mkdirs())
        val filename = if (fontScale > 1f) "configuration-ja-large.png" else "configuration-en-short.png"
        File(directory, filename).outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        save.performClick()
        compose.runOnIdle { assertEquals(WidgetPeriod.DAYS_30, saved) }
    }

    private companion object {
        const val CONFIGURATION_WINDOW_TAG = "widget_configuration_test_window"
    }
}
