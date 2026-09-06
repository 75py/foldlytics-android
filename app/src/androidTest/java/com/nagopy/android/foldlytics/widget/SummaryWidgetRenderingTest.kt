package com.nagopy.android.foldlytics.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.ui.DisplayChartPalette
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min
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

    @Test
    fun normalPayloadRemainsLegibleWhenHostFontScaleChangesAfterRendering() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val target = instrumentation.targetContext
            var checkedCases = 0
            var checkedTextViews = 0
            val states = listOf(
                "ready" to fixture().copy(lastSyncMillis = 1_788_688_800_000L),
                "all-inner" to fixture().copy(coverMillis = 0L, lastSyncMillis = 1_788_688_800_000L),
                "update-failed" to fixture().copy(
                    lastSyncMillis = 1_788_688_800_000L,
                    status = WidgetStatus.UPDATE_FAILED,
                ),
                "no-data" to fixture().copy(
                    innerMillis = 0L,
                    coverMillis = 0L,
                    openedCount = 0,
                    hasRecordedEvidence = false,
                    lastSyncMillis = 1_788_688_800_000L,
                    status = WidgetStatus.NO_DATA,
                ),
            )
            for (locale in listOf(Locale.ENGLISH, Locale.JAPANESE)) {
                val producer = localizedContext(target, locale, fontScale = 1f)
                for ((stateName, state) in states) {
                    for (wide in listOf(false, true)) {
                        // Reuse this exact payload: the producer does not run when the
                        // launcher reinflates it at the new font scale.
                        val payload = SummaryWidgetRenderer.render(producer, 999_999, state, wide)
                        for (hostScale in listOf(0.85f, 1f, 2f)) {
                            val host = localizedContext(target, locale, fontScale = hostScale)
                            val view = payload.apply(host, FrameLayout(host))
                            layout(view, host, wide)
                            val case = "${locale.language}, wide=$wide, $stateName, host=$hostScale"
                            saveCapture(view, target, locale, wide, "$stateName-font$hostScale")

                            assertEquals(visibleWidth(wide, host), view.measuredWidth)
                            assertEquals((180 * host.resources.displayMetrics.density).toInt(), view.measuredHeight)
                            val expectedIds = buildSet {
                                addAll(listOf(R.id.widget_period, R.id.widget_ratio, R.id.widget_sync))
                                if (state.innerRatio != null) add(R.id.widget_ratio_label)
                                if (state.status != WidgetStatus.READY) add(R.id.widget_status)
                                if (wide) addAll(METRIC_TEXT_IDS)
                            }
                            val visibleTextViews = (listOf(
                                R.id.widget_period,
                                R.id.widget_ratio,
                                R.id.widget_ratio_label,
                                R.id.widget_status,
                                R.id.widget_sync,
                            ) + METRIC_TEXT_IDS)
                                .map { view.findViewById<TextView>(it) }
                                .filter { isEffectivelyVisible(view, it) }
                            assertEquals("Visible text coverage: $case", expectedIds, visibleTextViews.map { it.id }.toSet())
                            visibleTextViews.forEach { text ->
                                assertTextFits(text, case)
                                assertWithinRoot(view, text)
                            }
                            assertCenterTextInsideHole(view, R.id.widget_ratio, case)
                            assertCenterTextInsideHole(view, R.id.widget_ratio_label, case)
                            if (wide) {
                                listOf(
                                    R.id.widget_inner_label to R.id.widget_inner_time,
                                    R.id.widget_cover_label to R.id.widget_cover_time,
                                    R.id.widget_opens_label to R.id.widget_open_count,
                                ).forEach { (labelId, valueId) -> assertNonOverlapping(view, labelId, valueId) }
                            }
                            checkedCases += 1
                            checkedTextViews += visibleTextViews.size
                        }
                    }
                }
            }
            // Offscreen RemoteViews are not attached: isShown would skip every assertion.
            assertEquals(48, checkedCases)
            assertEquals(348, checkedTextViews)
        }
    }

    private fun layout(view: View, context: android.content.Context, wide: Boolean) {
        val density = context.resources.displayMetrics.density
        val width = visibleWidth(wide, context)
        val height = (180 * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun localizedContext(
        target: android.content.Context,
        locale: Locale,
        fontScale: Float,
    ): android.content.Context = target.createConfigurationContext(
        android.content.res.Configuration(target.resources.configuration).apply {
            setLocale(locale)
            this.fontScale = fontScale
        },
    )

    private fun visibleWidth(wide: Boolean, context: android.content.Context): Int =
        ((if (wide) 280 else 140) * context.resources.displayMetrics.density).toInt()

    private fun assertTextFits(textView: TextView, case: String) {
        val name = "$case: ${textView.resources.getResourceEntryName(textView.id)}"
        val layout = checkNotNull(textView.layout)
        assertTrue("$name has no rendered text", layout.lineCount > 0)
        val minimumDp = if (textView.resources.configuration.fontScale < 1f) 8f else 10f
        assertTrue(
            "$name text is below the ${minimumDp}dp readable floor",
            textView.paint.textSize + 0.5f >= minimumDp * textView.resources.displayMetrics.density,
        )
        assertTrue(
            "$name exceeds its measured height: layout=${layout.height}, view=${textView.height}, " +
                "textSize=${textView.paint.textSize}, text=${textView.text}",
            layout.height <= textView.height,
        )
        assertEquals(
            "$name text is clipped",
            textView.text.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
        for (line in 0 until layout.lineCount) {
            assertEquals("$name text is ellipsized", 0, layout.getEllipsisCount(line))
            assertTrue(
                "$name line exceeds its bounds",
                layout.getLineWidth(line) <= textView.width - textView.paddingLeft - textView.paddingRight + 1f,
            )
        }
    }

    private fun assertWithinRoot(root: View, child: View) {
        val bounds = Rect(0, 0, child.width, child.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(child, bounds)
        assertTrue("${child.id} extends beyond the widget", bounds.left >= 0)
        assertTrue("${child.id} extends beyond the widget", bounds.top >= 0)
        assertTrue("${child.id} extends beyond the widget", bounds.right <= root.width)
        assertTrue("${child.id} extends beyond the widget", bounds.bottom <= root.height)
    }

    private fun assertNonOverlapping(root: View, firstId: Int, secondId: Int) {
        val first = root.findViewById<View>(firstId)
        val second = root.findViewById<View>(secondId)
        if (!isEffectivelyVisible(root, first) || !isEffectivelyVisible(root, second)) return
        val firstBounds = boundsInRoot(root, first)
        val secondBounds = boundsInRoot(root, second)
        assertTrue(
            "$firstId and $secondId overlap",
            firstBounds.right <= secondBounds.left ||
                secondBounds.right <= firstBounds.left ||
                firstBounds.bottom <= secondBounds.top ||
                secondBounds.bottom <= firstBounds.top,
        )
    }

    private fun boundsInRoot(root: View, child: View): Rect =
        Rect(0, 0, child.width, child.height).also {
            (root as ViewGroup).offsetDescendantRectToMyCoords(child, it)
        }

    private fun assertCenterTextInsideHole(root: View, childId: Int, case: String) {
        val chart = root.findViewById<View>(R.id.widget_chart)
        val child = root.findViewById<TextView>(childId)
        if (!isEffectivelyVisible(root, child)) return
        val layout = checkNotNull(child.layout)
        assertEquals("Center text must remain on one line: $case", 1, layout.lineCount)
        val childBounds = boundsInRoot(chart, child)
        val ink = Rect()
        val text = child.text.toString()
        child.paint.getTextBounds(text, 0, text.length, ink)
        val drawn = RectF(ink).apply {
            offset(
                childBounds.left + child.compoundPaddingLeft + layout.getLineLeft(0),
                (childBounds.top + child.totalPaddingTop + layout.getLineBaseline(0)).toFloat(),
            )
        }
        // Arc mask: 104px radius minus half of the 36px stroke, on a 256px bitmap.
        val holeRadius = min(chart.width, chart.height) * 86f / 256f
        val centerX = chart.width / 2f
        val centerY = chart.height / 2f
        for (x in listOf(drawn.left, drawn.right)) {
            for (y in listOf(drawn.top, drawn.bottom)) {
                assertTrue(
                    "$case: ${child.resources.getResourceEntryName(childId)} overlaps the donut: " +
                        "ink=$drawn, chart=${chart.width}x${chart.height}, radius=$holeRadius",
                    hypot(x - centerX, y - centerY) <= holeRadius + 1f,
                )
            }
        }
    }

    private fun isEffectivelyVisible(root: View, child: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }

    private fun saveCapture(
        view: View,
        target: android.content.Context,
        locale: Locale,
        wide: Boolean,
        stateName: String,
    ) {
        val directory = target.getExternalFilesDir("summary-widget-font-scale") ?: return
        directory.mkdirs()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(
            directory,
            "${locale.language}-${if (wide) "wide" else "small"}-$stateName.png",
        ).outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
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

    private companion object {
        val METRIC_TEXT_IDS = listOf(
            R.id.widget_inner_label,
            R.id.widget_inner_time,
            R.id.widget_cover_label,
            R.id.widget_cover_time,
            R.id.widget_opens_label,
            R.id.widget_open_count,
        )
    }

    private fun fixture() = SummaryWidgetState(
        period = WidgetPeriod.DAYS_7,
        dateRange = WidgetDateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)),
        innerMillis = 3_600_000, coverMillis = 10_800_000, openedCount = 4,
        hasRecordedEvidence = true, status = WidgetStatus.READY,
    )
}
