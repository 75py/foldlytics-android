package com.nagopy.android.foldlytics.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class SummaryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        SummaryWidgetUpdater.enqueue(context, sync = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        SummaryWidgetUpdater.enqueue(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> SummaryWidgetUpdater.enqueue(context, sync = true)
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            -> SummaryWidgetUpdater.enqueue(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val preferences = SummaryWidgetPreferences(context)
        appWidgetIds.forEach(preferences::remove)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val preferences = SummaryWidgetPreferences(context)
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) ->
            preferences.setPeriod(new, preferences.period(old))
            preferences.remove(old)
        }
        SummaryWidgetUpdater.enqueue(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.nagopy.android.foldlytics.widget.REFRESH"
        const val EXTRA_PERIOD = "com.nagopy.android.foldlytics.widget.PERIOD"
    }
}
