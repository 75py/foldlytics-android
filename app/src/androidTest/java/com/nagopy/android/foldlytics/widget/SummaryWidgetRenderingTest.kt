package com.nagopy.android.foldlytics.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.ui.DisplayChartPalette
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryWidgetRenderingTest {
    @Test
    fun restoresChartAndMetricsWhenPermissionReturnsOnReapply() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext.createConfigurationContext(Configuration().apply { fontScale = 1f })
            val state = fixture().copy(status = WidgetStatus.PERMISSION_REQUIRED)
            val view = SummaryWidgetRenderer.render(context, 999_999, state, wide = true).apply(context, FrameLayout(context))
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_chart).visibility)
            SummaryWidgetRenderer.render(context, 999_999, fixture(), wide = true).reapply(context, view)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_chart).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_metrics).visibility)
            assertEquals("25%", view.findViewById<TextView>(R.id.widget_ratio).text.toString())
            assertEquals(
                context.getString(R.string.widget_duration, 1, 0),
                view.findViewById<TextView>(R.id.widget_inner_time).text.toString(),
            )
            assertEquals(
                context.getString(R.string.widget_duration, 3, 0),
                view.findViewById<TextView>(R.id.widget_cover_time).text.toString(),
            )
            assertEquals("4", view.findViewById<TextView>(R.id.widget_open_count).text.toString())
            val empty = fixture().copy(innerMillis = 0, coverMillis = 0, openedCount = 0, hasRecordedEvidence = false, status = WidgetStatus.NO_DATA)
            SummaryWidgetRenderer.render(context, 999_999, empty, wide = true).reapply(context, view)
            assertEquals("—", view.findViewById<TextView>(R.id.widget_open_count).text.toString())
        }
    }

    @Test
    fun hostReinflationResolvesArcAndLabelColorsWithoutRegeneratingThePayload() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val target = instrumentation.targetContext
            fun context(night: Int) = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
                fontScale = 1f
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            })
            val light = context(Configuration.UI_MODE_NIGHT_NO)
            val dark = context(Configuration.UI_MODE_NIGHT_YES)
            val payload = SummaryWidgetRenderer.render(light, 999_999, fixture(), wide = true)
            for ((context, inner, cover) in listOf(
                Triple(light, DisplayChartPalette.LIGHT_INNER, DisplayChartPalette.LIGHT_COVER),
                Triple(dark, DisplayChartPalette.DARK_INNER, DisplayChartPalette.DARK_COVER),
            )) {
                val view = payload.apply(context, FrameLayout(context))
                layout(view, context, wide = true)
                assertEquals(inner, view.findViewById<ImageView>(R.id.widget_inner_arc).imageTintList?.defaultColor)
                assertEquals(cover, view.findViewById<ImageView>(R.id.widget_cover_arc).imageTintList?.defaultColor)
                assertEquals(inner, view.findViewById<TextView>(R.id.widget_inner_label).currentTextColor)
                assertEquals(cover, view.findViewById<TextView>(R.id.widget_cover_label).currentTextColor)
                assertTrue(
                    "inner arc did not render with host tint $inner",
                    view.findViewById<ImageView>(R.id.widget_inner_arc).containsPixelColor(inner),
                )
                assertTrue(
                    "cover arc did not render with host tint $cover",
                    view.findViewById<ImageView>(R.id.widget_cover_arc).containsPixelColor(cover),
                )
            }
        }
    }

    private fun layout(view: View, context: android.content.Context, wide: Boolean) {
        val density = context.resources.displayMetrics.density
        val width = ((if (wide) 280 else 140) * density).toInt()
        val height = (180 * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun ImageView.containsPixelColor(color: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return pixels.any { it == color }
    }

    private fun fixture() = SummaryWidgetState(
        period = WidgetPeriod.DAYS_7,
        dateRange = WidgetDateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)),
        innerMillis = 3_600_000, coverMillis = 10_800_000, openedCount = 4,
        hasRecordedEvidence = true, status = WidgetStatus.READY,
    )
}
