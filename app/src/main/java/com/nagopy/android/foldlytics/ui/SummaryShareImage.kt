package com.nagopy.android.foldlytics.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.nagopy.android.foldlytics.R
import com.nagopy.android.foldlytics.labelRes
import com.nagopy.android.foldlytics.model.PeriodUsageSummary
import com.nagopy.android.foldlytics.toDurationText
import com.nagopy.android.foldlytics.toShortDateText

internal const val SUMMARY_SHARE_IMAGE_WIDTH = 1200
internal const val SUMMARY_SHARE_IMAGE_HEIGHT = 675

internal data class SummaryShareContent(
    val brand: String,
    val deviceName: String,
    val period: String,
    val innerRatioLabel: String,
    val innerRatio: String,
    val coverTimeLabel: String,
    val coverTime: String,
    val innerTimeLabel: String,
    val innerTime: String,
    val openedCountLabel: String,
    val openedCount: String,
) {
    val visibleTexts: List<String> = listOf(
        brand,
        deviceName,
        period,
        innerRatioLabel,
        innerRatio,
        coverTimeLabel,
        coverTime,
        innerTimeLabel,
        innerTime,
        openedCountLabel,
        openedCount,
    )

    val accessibilityDescription: String = visibleTexts.joinToString(separator = ", ")
}

internal data class SummaryShareTextMeasurement(
    val text: String,
    val textSizePx: Float,
    val measuredWidthPx: Float,
    val availableWidthPx: Float,
    val measuredHeightPx: Float,
    val availableHeightPx: Float,
) {
    val fits: Boolean =
        measuredWidthPx <= availableWidthPx && measuredHeightPx <= availableHeightPx
}

internal data class SummaryShareRenderResult(
    val bitmap: Bitmap,
    val textMeasurements: List<SummaryShareTextMeasurement>,
)

internal fun interface SummaryShareImageGenerator {
    suspend fun generate(resources: Resources, summary: PeriodUsageSummary): Bitmap
}

internal object SummaryShareImageRenderer {
    private const val BackgroundColor = 0xFFF4F7FB.toInt()
    private const val SurfaceColor = 0xFFFFFFFF.toInt()
    private const val PrimaryTextColor = 0xFF10233A.toInt()
    private const val SecondaryTextColor = 0xFF526273.toInt()
    private const val DividerColor = 0xFFDCE3EB.toInt()
    private const val CoverColor = 0xFFE87526.toInt()
    private const val InnerColor = 0xFF2276C5.toInt()

    private val RegularTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val MediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val BoldTypeface = Typeface.create("sans-serif", Typeface.BOLD)

    fun createContent(
        resources: Resources,
        summary: PeriodUsageSummary,
        deviceName: String = formatDeviceName(Build.MANUFACTURER, Build.MODEL),
    ): SummaryShareContent {
        require(summary.classifiedMillis > 0L) {
            "A share image requires classified usage time"
        }
        val rangeText = resources.getString(
            R.string.date_range,
            summary.rangeStartMillis.toShortDateText(resources),
            (summary.rangeEndMillis - 1L).coerceAtLeast(0L).toShortDateText(resources),
        )
        return SummaryShareContent(
            brand = resources.getString(R.string.share_summary_brand),
            deviceName = deviceName,
            period = resources.getString(
                R.string.share_summary_period,
                resources.getString(summary.period.labelRes),
                rangeText,
            ),
            innerRatioLabel = resources.getString(R.string.share_summary_inner_ratio),
            innerRatio = resources.getString(
                R.string.value_percent_0,
                summary.innerRatio * 100f,
            ),
            coverTimeLabel = resources.getString(R.string.share_summary_cover_time),
            coverTime = summary.coverMillis.toDurationText(resources),
            innerTimeLabel = resources.getString(R.string.share_summary_inner_time),
            innerTime = summary.innerMillis.toDurationText(resources),
            openedCountLabel = resources.getString(R.string.share_summary_opened_count),
            openedCount = resources.getString(R.string.value_open_count, summary.openedCount),
        )
    }

    fun render(
        resources: Resources,
        summary: PeriodUsageSummary,
    ): Bitmap = renderWithDiagnostics(resources, summary).bitmap

    fun renderWithDiagnostics(
        resources: Resources,
        summary: PeriodUsageSummary,
    ): SummaryShareRenderResult {
        val content = createContent(resources, summary)
        val locale = resources.configuration.locales[0]
        val bitmap = Bitmap.createBitmap(
            SUMMARY_SHARE_IMAGE_WIDTH,
            SUMMARY_SHARE_IMAGE_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        val measurements = mutableListOf<SummaryShareTextMeasurement>()
        canvas.drawColor(BackgroundColor)

        val logo = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_foreground)
        if (logo != null) {
            canvas.drawBitmap(
                logo,
                Rect(0, 0, logo.width, logo.height),
                RectF(43f, 28f, 157f, 142f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        } else {
            drawFallbackBrandMark(canvas)
        }

        measurements += canvas.drawFittedText(
            text = content.brand,
            bounds = RectF(164f, 37f, 620f, 92f),
            maxTextSize = 48f,
            minTextSize = 30f,
            color = PrimaryTextColor,
            typeface = BoldTypeface,
            locale = locale,
        )
        measurements += canvas.drawFittedText(
            text = content.deviceName,
            bounds = RectF(650f, 43f, 1140f, 88f),
            maxTextSize = 30f,
            minTextSize = 18f,
            color = SecondaryTextColor,
            typeface = MediumTypeface,
            locale = locale,
            align = Paint.Align.RIGHT,
        )
        measurements += canvas.drawFittedText(
            text = content.period,
            bounds = RectF(164f, 92f, 1140f, 136f),
            maxTextSize = 30f,
            minTextSize = 18f,
            color = SecondaryTextColor,
            typeface = RegularTypeface,
            locale = locale,
        )
        canvas.drawLine(
            56f,
            164f,
            1144f,
            164f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DividerColor
                strokeWidth = 2f
            },
        )

        val leftPanel = RectF(54f, 194f, 548f, 621f)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SurfaceColor }
        canvas.drawRoundRect(leftPanel, 30f, 30f, panelPaint)
        drawRatioPanel(
            canvas = canvas,
            content = content,
            innerRatio = summary.innerRatio,
            locale = locale,
            measurements = measurements,
        )

        drawMetricPanel(
            canvas = canvas,
            bounds = RectF(582f, 194f, 1146f, 319f),
            label = content.coverTimeLabel,
            value = content.coverTime,
            accentColor = CoverColor,
            locale = locale,
            measurements = measurements,
        )
        drawMetricPanel(
            canvas = canvas,
            bounds = RectF(582f, 345f, 1146f, 470f),
            label = content.innerTimeLabel,
            value = content.innerTime,
            accentColor = InnerColor,
            locale = locale,
            measurements = measurements,
        )
        drawMetricPanel(
            canvas = canvas,
            bounds = RectF(582f, 496f, 1146f, 621f),
            label = content.openedCountLabel,
            value = content.openedCount,
            accentColor = PrimaryTextColor,
            locale = locale,
            measurements = measurements,
        )

        return SummaryShareRenderResult(
            bitmap = bitmap,
            textMeasurements = measurements,
        )
    }

    private fun drawRatioPanel(
        canvas: Canvas,
        content: SummaryShareContent,
        innerRatio: Float,
        locale: java.util.Locale,
        measurements: MutableList<SummaryShareTextMeasurement>,
    ) {
        val arcBounds = RectF(130f, 244f, 472f, 586f)
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 38f
            strokeCap = Paint.Cap.BUTT
            color = CoverColor
        }
        canvas.drawArc(arcBounds, -90f, 360f, false, arcPaint)
        val innerSweep = innerRatio.coerceIn(0f, 1f) * 360f
        if (innerSweep > 0f) {
            arcPaint.color = InnerColor
            canvas.drawArc(arcBounds, -90f, innerSweep, false, arcPaint)
        }

        measurements += canvas.drawFittedText(
            text = content.innerRatioLabel,
            bounds = RectF(174f, 344f, 428f, 390f),
            maxTextSize = 29f,
            minTextSize = 18f,
            color = SecondaryTextColor,
            typeface = MediumTypeface,
            locale = locale,
            align = Paint.Align.CENTER,
        )
        measurements += canvas.drawFittedText(
            text = content.innerRatio,
            bounds = RectF(168f, 396f, 434f, 482f),
            maxTextSize = 76f,
            minTextSize = 44f,
            color = InnerColor,
            typeface = BoldTypeface,
            locale = locale,
            align = Paint.Align.CENTER,
        )
    }

    private fun drawMetricPanel(
        canvas: Canvas,
        bounds: RectF,
        label: String,
        value: String,
        accentColor: Int,
        locale: java.util.Locale,
        measurements: MutableList<SummaryShareTextMeasurement>,
    ) {
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SurfaceColor }
        canvas.drawRoundRect(bounds, 25f, 25f, panelPaint)
        canvas.drawRoundRect(
            RectF(bounds.left + 22f, bounds.top + 27f, bounds.left + 32f, bounds.bottom - 27f),
            5f,
            5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor },
        )
        measurements += canvas.drawFittedText(
            text = label,
            bounds = RectF(bounds.left + 55f, bounds.top + 26f, bounds.left + 260f, bounds.bottom - 26f),
            maxTextSize = 28f,
            minTextSize = 17f,
            color = SecondaryTextColor,
            typeface = MediumTypeface,
            locale = locale,
        )
        measurements += canvas.drawFittedText(
            text = value,
            bounds = RectF(bounds.left + 266f, bounds.top + 20f, bounds.right - 28f, bounds.bottom - 20f),
            maxTextSize = 48f,
            minTextSize = 20f,
            color = PrimaryTextColor,
            typeface = BoldTypeface,
            locale = locale,
            align = Paint.Align.RIGHT,
        )
    }

    private fun drawFallbackBrandMark(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeJoin = Paint.Join.ROUND
            color = PrimaryTextColor
        }
        canvas.drawLine(65f, 56f, 100f, 76f, paint)
        canvas.drawLine(100f, 76f, 135f, 56f, paint)
        canvas.drawLine(65f, 56f, 65f, 115f, paint)
        canvas.drawLine(135f, 56f, 135f, 115f, paint)
        canvas.drawLine(65f, 115f, 100f, 135f, paint)
        canvas.drawLine(100f, 135f, 135f, 115f, paint)
    }

    private fun Canvas.drawFittedText(
        text: String,
        bounds: RectF,
        maxTextSize: Float,
        minTextSize: Float,
        color: Int,
        typeface: Typeface,
        locale: java.util.Locale,
        align: Paint.Align = Paint.Align.LEFT,
    ): SummaryShareTextMeasurement {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            this.typeface = typeface
            this.textAlign = align
            textLocale = locale
        }
        var textSize = maxTextSize
        var measuredWidth: Float
        var measuredHeight: Float
        do {
            paint.textSize = textSize
            measuredWidth = paint.measureText(text)
            measuredHeight = paint.fontMetrics.run { descent - ascent }
            if (measuredWidth <= bounds.width() && measuredHeight <= bounds.height()) break
            textSize -= 1f
        } while (textSize >= minTextSize)

        paint.textSize = textSize.coerceAtLeast(minTextSize)
        measuredWidth = paint.measureText(text)
        val metrics = paint.fontMetrics
        measuredHeight = metrics.descent - metrics.ascent
        val x = when (align) {
            Paint.Align.CENTER -> bounds.centerX()
            Paint.Align.RIGHT -> bounds.right
            Paint.Align.LEFT -> bounds.left
        }
        val baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
        save()
        clipRect(bounds)
        drawText(text, x, baseline, paint)
        restore()
        return SummaryShareTextMeasurement(
            text = text,
            textSizePx = paint.textSize,
            measuredWidthPx = measuredWidth,
            availableWidthPx = bounds.width(),
            measuredHeightPx = measuredHeight,
            availableHeightPx = bounds.height(),
        )
    }
}

internal fun formatDeviceName(
    manufacturer: String,
    model: String,
): String {
    val trimmedManufacturer = manufacturer.trim()
    val normalizedManufacturer = trimmedManufacturer.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
    val normalizedModel = model.trim()
    return when {
        normalizedManufacturer.isEmpty() -> normalizedModel
        normalizedModel.isEmpty() -> normalizedManufacturer
        normalizedModel.startsWith(trimmedManufacturer, ignoreCase = true) ->
            normalizedManufacturer + normalizedModel.drop(trimmedManufacturer.length)
        else -> "$normalizedManufacturer $normalizedModel"
    }
}
