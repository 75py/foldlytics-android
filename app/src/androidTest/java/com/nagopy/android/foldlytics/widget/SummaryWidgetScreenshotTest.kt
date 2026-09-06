package com.nagopy.android.foldlytics.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

/** Deterministic RemoteViews fixtures; these images contain no real usage data. */
class SummaryWidgetScreenshotTest {
    @Test
    fun captureSizesThemesLocalesAndUnavailableStates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val output = File(target.getExternalFilesDir(null), "summary-widget-review").apply { mkdirs() }
        instrumentation.runOnMainSync {
            for (locale in listOf(Locale.ENGLISH, Locale.JAPANESE)) {
                for (dark in listOf(false, true)) {
                    for (wide in listOf(false, true)) {
                        for (scale in listOf(1f, 1.3f, 2f)) {
                            for (status in listOf(WidgetStatus.READY, WidgetStatus.NO_DATA, WidgetStatus.PERMISSION_REQUIRED, WidgetStatus.UPDATE_FAILED)) {
                                val context = localizedContext(target, locale, dark, scale)
                                val state = SummaryWidgetState(
                                    period = WidgetPeriod.DAYS_7,
                                    dateRange = WidgetDateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)),
                                    innerMillis = if (status == WidgetStatus.NO_DATA) 0 else 21_600_000,
                                    coverMillis = if (status == WidgetStatus.NO_DATA) 0 else 64_800_000,
                                    openedCount = if (status == WidgetStatus.NO_DATA) 0 else 42,
                                    hasRecordedEvidence = status != WidgetStatus.NO_DATA,
                                    lastSyncMillis = 1_788_688_800_000L,
                                    status = status,
                                )
                                val views = SummaryWidgetRenderer.render(context, 999_999, state, wide)
                                val parent = FrameLayout(context)
                                val view = views.apply(context, parent)
                                val density = context.resources.displayMetrics.density
                                val width = ((if (wide) 280 else 140) * density).toInt()
                                val height = (180 * density).toInt()
                                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                                view.layout(0, 0, width, height)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                view.draw(Canvas(bitmap))
                                val name = "${locale.language}-${if (dark) "dark" else "light"}-${if (wide) "wide" else "small"}-$scale-${status.name.lowercase()}.png"
                                File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun localizedContext(target: Context, locale: Locale, dark: Boolean, scale: Float): Context {
        val configuration = Configuration(target.resources.configuration).apply {
            setLocale(locale)
            fontScale = scale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return target.createConfigurationContext(configuration)
    }
}
