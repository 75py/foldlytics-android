package com.nagopy.android.foldlytics.widget

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryWidgetBindingTest {
    @Test
    fun bindsIndependentPeriodsAndRefreshesWithoutAnActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, TEST_HOST_ID)
        val preferences = SummaryWidgetPreferences(context)
        val ids = mutableListOf<Int>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            repeat(2) {
                val id = host.allocateAppWidgetId()
                ids += id
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, SummaryWidgetProvider::class.java)))
            }
            assertEquals(WidgetPeriod.DAYS_7, preferences.period(ids[0]))
            preferences.setPeriod(ids[0], WidgetPeriod.TODAY)
            preferences.setPeriod(ids[1], WidgetPeriod.DAYS_30)
            assertEquals(WidgetPeriod.TODAY, SummaryWidgetPreferences(context).period(ids[0]))
            assertEquals(WidgetPeriod.DAYS_30, SummaryWidgetPreferences(context).period(ids[1]))
            manager.updateAppWidgetOptions(ids[1], Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 280)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            })
            val work = WorkManager.getInstance(context)
            val previous = work.getWorkInfosForUniqueWork("summary_widget_sync").get(5, TimeUnit.SECONDS)
            val completedIds = previous.filter { it.state.isFinished }.map { it.id }.toSet()
            // Exercise the provider refresh action without starting MainActivity.
            SummaryWidgetProvider().onReceive(context, Intent(context, SummaryWidgetProvider::class.java).apply {
                action = SummaryWidgetProvider.ACTION_REFRESH
            })
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var completed = false
            while (System.nanoTime() < deadline && !completed) {
                completed = work.getWorkInfosForUniqueWork("summary_widget_sync").get(5, TimeUnit.SECONDS)
                    .any { it.id !in completedIds && it.state == WorkInfo.State.SUCCEEDED }
                if (!completed) Thread.sleep(100)
            }
            assertTrue("Widget refresh worker did not complete without an activity", completed)
            assertEquals(WidgetPeriod.TODAY, preferences.period(ids[0]))
            assertEquals(WidgetPeriod.DAYS_30, preferences.period(ids[1]))
        } finally {
            ids.forEach(preferences::remove)
            host.deleteHost()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private companion object {
        const val TEST_HOST_ID = 0xF01D
    }
}
