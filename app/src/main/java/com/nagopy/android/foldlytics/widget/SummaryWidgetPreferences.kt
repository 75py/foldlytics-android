package com.nagopy.android.foldlytics.widget

import android.content.Context
import com.nagopy.android.foldlytics.data.UsageSyncResult

internal class SummaryWidgetPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("summary_widgets", Context.MODE_PRIVATE)

    fun period(id: Int): WidgetPeriod = WidgetPeriod.fromName(preferences.getString("period_$id", null))

    fun setPeriod(id: Int, period: WidgetPeriod) {
        preferences.edit().putString("period_$id", period.name).apply()
    }

    fun remove(id: Int) {
        preferences.edit().remove("period_$id").apply()
    }

    fun recordSyncResult(result: UsageSyncResult) {
        preferences.edit().putBoolean("update_failed", result !is UsageSyncResult.Success).apply()
    }

    val updateFailed: Boolean get() = preferences.getBoolean("update_failed", false)
}
