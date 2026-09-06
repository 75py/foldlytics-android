package com.nagopy.android.foldlytics.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.nagopy.android.foldlytics.MainActivity
import com.nagopy.android.foldlytics.R
import java.text.DateFormat
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.roundToInt

internal object SummaryWidgetRenderer {
    fun responsive(context: Context, id: Int, state: SummaryWidgetState): RemoteViews {
        val small = render(context, id, state, wide = false)
        val wide = render(context, id, state, wide = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(mapOf(SizeF(140f, 180f) to small, SizeF(280f, 180f) to wide))
        }
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val portrait = if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= 280) wide else small
        val landscape = if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) >= 280) wide else small
        return RemoteViews(landscape, portrait)
    }

    fun render(context: Context, id: Int, state: SummaryWidgetState, wide: Boolean): RemoteViews {
        val largeType = context.resources.configuration.fontScale >= 1.3f
        val layout = when {
            largeType && wide -> R.layout.summary_widget_large_type_wide
            largeType -> R.layout.summary_widget_large_type_small
            wide -> R.layout.summary_widget_wide
            else -> R.layout.summary_widget_small
        }
        val views = RemoteViews(context.packageName, layout)
        val locale = context.resources.configuration.locales[0]
        val number = NumberFormat.getIntegerInstance(locale)
        val date = DateTimeFormatter.ofPattern("M/d", locale)
        val dateLabel = if (state.period == WidgetPeriod.TODAY) {
            state.dateRange.endInclusive.format(DateTimeFormatter.ofPattern("yyyy/M/d", locale))
        } else {
            context.getString(
                R.string.widget_period_dates,
                context.getString(periodLabel(state.period)),
                state.dateRange.start.format(date),
                state.dateRange.endInclusive.format(date),
            )
        }
        views.setTextViewText(R.id.widget_period, if (!wide) dateLabel.replace(" · ", "\n") else dateLabel)
        views.setContentDescription(R.id.widget_configure, context.getString(R.string.widget_configure) + ": " + dateLabel)
        val ratio = state.innerRatio
        val percent = ratio?.let { number.format((it * 100f).roundToInt()) + "%" } ?: "—"
        views.setTextViewText(R.id.widget_ratio, percent)
        views.setContentDescription(R.id.widget_ratio, context.getString(R.string.widget_inner_ratio, percent))
        if (!largeType) {
            // Tint resources are resolved by the host, including after process death/theme changes.
            views.setImageViewBitmap(R.id.widget_donut, arcMask(-90f, if (ratio == null) 360f else 0f))
            views.setImageViewBitmap(R.id.widget_inner_arc, arcMask(-90f, (ratio ?: 0f) * 360f))
            views.setImageViewBitmap(R.id.widget_cover_arc, arcMask(-90f + (ratio ?: 0f) * 360f, if (ratio == null) 0f else (1f - ratio) * 360f))
            views.setViewVisibility(R.id.widget_ratio_label, if (ratio == null) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_chart, if (state.status == WidgetStatus.PERMISSION_REQUIRED) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_metrics, if (wide && state.status != WidgetStatus.PERMISSION_REQUIRED) View.VISIBLE else View.GONE)
        }
        views.setTextViewText(R.id.widget_inner_time, formatDuration(context, state.innerMillis))
        views.setTextViewText(R.id.widget_cover_time, formatDuration(context, state.coverMillis))
        views.setTextViewText(R.id.widget_open_count, number.format(state.openedCount))
        val unavailable = state.status == WidgetStatus.PERMISSION_REQUIRED || ratio == null
        if (unavailable) {
            views.setTextViewText(R.id.widget_inner_time, "—")
            views.setTextViewText(R.id.widget_cover_time, "—")
            views.setTextViewText(R.id.widget_open_count, if (state.status == WidgetStatus.PERMISSION_REQUIRED || !state.hasRecordedEvidence) "—" else number.format(state.openedCount))
        }
        val message = when (state.status) {
            WidgetStatus.READY -> null
            WidgetStatus.NO_DATA -> R.string.widget_no_data
            WidgetStatus.PERMISSION_REQUIRED -> R.string.widget_permission_required
            WidgetStatus.UPDATE_FAILED -> R.string.widget_update_failed
        }
        views.setViewVisibility(R.id.widget_status, if (message == null) View.GONE else View.VISIBLE)
        message?.let { views.setTextViewText(R.id.widget_status, context.getString(it)) }
        views.setTextViewText(
            R.id.widget_sync,
            state.lastSyncMillis?.let {
                context.getString(R.string.widget_last_sync, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(it)))
            } ?: context.getString(R.string.widget_never_synced),
        )
        if (largeType) {
            val ready = state.status == WidgetStatus.READY
            views.setViewVisibility(R.id.widget_ratio, if (ready) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_metrics, if (ready && wide) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_ratio, context.getString(R.string.widget_inner) + (if (wide) "\n" else " ") + percent)
            if (!wide && state.period != WidgetPeriod.TODAY) {
                views.setTextViewText(R.id.widget_period, context.getString(periodLabel(state.period)))
            }
            views.setTextViewText(R.id.widget_inner_time, context.getString(R.string.widget_inner) + " " + compactDuration(context, state.innerMillis))
            views.setTextViewText(R.id.widget_cover_time, context.getString(R.string.widget_cover) + " " + compactDuration(context, state.coverMillis))
            views.setTextViewText(R.id.widget_open_count, context.getString(R.string.widget_compact_opens) + " " + number.format(state.openedCount))
            if (state.status == WidgetStatus.PERMISSION_REQUIRED) views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_permission_short))
            state.lastSyncMillis?.let {
                val datePart = DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(it))
                val timePart = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(it))
                views.setTextViewText(R.id.widget_sync, "↻ " + datePart + (if (wide) " " else "\n") + timePart)
                views.setContentDescription(R.id.widget_sync, context.getString(R.string.widget_last_sync, datePart + " " + timePart))
            }
        }
        val open = Intent(context, MainActivity::class.java).apply {
            data = "foldlytics://widget/$id/open".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(SummaryWidgetProvider.EXTRA_PERIOD, state.period.name)
        }
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, open, PENDING_FLAGS))
        val configure = Intent(context, SummaryWidgetConfigurationActivity::class.java).apply {
            data = "foldlytics://widget/$id/configure".toUri()
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        views.setOnClickPendingIntent(R.id.widget_configure, PendingIntent.getActivity(context, id, configure, PENDING_FLAGS))
        val refresh = Intent(context, SummaryWidgetProvider::class.java).apply {
            action = SummaryWidgetProvider.ACTION_REFRESH
            data = "foldlytics://widget/$id/refresh".toUri()
        }
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id, refresh, PENDING_FLAGS))
        return views
    }

    private fun arcMask(start: Float, sweep: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 36f
            color = android.graphics.Color.WHITE
        }
        Canvas(bitmap).drawArc(RectF(24f, 24f, 232f, 232f), start, sweep, false, paint)
        return bitmap
    }

    private fun compactDuration(context: Context, millis: Long): String =
        if (millis >= 3_600_000L) context.getString(R.string.widget_compact_hours, millis / 3_600_000L)
        else context.getString(R.string.widget_compact_minutes, millis / 60_000L)

    private fun formatDuration(context: Context, millis: Long): String {
        val minutes = millis / 60_000L
        return if (millis in 1L until 60_000L) context.getString(R.string.widget_less_than_minute) else
            context.getString(R.string.widget_duration, minutes / 60L, minutes % 60L)
    }

    internal fun periodLabel(period: WidgetPeriod): Int = when (period) {
        WidgetPeriod.TODAY -> R.string.widget_today
        WidgetPeriod.DAYS_7 -> R.string.widget_days_7
        WidgetPeriod.DAYS_30 -> R.string.widget_days_30
    }

    private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
