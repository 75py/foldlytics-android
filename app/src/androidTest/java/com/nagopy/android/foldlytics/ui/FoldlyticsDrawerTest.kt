package com.nagopy.android.foldlytics.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Guards the navigation drawer against overflowing short windows and large font scales. */
class FoldlyticsDrawerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val englishContext: Context by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.US)
        }
        context.createConfigurationContext(configuration)
    }

    @Test
    fun reachesEveryDrawerActionInAShortWindow() {
        var privacyPolicyOpened = false
        var ossLicensesOpened = false
        openDrawer(
            widthDp = 520,
            heightDp = 320,
            fontScale = 1f,
            onOpenPrivacyPolicy = { privacyPolicyOpened = true },
            onOpenOssLicenses = { ossLicensesOpened = true },
        )

        composeRule.onNodeWithText(text(R.string.action_save_all_csv))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_usage_access_settings))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.drawer_privacy_note))
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.action_privacy_policy))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(privacyPolicyOpened) }

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu))
            .performClick()
        composeRule.onNodeWithText(text(R.string.action_open_source_licenses))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(ossLicensesOpened) }
    }

    @Test
    fun reachesTheLastDrawerActionAtLargeFontScale() {
        var ossLicensesOpened = false
        openDrawer(
            widthDp = 400,
            heightDp = 560,
            fontScale = 2f,
            onOpenOssLicenses = { ossLicensesOpened = true },
        )

        composeRule.onNodeWithTag(DRAWER_CONTENT_TAG).assert(hasScrollAction())
        composeRule.onNodeWithText(text(R.string.action_open_source_licenses))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertTrue(ossLicensesOpened) }
    }

    @Test
    fun keepsThePrivacyNoteAtTheBottomWhenTheDrawerFits() {
        openDrawer(widthDp = 400, heightDp = 900, fontScale = 1f)

        composeRule.onNodeWithText(text(R.string.nav_home)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_open_source_licenses)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.drawer_privacy_note)).assertIsDisplayed()

        val lastActionBottom = composeRule
            .onNodeWithText(text(R.string.action_open_source_licenses))
            .getUnclippedBoundsInRoot()
            .bottom
        val privacyNoteTop = composeRule
            .onNodeWithText(text(R.string.drawer_privacy_note))
            .getUnclippedBoundsInRoot()
            .top

        assertTrue(
            "Expected the privacy note to stay at the bottom of the drawer, " +
                "but it followed the last action at $privacyNoteTop after $lastActionBottom",
            privacyNoteTop - lastActionBottom > 100.dp,
        )
    }

    private fun openDrawer(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float,
        onOpenPrivacyPolicy: () -> Unit = {},
        onOpenOssLicenses: () -> Unit = {},
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides englishContext,
                LocalConfiguration provides englishContext.resources.configuration,
                LocalDensity provides Density(density = TEST_DENSITY, fontScale = fontScale),
            ) {
                Box(
                    Modifier
                        .size(width = widthDp.dp, height = heightDp.dp)
                        .testTag(TEST_WINDOW_TAG),
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
                            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                            onOpenOssLicenses = onOpenOssLicenses,
                            appName = "Foldlytics",
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription(text(R.string.content_desc_open_menu))
            .performClick()
    }

    private fun text(resourceId: Int): String = englishContext.getString(resourceId)

    companion object {
        private const val TEST_DENSITY = 2f
        private const val TEST_WINDOW_TAG = "drawer_test_window"
    }
}
