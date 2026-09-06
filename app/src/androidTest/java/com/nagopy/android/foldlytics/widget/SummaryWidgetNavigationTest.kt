package com.nagopy.android.foldlytics.widget

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.MainUiState
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.ui.FoldlyticsScreen
import com.nagopy.android.foldlytics.ui.FoldlyticsTheme
import com.nagopy.android.foldlytics.ui.LIVE_STATE_CARD_TAG
import org.junit.Rule
import org.junit.Test

class SummaryWidgetNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun widgetOpenReturnsToHomeAndLaterNavigationStillWorks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = mutableIntStateOf(0)
        compose.setContent {
            FoldlyticsTheme {
                FoldlyticsScreen(
                    state = MainUiState(hasUsageAccess = true),
                    onOpenUsageAccess = {}, onSaveCover = {}, onSaveInner = {},
                    onClearCalibration = {}, onPeriodChanged = {}, onCustomPeriodChanged = { _, _ -> },
                    onRefresh = {}, onShare = {}, onExportCsv = {}, onOpenPrivacyPolicy = {}, onOpenOssLicenses = {},
                    homeNavigationRequest = request.intValue,
                    appName = "Widget navigation fixture",
                )
            }
        }
        repeat(2) {
            compose.onNodeWithContentDescription(context.getString(R.string.content_desc_open_menu)).performClick()
            compose.onNodeWithText(context.getString(R.string.screen_calibration)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(LIVE_STATE_CARD_TAG).assertDoesNotExist()
            compose.runOnIdle { request.intValue += 1 }
            compose.onNodeWithTag(LIVE_STATE_CARD_TAG).assertIsDisplayed()
        }
    }
}
