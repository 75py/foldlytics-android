package com.nagopy.android.foldlytics.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InfoLineLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keepsValueVisibleAtNarrowWidthAndLargeFontScale() {
        val label = "Full-history change (first 30 vs latest 30)"
        val value = "+5.9 pts"

        setContent(label = label, value = value, fontScale = 2f)

        assertVisibleWithPositiveWidth(label)
        assertVisibleWithPositiveWidth(value)
        assertValueIsEndAligned(value)
    }

    @Test
    fun keepsLabelVisibleWithLongValueAtNarrowWidth() {
        val label = "Current configuration"
        val value =
            "1768×2208 dp / orientation=portrait / smallest=600 dp / density=420"

        setContent(label = label, value = value, fontScale = 1f)

        assertVisibleWithPositiveWidth(label)
        assertVisibleWithPositiveWidth(value)
        assertValueIsEndAligned(value)
    }

    private fun setContent(label: String, value: String, fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = density.density,
                    fontScale = fontScale,
                ),
            ) {
                FoldlyticsTheme {
                    Box(
                        Modifier
                            .width(256.dp)
                            .testTag(CONTAINER_TAG),
                    ) {
                        InfoLine(label = label, value = value)
                    }
                }
            }
        }
    }

    private fun assertVisibleWithPositiveWidth(text: String) {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true)
        node.assertIsDisplayed()
        assertTrue(
            "$text should retain positive width",
            node.fetchSemanticsNode().boundsInRoot.width > 0f,
        )
    }

    private fun assertValueIsEndAligned(value: String) {
        val containerBounds = composeRule.onNodeWithTag(CONTAINER_TAG).fetchSemanticsNode()
            .boundsInRoot
        val valueBounds = composeRule.onNodeWithText(value, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(containerBounds.right, valueBounds.right, 1f)
    }

    private companion object {
        const val CONTAINER_TAG = "info_line_test_container"
    }
}
