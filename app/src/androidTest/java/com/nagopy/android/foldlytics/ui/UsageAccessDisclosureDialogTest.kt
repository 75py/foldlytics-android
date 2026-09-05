package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UsageAccessDisclosureDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keepsDisclosureActionsVisibleWhileBodyScrollsInACompactEnglishDialog() {
        assertDialogIsUsable(Locale.ENGLISH)
    }

    @Test
    fun keepsDisclosureActionsVisibleWhileBodyScrollsInACompactJapaneseDialog() {
        assertDialogIsUsable(Locale.JAPANESE)
    }

    private fun assertDialogIsUsable(locale: Locale) {
        val context = localizedContext(locale)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalResources provides context.resources,
                LocalConfiguration provides context.resources.configuration,
            ) {
                FoldlyticsTheme {
                    UsageAccessDisclosureDialog(
                        hasAccess = false,
                        onDismiss = {},
                        onContinue = {},
                        modifier = Modifier.heightIn(max = 320.dp),
                    )
                }
            }
        }

        val body = composeRule.onNodeWithTag(USAGE_ACCESS_DISCLOSURE_BODY_TAG)
        body.assert(hasScrollAction())
        val bodyEnd = context.getString(R.string.usage_access_disclosure_body)
            .substringAfterLast("\n\n")
        body.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, 10_000f)
        }
        val bodyViewport = body.getUnclippedBoundsInRoot()
        val bodyEndBounds = composeRule.onNodeWithText(bodyEnd)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(bodyViewport.bottom.value, bodyEndBounds.bottom.value, 2f)
        composeRule.onNodeWithText(context.getString(R.string.usage_access_disclosure_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_not_now))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_agree_and_open_settings))
            .assertIsDisplayed()
    }

    private fun localizedContext(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
